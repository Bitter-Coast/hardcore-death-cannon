package io.github.deathcannon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.runelite.api.Model;
import net.runelite.api.NPCComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;
import org.junit.Test;

public class KillCamRenderTest
{
    @SuppressWarnings("unchecked")
    static <T> T stub(Class<T> type, Map<String, Object> values)
    {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) ->
        {
            if (values.containsKey(method.getName()))
            {
                return values.get(method.getName());
            }
            Class<?> returns = method.getReturnType();
            if (returns == boolean.class)
            {
                return false;
            }
            if (returns.isPrimitive() && returns != void.class)
            {
                return returns == float.class ? 0f : returns == double.class ? 0d
                    : returns == long.class ? 0L : returns == short.class ? (short) 0
                    : returns == byte.class ? (byte) 0 : returns == char.class ? (char) 0 : 0;
            }
            return null;
        });
    }

    /** A two-triangle quad with flat colours, enough to exercise the real renderer. */
    private static Model quad()
    {
        Map<String, Object> values = new HashMap<>();
        values.put("getVerticesCount", 4);
        values.put("getFaceCount", 2);
        values.put("getVerticesX", new float[] {-50, 50, 50, -50});
        values.put("getVerticesY", new float[] {-100, -100, 0, 0});
        values.put("getVerticesZ", new float[] {0, 0, 0, 0});
        values.put("getFaceIndices1", new int[] {0, 0});
        values.put("getFaceIndices2", new int[] {1, 2});
        values.put("getFaceIndices3", new int[] {2, 3});
        values.put("getFaceColors1", new int[] {(10 << 10) | (5 << 7) | 70, (40 << 10) | (6 << 7) | 50});
        values.put("getFaceColors2", new int[] {0, 0});
        values.put("getFaceColors3", new int[] {-1, -1});
        return stub(Model.class, values);
    }

    @Test
    public void rendersModelsAtTheRequestedSize()
    {
        BufferedImage art = PlayerPortrait.render(quad(), KillerArt.SIZE, 1, 1);
        assertEquals(KillerArt.SIZE, art.getWidth());
        assertNotEquals(0, art.getRGB(KillerArt.SIZE / 2, KillerArt.SIZE / 2) >>> 24);
        assertEquals(PlayerPortrait.SIZE, PlayerPortrait.render(quad()).getWidth());
        assertNull(PlayerPortrait.render(null, 64, 1, 1));
    }

    private static NPCComposition npc(int id, int combat, int[] models, int scale)
    {
        Map<String, Object> values = new HashMap<>();
        values.put("getId", id);
        values.put("getCombatLevel", combat);
        values.put("getModels", models);
        values.put("getWidthScale", scale);
        values.put("getHeightScale", scale);
        return stub(NPCComposition.class, values);
    }

    @Test
    public void cacheDefinitionChoicePrefersAttackableThenCommonestAppearance()
    {
        NPCComposition talking = npc(1, 0, new int[] {9}, 128);
        NPCComposition rare = npc(2, 5, new int[] {7}, 128);
        NPCComposition commonHigh = npc(9, 5, new int[] {8}, 128);
        NPCComposition commonLow = npc(4, 2, new int[] {8}, 128);
        assertSame(commonLow, KillerArt.choose(Arrays.asList(talking, rare, commonHigh, commonLow)));
        // Equal counts: the appearance with the lowest NPC id wins.
        assertSame(rare, KillerArt.choose(Arrays.asList(commonHigh, rare)));
        // Nothing attackable: every definition is considered.
        assertSame(talking, KillerArt.choose(List.of(talking)));
    }

    private static DeathBroadcast sample(String message, boolean sighting)
    {
        DeathBroadcast death = DeathBroadcast.parse(message);
        if (sighting)
        {
            death = death.withSighting(new PlayerSighting(PlayerPortrait.render(quad()), Instant.EPOCH, 301,
                new WorldPoint(3222, 3218, 0)), true);
        }
        return death;
    }

    @Test
    public void everyHiscoresBossHasItsBundledCacheIcon()
    {
        for (HiscoreSkill skill : HiscoreSkill.values())
        {
            if (skill.getType() == HiscoreSkillType.BOSS)
            {
                assertNotNull(skill.name(), DeathCannonOverlay.bundledBossIcon(skill));
            }
        }
        assertNull(DeathCannonOverlay.bundledBossIcon(HiscoreSkill.CLUE_SCROLL_ALL));
    }

    @Test
    public void rendersEveryKillCamVariant() throws IOException
    {
        DeathBroadcast full = sample("Hill Giant killed Some_Player (1,024) with a 14 damage hit.", true);
        full.killerArt = PlayerPortrait.render(quad(), KillerArt.SIZE, 1, 1);
        full.topSkill = TopSkill.choose(null, net.runelite.api.Skill.SLAYER, 91, 6_000_000);
        full.bossKc = new BossKc(HiscoreSkill.CHAMBERS_OF_XERIC_CHALLENGE_MODE, 1234);
        DeathBroadcast bare = sample("Tester (1.2k) was killed by an 8 damage hit.", false);

        int[] widths = {160, 227, 320, 480};
        for (int width : widths)
        {
            int height = (int) Math.round((double) width * DeathCannonOverlay.HEIGHT / DeathCannonOverlay.WIDTH);
            BufferedImage card = DeathCannonOverlay.renderCard(full, TotalPolicy.displayTotal(full), full.topSkill,
                full.bossKc, width, height);
            assertEquals(width, card.getWidth());
            assertEquals(height, card.getHeight());
            write(card, "killcam-full-" + width + ".png");
        }
        int height = (int) Math.round(227.0 * DeathCannonOverlay.HEIGHT / DeathCannonOverlay.WIDTH);
        BufferedImage noArt = DeathCannonOverlay.renderCard(bare, TotalPolicy.displayTotal(bare), null, null, 227,
            height);
        assertNotNull(noArt);
        write(noArt, "killcam-fallback-227.png");
    }

    private static void write(BufferedImage image, String name) throws IOException
    {
        File directory = new File("build/killcam-previews");
        if (directory.isDirectory() || directory.mkdirs())
        {
            ImageIO.write(image, "png", new File(directory, name));
        }
    }
}
