package io.github.deathcannon;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

final class DeathCannonOverlay extends Overlay
{
    private static final int WIDTH = 396;
    private static final int HEIGHT = 246;
    private static final int COMPACT_SIZE = 128;
    private static final int MIN_FULL_HEIGHT = 126;
    private static final int CARD_BASE_WIDTH = 320;
    private static final Color INK = new Color(35, 24, 13);
    private static final Color CLANMATE = new Color(21, 61, 106);
    private static final Color RED = new Color(135, 24, 9);
    private static final Color WHITE = new Color(255, 251, 239);
    private static final CacheFont REGULAR = new CacheFont("regular");
    private static final CacheFont BOLD = new CacheFont("bold");
    private static final DateTimeFormatter SEEN_DATE = DateTimeFormatter.ofPattern("MM-dd-yyyy");
    private static final DateTimeFormatter SHORT_SEEN_DATE = DateTimeFormatter.ofPattern("MM-dd-yy");
    private static final BufferedImage SCROLL = load("memorial-scroll.png");
    private static final int GRAVESTONE_SPRITE = 8920;
    private static final BufferedImage TOTAL = load("skills/overall.png");
    private static final Map<Skill, BufferedImage> ICONS = loadIcons();

    private final Client client;
    private final DeathCannonPlugin plugin;
    private final DeathCannonConfig config;
    @Inject private SpriteManager sprites;
    private volatile BufferedImage grave;
    private DeathBroadcast drawnDeath;
    private TopSkill drawnSkill;
    private boolean drawnCompact;
    private int drawnWidth;
    private BufferedImage card;

    @Inject
    DeathCannonOverlay(Client client, DeathCannonPlugin plugin, DeathCannonConfig config)
    {
        super(plugin);
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_CENTER);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setMovable(true);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        DeathBroadcast death = plugin.active();
        long elapsed = plugin.elapsedMs();
        long duration = plugin.durationMs();
        BufferedImage gravestone = grave;
        if (death == null || elapsed >= duration || gravestone == null)
        {
            drawnDeath = null;
            drawnSkill = null;
            card = null;
            return null;
        }

        int viewportWidth = client.getViewportWidth();
        int viewportHeight = client.getViewportHeight();
        boolean compact = config.compactPopup();
        int baseWidth = compact ? COMPACT_SIZE : WIDTH;
        int baseHeight = compact ? COMPACT_SIZE : HEIGHT;
        int scaleBaseWidth = compact ? COMPACT_SIZE : CARD_BASE_WIDTH;
        int requestedWidth = scaleBaseWidth * Math.max(50, Math.min(150, config.popupScale())) / 100;
        int width = Math.min(requestedWidth, Math.min(viewportWidth - 16, (viewportHeight - 20) * baseWidth / baseHeight));
        if (width >= (compact ? 48 : 160) && (compact || viewportHeight >= MIN_FULL_HEIGHT + 20))
        {
            int x = width / 2;
            int y = 8;
            int height = compact ? width : Math.max(MIN_FULL_HEIGHT, (int) Math.round((double) width * HEIGHT / WIDTH));
            TopSkill top = compact ? null : death.topSkill;
            if (drawnDeath != death || drawnSkill != top || drawnCompact != compact
                || drawnWidth != width)
            {
                card = compact ? renderGravestone(death, width, gravestone) : renderCard(death, top, gravestone, width, height);
                drawnDeath = death;
                drawnSkill = top;
                drawnCompact = compact;
                drawnWidth = width;
            }
            paint(graphics, card, elapsed, duration, x, y, width);
            return new Dimension(width, height + y);
        }
        return null;
    }

    void loadGravestone()
    {
        sprites.getSpriteAsync(GRAVESTONE_SPRITE, 0, image -> grave = image);
    }

    static BufferedImage renderGravestone(DeathBroadcast death, int size, BufferedImage grave)
    {
        BufferedImage stone = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = stone.createGraphics();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(grave, 0, 0, size, size, null);
            // Use the cache sprite's blank stone strip as the face for the two inscription lines.
            g.drawImage(grave, at(size, 22), at(size, death.clanmate ? 50 : 58), at(size, 106), at(size, 108),
                11, 28, 53, 29, null);
            int textWidth = at(size, 78);
            int textScale = size >= 166 ? 2 : 1;
            CacheFont nameFont = BOLD.width(death.player) * textScale <= textWidth ? BOLD : REGULAR;
            nameFont.draw(g, INK, death.player, size / 2, at(size, 79), textScale, textWidth, true);
            REGULAR.draw(g, INK, death.levelText, size / 2, at(size, 100), textScale, textWidth, true);
            if (death.clanmate)
            {
                BOLD.draw(g, INK, "Clanmate", size / 2, at(size, 63), 1, textWidth, true);
            }
        }
        finally
        {
            g.dispose();
        }
        return stone;
    }

    private static int at(int size, int position)
    {
        return (int) Math.round((double) size * position / COMPACT_SIZE);
    }

    static BufferedImage renderCard(DeathBroadcast death, TopSkill top, BufferedImage grave,
        int width, int height)
    {
        BufferedImage card = new BufferedImage(width, height,
            BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = card.createGraphics();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.scale((double) width / WIDTH, (double) height / HEIGHT);
            g.drawImage(SCROLL, 0, 0, WIDTH, HEIGHT, null);
            g.drawImage(grave, 181, 26, 34, 34, null);
            REGULAR.draw(g, WHITE, death.player, 198, 95, 2.75, 235, true);
            REGULAR.draw(g, RED, "has fallen", 198, 128, 1.7, 112, true);
            if (death.clanmate)
            {
                BOLD.draw(g, CLANMATE, "Clanmate", 198, 23, 1.4, 100, true);
            }
            int statCenter = width < 205 ? 220 : 198;
            drawStat(g, TOTAL, death.levelText, statCenter, 135);
            if (top != null)
            {
                drawStat(g, ICONS.get(top.skill), Integer.toString(top.level), statCenter, 160);
            }

            PlayerSighting sighting = death.sighting;
            if (sighting != null)
            {
                ZonedDateTime when = sighting.time.atZone(ZoneId.systemDefault());
                BOLD.draw(g, INK, death.lastSeen ? "Last seen" : "Seen", 28, 128, 1.5, 123, false);
                String date = SEEN_DATE.format(when);
                if (BOLD.width(date) > 123 * width / WIDTH)
                {
                    date = SHORT_SEEN_DATE.format(when);
                }
                BOLD.draw(g, INK, date, 28, 154, 1.85, 123, false);
                BOLD.draw(g, INK, "World " + sighting.world, 28, 179, 1.55, 145, false);
                drawLocation(g, sighting.displayLocation(), width);
                drawContained(g, sighting.portrait, 259, 48, 134, 142);
            }
            BOLD.draw(g, INK, death.cause, 198, sighting == null ? 204 : 228, 1.65, 318, true);
        }
        finally
        {
            g.dispose();
        }
        return card;
    }

    static void paint(Graphics2D graphics, BufferedImage card, long elapsed, long duration, int x, int y, int width)
    {
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            float reveal = (float) Math.min(1, elapsed / 270.0);
            float fade = (float) Math.min(1, (duration - elapsed) / 450.0);
            g.setComposite(AlphaComposite.SrcOver.derive(reveal * fade));
            g.drawImage(card, x - width / 2, y - Math.round(7 * (1 - reveal)), null);
        }
        finally
        {
            g.dispose();
        }
    }

    private static void drawLocation(Graphics2D graphics, String location, int cardWidth)
    {
        // The location row sits below the stats and portrait, so it can use the full parchment.
        int textWidth = 340 * cardWidth / WIDTH;
        int nativeWidth = BOLD.width(location);
        int scale = Math.max(1, Math.min(cardWidth / 220, textWidth / Math.max(1, nativeWidth)));
        if (nativeWidth > textWidth)
        {
            while (BOLD.width(location + "...") > textWidth)
            {
                location = location.substring(0, location.length() - 1).stripTrailing();
            }
            location += "...";
        }

        BOLD.draw(graphics, INK, location, 28, 203, scale * WIDTH / (double) cardWidth, 340, false);
    }

    private static void drawStat(Graphics2D g, BufferedImage icon, String value, int center, int y)
    {
        int width = (int) Math.round(BOLD.width(value) * 1.85);
        int left = center - (23 + width) / 2;
        drawContained(g, icon, left, y, 21, 21);
        BOLD.draw(g, INK, value, left + 23, y + 19, 1.85, 62, false);
    }

    static void drawContained(Graphics2D g, BufferedImage image, int x, int y, int width, int height)
    {
        double scale = Math.min((double) width / image.getWidth(), (double) height / image.getHeight());
        int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(image.getHeight() * scale));
        g.drawImage(image, x + (width - w) / 2, y + (height - h) / 2, w, h, null);
    }

    private static Map<Skill, BufferedImage> loadIcons()
    {
        Map<Skill, BufferedImage> icons = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values())
        {
            icons.put(skill, load("skills/" + skill.name().toLowerCase(Locale.ROOT) + ".png"));
        }
        return icons;
    }

    static BufferedImage load(String name)
    {
        try (InputStream stream = DeathCannonOverlay.class.getResourceAsStream(name))
        {
            if (stream == null)
            {
                throw new IllegalStateException("Missing memorial cache export: " + name);
            }
            return ImageIO.read(stream);
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Could not load memorial cache export: " + name, ex);
        }
    }
}
