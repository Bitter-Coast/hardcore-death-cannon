package io.github.deathcannon;

import com.google.inject.Provides;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.PlayerChanged;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(name = "Hardcore Death Cannon",
    internalName = "hardcore-death-cannon",
    description = "Play a cannon and memorial fanfare for Hardcore Worlds death broadcasts.",
    tags = {"hardcore", "death", "broadcast"})
public final class DeathCannonPlugin extends Plugin
{
    private static final long SAVE_INTERVAL = TimeUnit.SECONDS.toNanos(30);
    private static final int CAPTURES_PER_FRAME = 2;
    private static final Logger LOG = LoggerFactory.getLogger(DeathCannonPlugin.class);

    @Inject private Client client;
    @Inject private DeathCannonConfig config;
    @Inject private OverlayManager overlayManager;
    @Inject private DeathCannonOverlay overlay;
    @Inject private ScheduledExecutorService executor;
    @Inject private AudioPlayer audioPlayer;
    @Inject private HiscoreClient hiscores;
    @Inject private ClientThread clientThread;
    @Inject private MouseManager mouseManager;

    private final ArrayDeque<DeathBroadcast> pending = new ArrayDeque<>();
    private final Map<String, DeathBroadcast> replays = new LinkedHashMap<>();
    private final MouseAdapter replayClicks = new MouseAdapter()
    {
        @Override
        public MouseEvent mouseClicked(MouseEvent event)
        {
            if (event.getButton() == MouseEvent.BUTTON1 && event.getModifiersEx() == 0)
            {
                int x = event.getX();
                int y = event.getY();
                clientThread.invokeLater(() -> replayAt(x, y));
            }
            return event;
        }
    };
    private final Map<String, Player> portraitRequests = new LinkedHashMap<>();
    private final Map<String, PlayerSighting> sightings = new LinkedHashMap<>(16, 0.75f, true);
    private final AtomicLong savedVersion = new AtomicLong();
    private long sightingVersion;
    private long submittedVersion;
    private long lastSaveAttempt;
    private Future<?> pendingSave;
    private boolean loadedSightings;
    private boolean seedPortraits;
    private CannonAudio audio;
    private volatile DeathBroadcast active;
    private volatile long startedAt;
    private volatile long durationMs;

    @Provides
    DeathCannonConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(DeathCannonConfig.class);
    }

    @Override
    protected synchronized void startUp()
    {
        audio = new CannonAudio(audioPlayer);
        if (!loadedSightings)
        {
            loadedSightings = true;
            executor.execute(this::loadSightings);
        }
        lastSaveAttempt = System.nanoTime();
        seedPortraits = true;
        overlayManager.add(overlay);
        overlay.loadGravestone();
        mouseManager.registerMouseListener(replayClicks);
    }

    private void loadSightings()
    {
        try
        {
            Map<String, PlayerSighting> restored = new SightingStore(getPluginDirectory()).load();
            synchronized (this)
            {
                sightings.forEach((key, sighting) -> restored.merge(key, sighting, SightingStore::newest));
                sightings.clear();
                restored.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue((a, b) -> a.time.compareTo(b.time)))
                    .skip(Math.max(0, restored.size() - SightingStore.LIMIT))
                    .forEach(entry -> sightings.put(entry.getKey(), entry.getValue()));
            }
        }
        catch (IOException ex)
        {
            LOG.warn("Could not open the Hardcore Death Cannon cache directory", ex);
        }
    }

    @Override
    protected synchronized void shutDown()
    {
        saveSightings();
        pending.clear();
        replays.clear();
        portraitRequests.clear();
        active = null;
        overlayManager.remove(overlay);
        mouseManager.unregisterMouseListener(replayClicks);
        audio.close();
        audio = null;
    }

    @Subscribe
    public synchronized void onClientShutdown(ClientShutdown event)
    {
        Future<?> save = saveSightings();
        if (save != null)
        {
            event.waitFor(save);
        }
    }

    private Future<?> saveSightings()
    {
        if (sightingVersion <= savedVersion.get()
            || (submittedVersion == sightingVersion && pendingSave != null && !pendingSave.isDone()))
        {
            return pendingSave;
        }
        long version = sightingVersion;
        Map<String, PlayerSighting> snapshot = new LinkedHashMap<>(sightings);
        pendingSave = executor.submit(() ->
        {
            try
            {
                if (new SightingStore(getPluginDirectory()).save(snapshot))
                {
                    savedVersion.accumulateAndGet(version, Math::max);
                }
            }
            catch (IOException ex)
            {
                LOG.warn("Could not open the Hardcore Death Cannon cache directory", ex);
            }
        });
        submittedVersion = version;
        lastSaveAttempt = System.nanoTime();
        return pendingSave;
    }

    @Subscribe
    public synchronized void onChatMessage(ChatMessage event)
    {
        if (audio == null || client.getGameState() != GameState.LOGGED_IN
            || !client.getWorldType().contains(WorldType.HARDCORE_MAIN))
        {
            return;
        }

        if (event.getType() != ChatMessageType.BROADCAST && event.getType() != ChatMessageType.GAMEMESSAGE)
        {
            return;
        }

        DeathBroadcast death = DeathBroadcast.parse(event.getMessage());
        if (death != null && death.level > config.threshold())
        {
            PlayerSighting current = captureSighting(findPlayer(death.player));
            if (current != null)
            {
                remember(nameKey(death.player), current);
            }
            DeathBroadcast memorial = current != null ? death.withSighting(current, false)
                : death.withSighting(sightings.get(nameKey(death.player)), true);
            pending.add(memorial);
            playNext();
            rememberReplay(event.getMessage(), memorial);
            if (config.compactPopup())
            {
                return;
            }
            hiscores.lookupAsync(death.player.replace('_', ' '), HiscoreEndpoint.HARDCORE_MAIN)
                .whenComplete((result, error) ->
                {
                    if (error == null)
                    {
                        memorial.topSkill = TopSkill.fromHiscores(result);
                    }
                    else
                    {
                        LOG.debug("Could not look up the Hardcore Worlds top skill", error);
                    }
                });
        }
    }

    private void rememberReplay(String originalMessage, DeathBroadcast memorial)
    {
        String line = DeathBroadcast.plainText(originalMessage);
        replays.remove(line);
        replays.put(line, memorial);
        if (replays.size() > 100)
        {
            replays.remove(replays.keySet().iterator().next());
        }
    }

    private synchronized void replayAt(int x, int y)
    {
        if (audio == null || replays.isEmpty() || client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }
        Widget scroll = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (scroll == null || scroll.isHidden() || !scroll.getBounds().contains(x, y))
        {
            return;
        }
        Widget[] children = scroll.getDynamicChildren();
        if (children == null)
        {
            return;
        }
        for (Widget child : children)
        {
            if (child != null && !child.isHidden() && child.getBounds().contains(x, y))
            {
                String value = child.getText();
                if (value == null)
                {
                    continue;
                }
                String text = DeathBroadcast.plainText(value);
                DeathBroadcast memorial = replays.get(text);
                if (memorial == null)
                {
                    // Chat timestamp plugins can prefix the displayed message.
                    for (Map.Entry<String, DeathBroadcast> replay : replays.entrySet())
                    {
                        String original = replay.getKey();
                        if (text.endsWith(" " + original))
                        {
                            memorial = replay.getValue();
                            break;
                        }
                    }
                }
                if (memorial != null)
                {
                    active = null;
                    pending.addFirst(memorial);
                    playNext();
                    return;
                }
            }
        }
    }

    @Subscribe
    public synchronized void onCommandExecuted(CommandExecuted event)
    {
        if (audio != null && "deathcannon".equalsIgnoreCase(event.getCommand())
            && client.getGameState() == GameState.LOGGED_IN)
        {
            Player local = client.getLocalPlayer();
            if (local == null || local.getName() == null)
            {
                return;
            }
            DeathBroadcast preview = DeathBroadcast.preview(local.getName(), client.getTotalLevel())
                .withSighting(captureSighting(local), false);
            preview.topSkill = TopSkill.fromLocalPlayer(client);
            pending.add(preview);
            playNext();
        }
    }

    private Player findPlayer(String name)
    {
        Player local = client.getLocalPlayer();
        if (local == null)
        {
            return null;
        }
        String key = nameKey(name);
        if (key.equals(nameKey(local.getName())))
        {
            return local;
        }
        WorldView view = local.getWorldView();
        if (view != null)
        {
            for (Player player : view.players())
            {
                if (key.equals(nameKey(player.getName())))
                {
                    return player;
                }
            }
        }
        return null;
    }

    private static String nameKey(String name)
    {
        return name == null ? "" : name.replace('\u00a0', ' ').replace('_', ' ').trim().toLowerCase(Locale.ROOT);
    }

    @Subscribe
    public synchronized void onPlayerSpawned(PlayerSpawned event)
    {
        requestPortrait(event.getPlayer());
    }

    @Subscribe
    public synchronized void onPlayerChanged(PlayerChanged event)
    {
        requestPortrait(event.getPlayer());
    }

    @Subscribe
    public synchronized void onPlayerDespawned(PlayerDespawned event)
    {
        Player player = event.getPlayer();
        if (audio == null || client.getGameState() != GameState.LOGGED_IN
            || !client.getWorldType().contains(WorldType.HARDCORE_MAIN))
        {
            return;
        }
        String key = nameKey(player.getName());
        PlayerSighting previous = sightings.get(key);
        if (previous != null)
        {
            WorldPoint tile = sightingTile(player);
            if (tile != null)
            {
                remember(key, new PlayerSighting(previous.portrait, Instant.now(), client.getWorld(), tile));
            }
        }
    }

    private void requestPortrait(Player player)
    {
        if (audio != null && client.getWorldType().contains(WorldType.HARDCORE_MAIN))
        {
            String key = nameKey(player.getName());
            if (!key.isEmpty())
            {
                portraitRequests.put(key, player);
            }
        }
    }

    private void capturePortraits()
    {
        if (audio == null || client.getGameState() != GameState.LOGGED_IN
            || !client.getWorldType().contains(WorldType.HARDCORE_MAIN))
        {
            return;
        }
        if (seedPortraits)
        {
            Player local = client.getLocalPlayer();
            if (local != null && local.getWorldView() != null)
            {
                local.getWorldView().players().forEach(this::requestPortrait);
                seedPortraits = false;
            }
        }

        Iterator<Map.Entry<String, Player>> requests = portraitRequests.entrySet().iterator();
        for (int i = 0; i < CAPTURES_PER_FRAME && requests.hasNext(); i++)
        {
            Map.Entry<String, Player> request = requests.next();
            String key = request.getKey();
            PlayerSighting sighting = captureSighting(request.getValue());
            requests.remove();
            if (sighting != null)
            {
                remember(key, sighting);
            }
        }
    }

    private void remember(String key, PlayerSighting sighting)
    {
        sightings.put(key, sighting);
        sightingVersion++;
        if (sightings.size() > SightingStore.LIMIT)
        {
            sightings.remove(sightings.keySet().iterator().next());
        }
    }

    private PlayerSighting captureSighting(Player player)
    {
        BufferedImage portrait = capture(player);
        return portrait == null ? null : new PlayerSighting(portrait, Instant.now(), client.getWorld(),
            sightingTile(player));
    }

    private static WorldPoint sightingTile(Player player)
    {
        WorldPoint tile = player.getWorldLocation();
        WorldView view = player.getWorldView();
        if (tile != null && view != null && view.isInstance() && player.getLocalLocation() != null)
        {
            return WorldPoint.fromLocalInstance(view.getScene(), player.getLocalLocation(), tile.getPlane());
        }
        return tile;
    }

    @Subscribe
    public synchronized void onGameTick(GameTick event)
    {
        if (sightingVersion > savedVersion.get() && System.nanoTime() - lastSaveAttempt >= SAVE_INTERVAL)
        {
            saveSightings();
        }
    }

    private static BufferedImage capture(Player player)
    {
        if (player != null)
        {
            try
            {
                return PlayerPortrait.render(player.getModel());
            }
            catch (RuntimeException ex)
            {
                LOG.debug("Could not draw a Hardcore Death Cannon portrait", ex);
            }
        }
        return null;
    }

    @Subscribe
    public synchronized void onClientTick(ClientTick event)
    {
        capturePortraits();
        playNext();
    }

    @Subscribe
    public synchronized void onGameStateChanged(GameStateChanged event)
    {
        if (audio == null)
        {
            return;
        }
        switch (event.getGameState())
        {
            case HOPPING:
            case LOGIN_SCREEN:
            case CONNECTION_LOST:
                pending.clear();
                portraitRequests.clear();
                seedPortraits = true;
                active = null;
                break;
            default:
                break;
        }
    }

    private void playNext()
    {
        if (active != null && elapsedMs() < durationMs)
        {
            return;
        }

        DeathBroadcast next = pending.poll();
        if (next != null)
        {
            int popupSeconds = Math.max(1, Math.min(15, config.popupSeconds()));
            durationMs = TimeUnit.SECONDS.toMillis(popupSeconds);
            startedAt = System.nanoTime();
            audio.play(config.volume(), config.cannonSound(), config.memorialMusic(), popupSeconds);
        }
        active = next;
    }

    long durationMs()
    {
        return durationMs;
    }

    DeathBroadcast active()
    {
        return active;
    }

    long elapsedMs()
    {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
