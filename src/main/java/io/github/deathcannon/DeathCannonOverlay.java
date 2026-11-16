package io.github.deathcannon;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

final class DeathCannonOverlay extends Overlay
{
    /** Layout space of the kill-cam frame; the popup scale is a percentage of this width. */
    static final int WIDTH = 320;
    static final int HEIGHT = 244;
    private static final int COMPACT_SIZE = 128;
    private static final int MIN_FULL_WIDTH = 160;
    private static final int VIEW_X = 8;
    private static final int VIEW_Y = 66;
    private static final int VIEW_W = WIDTH - 2 * VIEW_X;
    private static final int VIEW_H = 170;
    private static final Color INK = new Color(35, 24, 13);
    private static final Color STONE = new Color(62, 53, 41);
    private static final Color STONE_EDGE = new Color(29, 25, 20);
    private static final Color STONE_LIGHT = new Color(91, 78, 60);
    private static final Color STONE_DARK = new Color(41, 35, 27);
    private static final Color VIEW_EDGE = new Color(14, 12, 9);
    private static final Color ORANGE = new Color(255, 152, 31);
    private static final Color YELLOW = new Color(255, 255, 0);
    private static final Color WHITE = new Color(255, 255, 255);
    private static final Color GREY = new Color(178, 168, 150);
    private static final Color CLANMATE = new Color(128, 196, 255);
    private static final Color SHADOW = Color.BLACK;
    private static final CacheFont REGULAR = new CacheFont("regular");
    private static final CacheFont BOLD = new CacheFont("bold");
    private static final int GRAVESTONE_SPRITE = 8920;
    private static final BufferedImage TOTAL = load("skills/overall.png");
    private static final Map<Skill, BufferedImage> ICONS = loadIcons();
    /** The red damage and blue zero hitsplats, exported from game cache sprites 1359 and 1358. */
    private static final BufferedImage DAMAGE_SPLAT = load("hitsplats/damage.png");
    private static final BufferedImage ZERO_SPLAT = load("hitsplats/zero.png");
    /** The white PK skull head icon, exported from game cache sprite 439. */
    private static final BufferedImage PK_SKULL = load("pk-skull.png");
    /** Horizontal offsets from the view centre of the skull and the hitsplat in the Wilderness PK layout. */
    static final int PK_SKULL_X = -52;
    static final int PK_SPLAT_X = 52;
    /** Layout units per scene map pixel; 51 x 28 tiles fill the view. */
    static final double MAP_SCALE = 1.5;
    /** Hiscores boss icons, exported from the cache sprites RuneLite's Hiscores panel shows. */
    private static final Map<HiscoreSkill, BufferedImage> BOSS_ICONS = loadBossIcons();
    /** RuneLite's own Hardcore Worlds Hiscores icon, shipped in the client; omitted if the client drops it. */
    private static final BufferedImage HARDCORE = loadOptional("/net/runelite/client/plugins/hiscore/hardcore_main.png");

    private final Client client;
    private final DeathCannonPlugin plugin;
    private final DeathCannonConfig config;
    @Inject private SpriteManager sprites;
    private volatile BufferedImage grave;
    private Object[] drawnKey;
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
        boolean compact = config.compactPopup();
        if (death == null || elapsed >= duration || (compact && gravestone == null))
        {
            drawnKey = null;
            card = null;
            return null;
        }

        int viewportWidth = client.getViewportWidth();
        int viewportHeight = client.getViewportHeight();
        int baseHeight = compact ? COMPACT_SIZE : HEIGHT;
        int baseWidth = compact ? COMPACT_SIZE : WIDTH;
        int requestedWidth = baseWidth * Math.max(50, Math.min(150, config.popupScale())) / 100;
        int width = Math.min(requestedWidth, Math.min(viewportWidth - 16, (viewportHeight - 20) * baseWidth / baseHeight));
        if (width < (compact ? 48 : MIN_FULL_WIDTH))
        {
            return null;
        }
        int x = width / 2;
        int y = 8;
        int height = compact ? width : (int) Math.round((double) width * baseHeight / WIDTH);
        String total = TotalPolicy.displayTotal(death);
        TopSkill top = compact || !config.showHighestSkill() ? null : death.topSkill;
        BossKc boss = compact || !config.showBossKc() ? null : death.bossKc;
        Object[] key = {death, compact, width, total, top, boss, death.killerArt, death.killerIsPlayer, death.killerTile};
        if (!Arrays.equals(key, drawnKey))
        {
            card = compact ? renderGravestone(death, total, width, gravestone)
                : renderCard(death, total, top, boss, width, height);
            drawnKey = key;
        }
        paint(graphics, card, elapsed, duration, x, y, width);
        return new Dimension(width, height + y);
    }

    void loadGravestone()
    {
        sprites.getSpriteAsync(GRAVESTONE_SPRITE, 0, image -> grave = image);
    }

    static BufferedImage renderGravestone(DeathBroadcast death, String total, int size, BufferedImage grave)
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
            REGULAR.draw(g, INK, total, size / 2, at(size, 100), textScale, textWidth, true);
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

    /**
     * The kill-cam frame: player name and stat chips on top, then a small viewport with the
     * killer's real model, the cache hitsplat and the killing blow over it.
     */
    static BufferedImage renderCard(DeathBroadcast death, String total, TopSkill top, BossKc boss, int width,
        int height)
    {
        BufferedImage card = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = card.createGraphics();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.scale((double) width / WIDTH, (double) height / HEIGHT);
            double sx = (double) width / WIDTH;
            drawFrame(g, HEIGHT);
            drawName(g, death, sx);
            drawStats(g, total, top, boss, sx);
            drawView(g, death, sx);
        }
        finally
        {
            g.dispose();
        }
        return card;
    }

    private static void drawFrame(Graphics2D g, int baseHeight)
    {
        g.setColor(STONE_EDGE);
        g.fillRect(0, 0, WIDTH, baseHeight);
        g.setColor(STONE);
        g.fillRect(2, 2, WIDTH - 4, baseHeight - 4);
        g.setColor(STONE_LIGHT);
        g.fillRect(2, 2, WIDTH - 4, 1);
        g.fillRect(2, 2, 1, baseHeight - 4);
        g.setColor(STONE_DARK);
        g.fillRect(2, baseHeight - 3, WIDTH - 4, 1);
        g.fillRect(WIDTH - 3, 2, 1, baseHeight - 4);
    }

    private static void drawName(Graphics2D g, DeathBroadcast death, double sx)
    {
        double scale = 1.6;
        int maxWidth = 236;
        double nameWidth = Math.min(maxWidth, baseWidth(BOLD, death.player, scale, sx));
        int iconSize = 20;
        int gap = HARDCORE == null ? 0 : iconSize + 5;
        int left = (int) Math.round(WIDTH / 2.0 - (nameWidth + gap) / 2);
        if (HARDCORE != null)
        {
            drawContained(g, HARDCORE, left, 12, iconSize, iconSize);
        }
        text(g, BOLD, ORANGE, death.player, left + gap, 30, scale, maxWidth, false);
        if (death.clanmate)
        {
            String label = "Clanmate";
            text(g, REGULAR, CLANMATE, label, WIDTH - 10 - (int) Math.ceil(baseWidth(REGULAR, label, 1, sx)), 15,
                1, 60, false);
        }
    }

    private static void drawStats(Graphics2D g, String total, TopSkill top, BossKc boss, double sx)
    {
        List<Object[]> chips = new ArrayList<>();
        chips.add(new Object[] {TOTAL, total, YELLOW});
        if (top != null)
        {
            chips.add(new Object[] {ICONS.get(top.skill), Integer.toString(top.level), WHITE});
        }
        if (boss != null)
        {
            String kills = NumberFormat.getIntegerInstance(Locale.US).format(boss.kills) + " kc";
            BufferedImage bossIcon = BOSS_ICONS.get(boss.boss);
            String label = bossIcon == null ? boss.name() + " " + kills : kills;
            chips.add(new Object[] {bossIcon, label, WHITE});
        }
        int icon = 20;
        int gap = 14;
        double scale = 1.5;
        double row = rowWidth(chips, scale, icon, gap, sx);
        if (row > WIDTH - 20)
        {
            scale = 1.1;
            row = rowWidth(chips, scale, icon, gap, sx);
        }
        double x = Math.max(10, WIDTH / 2.0 - row / 2);
        for (Object[] chip : chips)
        {
            BufferedImage image = (BufferedImage) chip[0];
            String value = (String) chip[1];
            if (image != null)
            {
                drawContained(g, image, (int) Math.round(x), 38, icon, icon);
                x += icon + 3;
            }
            // A row that still overflows (a long boss name without its icon) shrinks its last text to fit.
            int limit = (int) Math.max(8, Math.min(WIDTH - 10 - x, Math.ceil(baseWidth(BOLD, value, scale, sx))));
            text(g, BOLD, (Color) chip[2], value, (int) Math.round(x), 55, scale, limit, false);
            x += Math.min(limit, baseWidth(BOLD, value, scale, sx)) + gap;
        }
    }

    private static double rowWidth(List<Object[]> chips, double scale, int icon, int gap, double sx)
    {
        double width = gap * (chips.size() - 1);
        for (Object[] chip : chips)
        {
            width += (chip[0] != null ? icon + 3 : 0) + baseWidth(BOLD, (String) chip[1], scale, sx);
        }
        return width;
    }

    private static void drawView(Graphics2D g, DeathBroadcast death, double sx)
    {
        float cx = VIEW_X + VIEW_W / 2f;
        g.setPaint(new RadialGradientPaint(cx, VIEW_Y + VIEW_H * 0.8f, VIEW_W * 0.62f,
            new float[] {0f, 0.55f, 1f},
            new Color[] {new Color(76, 90, 60), new Color(44, 53, 36), new Color(22, 26, 18)}));
        g.fillRect(VIEW_X, VIEW_Y, VIEW_W, VIEW_H);
        g.setColor(VIEW_EDGE);
        g.drawRect(VIEW_X, VIEW_Y, VIEW_W - 1, VIEW_H - 1);
        g.setColor(STONE_LIGHT);
        g.drawRect(VIEW_X + 1, VIEW_Y + 1, VIEW_W - 3, VIEW_H - 3);

        boolean pk = death.wildernessPk();
        // A Wilderness PK needs no player model: the skull stands in for the killer.
        BufferedImage art = pk ? null : death.killerArt;
        SceneMap map = pk || art != null ? null : death.sceneMap;
        int mapY = VIEW_Y + 1;
        if (map != null)
        {
            Graphics2D m = (Graphics2D) g.create();
            try
            {
                m.clipRect(VIEW_X, VIEW_Y, VIEW_W, VIEW_H);
                m.translate(VIEW_X, mapY);
                m.scale(MAP_SCALE, MAP_SCALE);
                m.drawImage(map.image, 0, 0, null);
                // The marked tile, outlined like RuneLite's Instance Map player dot.
                m.setColor(SHADOW);
                m.fillRect(map.markerX - 1, map.markerY - 1, SceneMap.TILE + 2, SceneMap.TILE + 2);
                m.setColor(WHITE);
                m.fillRect(map.markerX, map.markerY, SceneMap.TILE, SceneMap.TILE);
            }
            finally
            {
                m.dispose();
            }
            // Shade the top and bottom so the location and the killer stay legible over bright terrain.
            g.setColor(new Color(0, 0, 0, 120));
            g.fillRect(VIEW_X, VIEW_Y, VIEW_W, 24);
            g.fillRect(VIEW_X, VIEW_Y + VIEW_H - 26, VIEW_W, 26);
            g.setColor(VIEW_EDGE);
            g.drawRect(VIEW_X, VIEW_Y, VIEW_W - 1, VIEW_H - 1);
        }
        int artSize = 136;
        if (art != null)
        {
            Graphics2D smooth = (Graphics2D) g.create();
            try
            {
                smooth.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                drawContained(smooth, art, Math.round(cx - artSize / 2f), VIEW_Y + 8, artSize, artSize);
            }
            finally
            {
                smooth.dispose();
            }
        }

        PlayerSighting sighting = death.sighting;
        int locationRoom = VIEW_W - 16;
        if (sighting != null && sighting.portrait != null)
        {
            int px = VIEW_X + VIEW_W - 50;
            g.setColor(VIEW_EDGE);
            g.fillRect(px - 1, VIEW_Y + 5, 46, 46);
            g.setColor(STONE_DARK);
            g.fillRect(px, VIEW_Y + 6, 44, 44);
            drawContained(g, sighting.portrait, px, VIEW_Y + 6, 44, 44);
            locationRoom -= 54;
        }
        if (sighting != null)
        {
            // A last sighting is where the player was seen, not where they died.
            String place = (death.lastSeen ? "Last seen: " : "Seen at death: ") + sighting.displayLocation();
            String location = ellipsize(REGULAR, place, locationRoom, 1.2, sx);
            text(g, REGULAR, GREY, location, VIEW_X + 6, VIEW_Y + 17, 1.2, locationRoom, false);
        }

        BufferedImage splat = death.damageValue() == 0 ? ZERO_SPLAT : DAMAGE_SPLAT;
        int viewMiddle = Math.round(VIEW_Y + VIEW_H * 0.44f);
        if (pk)
        {
            int skull = 78;
            drawContained(g, PK_SKULL, Math.round(cx) + PK_SKULL_X - skull / 2, viewMiddle - skull / 2, skull, skull);
            drawHitsplat(g, death.damage, splat, Math.round(cx) + PK_SPLAT_X, viewMiddle, 1.3, sx);
        }
        else if (map != null)
        {
            int mx = (int) Math.round(VIEW_X + (map.markerX + SceneMap.TILE / 2.0) * MAP_SCALE);
            int my = (int) Math.round(mapY + (map.markerY + SceneMap.TILE / 2.0) * MAP_SCALE);
            // The hitsplat sits just above the marked tile, or below it near the top of the view.
            int above = my - 28;
            drawHitsplat(g, death.damage, splat, mx, above > VIEW_Y + 44 ? above : my + 28, 1, sx);
        }
        else
        {
            // Without killer art or a map the hitsplat carries the view on its own; no stand-in figure is drawn.
            int splatCenter = art != null ? Math.round(VIEW_Y + VIEW_H * 0.46f) : viewMiddle;
            drawHitsplat(g, death.damage, splat, Math.round(cx), splatCenter, art != null ? 1 : 1.3, sx);
        }

        String killer = death.killer;
        if (killer != null)
        {
            Color color = death.killerIsPlayer ? WHITE : YELLOW;
            String label = ellipsize(BOLD, killer, VIEW_W - 20, 1.5, sx);
            text(g, BOLD, color, label, Math.round(cx), VIEW_Y + VIEW_H - 9, 1.5, VIEW_W - 20, true);
        }
        else
        {
            text(g, REGULAR, GREY, "Killing blow", Math.round(cx), VIEW_Y + VIEW_H - 9, 1.3, VIEW_W - 20, true);
        }
    }

    private static void drawHitsplat(Graphics2D g, String damage, BufferedImage splat, int cx, int cy,
        double size, double sx)
    {
        double textScale = 1.6 * size;
        int w = (int) Math.round(splat.getWidth() * 2 * size);
        int h = (int) Math.round(splat.getHeight() * 2 * size);
        drawContained(g, splat, cx - w / 2, cy - h / 2, w, h);
        text(g, BOLD, WHITE, damage, cx, cy + (int) Math.round(glyphHalf(textScale, sx)), textScale, w - 6, true);
    }

    /** Half the height of a digit in base units, so the number sits centred on the splat. */
    private static double glyphHalf(double scale, double sx)
    {
        return 5 * Math.max(1, Math.round(scale * sx)) / sx;
    }

    /** Draws cache-font text with the one-pixel black drop shadow the game uses. */
    private static void text(Graphics2D g, CacheFont font, Color color, String value, int x, int y, double scale,
        int maxWidth, boolean centered)
    {
        Graphics2D shadow = (Graphics2D) g.create();
        try
        {
            shadow.translate(1 / shadow.getTransform().getScaleX(), 1 / shadow.getTransform().getScaleY());
            font.draw(shadow, SHADOW, value, x, y, scale, maxWidth, centered);
        }
        finally
        {
            shadow.dispose();
        }
        font.draw(g, color, value, x, y, scale, maxWidth, centered);
    }

    /** Width in layout units of text drawn at a whole-pixel font scale, before any fit-to-width shrink. */
    static double baseWidth(CacheFont font, String text, double scale, double sx)
    {
        return font.width(text) * Math.max(1, Math.round(scale * sx)) / sx;
    }

    static String ellipsize(CacheFont font, String text, int maxWidth, double scale, double sx)
    {
        if (baseWidth(font, text, scale, sx) <= maxWidth)
        {
            return text;
        }
        String cut = text;
        while (cut.length() > 1 && baseWidth(font, cut + "...", scale, sx) > maxWidth)
        {
            cut = cut.substring(0, cut.length() - 1).stripTrailing();
        }
        return cut + "...";
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

    static void drawContained(Graphics2D g, BufferedImage image, int x, int y, int width, int height)
    {
        if (image == null)
        {
            return;
        }
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

    static BufferedImage bundledBossIcon(HiscoreSkill boss)
    {
        return BOSS_ICONS.get(boss);
    }

    private static Map<HiscoreSkill, BufferedImage> loadBossIcons()
    {
        Map<HiscoreSkill, BufferedImage> icons = new EnumMap<>(HiscoreSkill.class);
        for (HiscoreSkill skill : HiscoreSkill.values())
        {
            if (skill.getType() == HiscoreSkillType.BOSS)
            {
                BufferedImage icon = loadOptional("bosses/" + skill.name().toLowerCase(Locale.ROOT) + ".png");
                if (icon != null)
                {
                    icons.put(skill, icon);
                }
            }
        }
        return icons;
    }

    private static BufferedImage loadOptional(String name)
    {
        try (InputStream stream = DeathCannonOverlay.class.getResourceAsStream(name))
        {
            return stream == null ? null : ImageIO.read(stream);
        }
        catch (IOException | RuntimeException ex)
        {
            return null;
        }
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
