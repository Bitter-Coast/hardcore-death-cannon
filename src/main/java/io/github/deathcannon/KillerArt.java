package io.github.deathcannon;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds real game art for the killer named in a broadcast. The broadcast carries only a name, so:
 * <ol>
 * <li>a loaded NPC or player with that name in the current scene is drawn from its live model,
 * preferring an NPC that is interacting with the victim;</li>
 * <li>otherwise the NPC definitions in the local game cache with that name are used. Attackable
 * definitions are preferred, and among those the appearance shared by the most definitions is
 * drawn (ties go to the lowest NPC id).</li>
 * </ol>
 * Killers that match nothing get no art. A killer counts as a player only on evidence: a player with
 * that name in the scene, or no NPC definition with that name and an earlier Hardcore Worlds sighting
 * of a player with it. Nothing is downloaded. Every method runs on the client thread.
 */
@Singleton
class KillerArt
{
    static final int SIZE = 160;
    private static final int NPC_CONFIG_GROUP = 9;
    private static final int IDS_PER_TICK = 256;
    private static final int MAX_PENDING = 25;
    private static final int MAX_CACHED = 64;
    private static final Logger LOG = LoggerFactory.getLogger(KillerArt.class);

    private final Client client;
    private final Map<String, List<Integer>> idsByName = new HashMap<>();
    private final List<DeathBroadcast> pending = new ArrayList<>();
    private final Map<String, BufferedImage> byName = new LinkedHashMap<>(16, 0.75f, true);
    private int[] npcIds;
    private int nextIndex;
    private boolean indexed;

    @Inject
    KillerArt(Client client)
    {
        this.client = client;
    }

    void clear()
    {
        pending.clear();
    }

    /** Attaches art right away from the scene, or queues a cache lookup for an NPC killer. */
    void request(DeathBroadcast death)
    {
        if (death.killer == null || death.killerArt != null)
        {
            return;
        }
        try
        {
            if (fromScene(death))
            {
                return;
            }
        }
        catch (RuntimeException ex)
        {
            LOG.debug("Could not draw the killer from the scene", ex);
        }
        if (indexed)
        {
            fromCache(death);
            return;
        }
        if (pending.size() >= MAX_PENDING)
        {
            pending.remove(0);
        }
        pending.add(death);
    }

    /** Indexes a slice of the cache's NPC definitions per client tick, so no frame stalls. */
    void tick()
    {
        if (indexed || pending.isEmpty() || client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }
        if (npcIds == null)
        {
            int[] ids = client.getIndexConfig().getFileIds(NPC_CONFIG_GROUP);
            npcIds = ids == null ? new int[0] : ids;
        }
        int end = Math.min(npcIds.length, nextIndex + IDS_PER_TICK);
        for (; nextIndex < end; nextIndex++)
        {
            int id = npcIds[nextIndex];
            NPCComposition definition;
            try
            {
                definition = client.getNpcDefinition(id);
            }
            catch (RuntimeException ex)
            {
                continue;
            }
            if (definition == null || definition.getModels() == null || definition.getModels().length == 0)
            {
                continue;
            }
            String name = nameKey(definition.getName());
            if (!name.isEmpty() && !"null".equals(name))
            {
                idsByName.computeIfAbsent(name, key -> new ArrayList<>()).add(id);
            }
        }
        if (nextIndex >= npcIds.length)
        {
            indexed = true;
            List<DeathBroadcast> work = new ArrayList<>(pending);
            pending.clear();
            work.forEach(this::fromCache);
        }
    }

    private boolean fromScene(DeathBroadcast death)
    {
        WorldView view = client.getTopLevelWorldView();
        if (view == null)
        {
            return false;
        }
        String exact = nameKey(death.killer);
        String bare = nameKey(stripArticle(death.killer));
        String victim = nameKey(death.player);
        NPC chosen = null;
        boolean engaged = false;
        for (NPC npc : view.npcs())
        {
            String name = npc == null ? "" : nameKey(npc.getName());
            if (name.isEmpty() || !(name.equals(exact) || name.equals(bare)))
            {
                continue;
            }
            Actor target = npc.getInteracting();
            engaged = target instanceof Player && victim.equals(nameKey(target.getName()));
            if (engaged || chosen == null)
            {
                chosen = npc;
            }
            if (engaged)
            {
                break;
            }
        }
        // A player with the killer's name beats an NPC that was not fighting the victim.
        for (Player player : engaged ? List.<Player>of() : view.players())
        {
            if (player != null && exact.equals(nameKey(player.getName())))
            {
                // The player in the scene is the evidence, so it stands even when the model cannot be drawn.
                death.killerIsPlayer = true;
                attach(death, PlayerPortrait.render(player.getModel(), SIZE, 1, 1), true);
                return true;
            }
        }
        // The live model already carries the definition's scale and colours.
        return chosen != null && attach(death, PlayerPortrait.render(chosen.getModel(), SIZE, 1, 1), false);
    }

    private void fromCache(DeathBroadcast death)
    {
        String exact = nameKey(death.killer);
        String key = idsByName.containsKey(exact) ? exact : nameKey(stripArticle(death.killer));
        if (!idsByName.containsKey(key))
        {
            // No NPC has this name. That alone does not make a player; a Hardcore Worlds sighting does.
            death.killerIsPlayer = death.killerSeen;
            return;
        }
        BufferedImage art = byName.get(key);
        if (art == null)
        {
            try
            {
                art = render(choose(definitions(idsByName.get(key))));
            }
            catch (RuntimeException ex)
            {
                LOG.debug("Could not draw the killer from the game cache", ex);
            }
            if (art != null)
            {
                byName.put(key, art);
                if (byName.size() > MAX_CACHED)
                {
                    byName.remove(byName.keySet().iterator().next());
                }
            }
        }
        attach(death, art, false);
    }

    private List<NPCComposition> definitions(List<Integer> ids)
    {
        List<NPCComposition> definitions = new ArrayList<>(ids.size());
        for (int id : ids)
        {
            NPCComposition definition = client.getNpcDefinition(id);
            if (definition != null)
            {
                definitions.add(definition);
            }
        }
        return definitions;
    }

    /**
     * Picks the definition to draw for a name: attackable definitions first (all of them if none
     * is attackable), then the appearance used by the most of them, ties going to the lowest id.
     */
    static NPCComposition choose(List<NPCComposition> definitions)
    {
        List<NPCComposition> candidates = new ArrayList<>();
        for (NPCComposition definition : definitions)
        {
            if (definition.getCombatLevel() > 0)
            {
                candidates.add(definition);
            }
        }
        if (candidates.isEmpty())
        {
            candidates = definitions;
        }
        Map<String, List<NPCComposition>> looks = new LinkedHashMap<>();
        for (NPCComposition definition : candidates)
        {
            looks.computeIfAbsent(appearance(definition), key -> new ArrayList<>()).add(definition);
        }
        NPCComposition best = null;
        int bestCount = 0;
        for (List<NPCComposition> group : looks.values())
        {
            NPCComposition lowest = group.get(0);
            for (NPCComposition definition : group)
            {
                if (definition.getId() < lowest.getId())
                {
                    lowest = definition;
                }
            }
            if (group.size() > bestCount || (group.size() == bestCount && lowest.getId() < best.getId()))
            {
                best = lowest;
                bestCount = group.size();
            }
        }
        return best;
    }

    private BufferedImage render(NPCComposition definition)
    {
        if (definition == null)
        {
            return null;
        }
        int[] modelIds = definition.getModels();
        ModelData[] parts = new ModelData[modelIds.length];
        for (int i = 0; i < modelIds.length; i++)
        {
            parts[i] = client.loadModelData(modelIds[i]);
            if (parts[i] == null)
            {
                return null;
            }
        }
        // Loaded model data is shared by the client, so recolour a copy.
        ModelData merged = client.mergeModels(parts, parts.length).cloneColors();
        short[] find = definition.getColorToReplace();
        short[] replace = definition.getColorToReplaceWith();
        if (find != null && replace != null)
        {
            for (int i = 0; i < Math.min(find.length, replace.length); i++)
            {
                merged.recolor(find[i], replace[i]);
            }
        }
        Model lit = merged.light();
        return PlayerPortrait.render(lit, SIZE, definition.getWidthScale() / 128.0, definition.getHeightScale() / 128.0);
    }

    private static boolean attach(DeathBroadcast death, BufferedImage art, boolean player)
    {
        if (art == null)
        {
            return false;
        }
        death.killerIsPlayer = player;
        death.killerArt = art;
        return true;
    }

    private static String appearance(NPCComposition definition)
    {
        return Arrays.toString(definition.getModels()) + Arrays.toString(definition.getColorToReplace())
            + Arrays.toString(definition.getColorToReplaceWith()) + definition.getWidthScale()
            + "x" + definition.getHeightScale();
    }

    static String stripArticle(String name)
    {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String article : new String[] {"a ", "an ", "the "})
        {
            if (lower.startsWith(article) && name.length() > article.length())
            {
                return name.substring(article.length());
            }
        }
        return name;
    }

    static String nameKey(String name)
    {
        if (name == null)
        {
            return "";
        }
        return name.replaceAll("<[^>]*>", "").replace('\u00a0', ' ').replace('_', ' ').trim().toLowerCase(Locale.ROOT);
    }
}
