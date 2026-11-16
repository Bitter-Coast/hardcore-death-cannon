package io.github.deathcannon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexDataBase;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class DeathLocationTest
{
    private static final int MAP_SIZE = SceneMap.MARGIN * 2 + SceneMap.SCENE * SceneMap.TILE;
    private static final int TERRAIN = 0x506040;
    private static final int MARK = 0xff00ff;
    private static final WorldPoint LUMBRIDGE = new WorldPoint(3222, 3218, 0);
    private static final WorldPoint WILDERNESS = new WorldPoint(3100, 3700, 0);

    /** A scene map laid out like the client's, with one scene tile painted. */
    private static int[] sceneWithTile(int sceneX, int sceneY)
    {
        int[] pixels = new int[MAP_SIZE * MAP_SIZE];
        Arrays.fill(pixels, TERRAIN);
        int left = SceneMap.MARGIN + sceneX * 4;
        int top = SceneMap.MARGIN + (103 - sceneY) * 4;
        for (int y = top; y < top + 4; y++)
        {
            Arrays.fill(pixels, y * MAP_SIZE + left, y * MAP_SIZE + left + 4, MARK);
        }
        return pixels;
    }

    @Test
    public void mapMarkerLandsOnTheSightedTile()
    {
        // Centre, south-west corner and north-east corner of the scene.
        for (int[] tile : new int[][] {{54, 50}, {0, 0}, {103, 103}})
        {
            SceneMap map = SceneMap.crop(sceneWithTile(tile[0], tile[1]), MAP_SIZE, MAP_SIZE, tile[0], tile[1]);
            assertEquals(MARK, map.image.getRGB(map.markerX, map.markerY) & 0xffffff);
            assertEquals(MARK, map.image.getRGB(map.markerX + 3, map.markerY + 3) & 0xffffff);
            if (map.markerX > 0)
            {
                assertEquals(TERRAIN, map.image.getRGB(map.markerX - 1, map.markerY) & 0xffffff);
            }
        }
        SceneMap centre = SceneMap.crop(sceneWithTile(54, 50), MAP_SIZE, MAP_SIZE, 54, 50);
        assertEquals(SceneMap.COLUMNS * 2 - 2, centre.markerX);
    }

    @Test
    public void tilesOutsideTheSceneHaveNoMap()
    {
        int[] pixels = sceneWithTile(10, 10);
        assertNull(SceneMap.crop(pixels, MAP_SIZE, MAP_SIZE, -1, 10));
        assertNull(SceneMap.crop(pixels, MAP_SIZE, MAP_SIZE, 10, 104));
        assertNull(SceneMap.crop(null, MAP_SIZE, MAP_SIZE, 10, 10));
    }

    private static BufferedImage card(DeathBroadcast death)
    {
        return DeathCannonOverlay.renderCard(death, TotalPolicy.displayTotal(death), null, null,
            DeathCannonOverlay.WIDTH, DeathCannonOverlay.HEIGHT);
    }

    private static DeathBroadcast seen(String message, WorldPoint tile)
    {
        return DeathBroadcast.parse(message).withSighting(new PlayerSighting(null, Instant.EPOCH, 301, tile), true);
    }

    @Test
    public void overlayMarksTheMapTileAndDrawsNoMapWithoutALocation()
    {
        DeathBroadcast death = seen("Tester (1,500) was killed by a 31 damage hit.", LUMBRIDGE);
        death.sceneMap = SceneMap.crop(sceneWithTile(54, 50), MAP_SIZE, MAP_SIZE, 54, 50);
        BufferedImage withMap = card(death);
        // The view's map starts at (8, 67), scaled by MAP_SCALE; sample the middle of the marked tile.
        int mx = (int) (8 + (death.sceneMap.markerX + 2) * DeathCannonOverlay.MAP_SCALE);
        int my = (int) (67 + (death.sceneMap.markerY + 2) * DeathCannonOverlay.MAP_SCALE);
        assertEquals(0xffffff, withMap.getRGB(mx, my) & 0xffffff);
        assertEquals(TERRAIN, withMap.getRGB(mx + 60, my) & 0xffffff);

        BufferedImage noMap = card(DeathBroadcast.parse("Tester (1,500) was killed by a 31 damage hit."));
        assertNotEquals(TERRAIN, noMap.getRGB(mx + 60, my) & 0xffffff);
    }

    @Test
    public void wildernessBoundsCoverTheSurfaceAndDungeonsOnly()
    {
        assertTrue(DeathBroadcast.inWilderness(WILDERNESS));
        assertTrue(DeathBroadcast.inWilderness(new WorldPoint(3200, 10100, 0)));
        assertFalse(DeathBroadcast.inWilderness(new WorldPoint(3094, 3491, 0)));
        assertFalse(DeathBroadcast.inWilderness(LUMBRIDGE));
        assertFalse(DeathBroadcast.inWilderness(null));
    }

    @Test
    public void wildernessPkDrawsTheSkullLeftOfTheHitsplat()
    {
        String message = "Pker killed Tester (1,500) with a 38 damage hit.";
        DeathBroadcast death = seen(message, WILDERNESS);
        assertFalse("an unconfirmed killer is not a PK", death.wildernessPk());
        death.killerIsPlayer = true;
        assertTrue(death.wildernessPk());

        BufferedImage pk = card(death);
        int cx = DeathCannonOverlay.WIDTH / 2;
        int skull = 0;
        int redLeft = 0;
        int redRight = 0;
        for (int y = 90; y < 210; y++)
        {
            for (int x = 10; x < DeathCannonOverlay.WIDTH - 10; x++)
            {
                int rgb = pk.getRGB(x, y);
                int r = rgb >> 16 & 255;
                int g = rgb >> 8 & 255;
                int b = rgb & 255;
                if (r > 150 && g < 60 && b < 60)
                {
                    if (x < cx)
                    {
                        redLeft++;
                    }
                    else
                    {
                        redRight++;
                    }
                }
                if (x < cx - 15 && r > 200 && g > 200 && b > 200)
                {
                    skull++;
                }
            }
        }
        assertTrue("skull left of centre", skull > 200);
        assertTrue("hitsplat right of centre", redRight > 200);
        assertEquals("no hitsplat left of centre", 0, redLeft);

        DeathBroadcast town = seen(message, LUMBRIDGE);
        town.killerIsPlayer = true;
        assertFalse("a player killer outside the Wilderness is not a Wilderness PK", town.wildernessPk());
    }

    @SuppressWarnings("unchecked")
    private static <T> IndexedObjectSet<T> set(List<T> items)
    {
        return (IndexedObjectSet<T>) Proxy.newProxyInstance(IndexedObjectSet.class.getClassLoader(),
            new Class<?>[] {IndexedObjectSet.class},
            (proxy, method, args) -> "iterator".equals(method.getName()) ? items.iterator() : null);
    }

    /** A logged-in client whose cache knows one NPC name, Goblin, and whose scene holds the given players. */
    private static KillerArt killerArt(List<Player> players)
    {
        Map<String, Object> npc = new HashMap<>();
        npc.put("getName", "Goblin");
        npc.put("getModels", new int[] {1});
        npc.put("getCombatLevel", 2);
        Map<String, Object> index = new HashMap<>();
        index.put("getFileIds", new int[] {1});
        Map<String, Object> view = new HashMap<>();
        view.put("npcs", set(List.of()));
        view.put("players", set(players));
        Map<String, Object> client = new HashMap<>();
        client.put("getGameState", GameState.LOGGED_IN);
        client.put("getIndexConfig", KillCamRenderTest.stub(IndexDataBase.class, index));
        client.put("getNpcDefinition", KillCamRenderTest.stub(NPCComposition.class, npc));
        client.put("getTopLevelWorldView", KillCamRenderTest.stub(WorldView.class, view));
        return new KillerArt(KillCamRenderTest.stub(Client.class, client));
    }

    private static DeathBroadcast classify(KillerArt art, String killer, boolean seen)
    {
        DeathBroadcast death = DeathBroadcast.parse(killer + " killed Tester (1,500) with a 38 damage hit.");
        death.killerSeen = seen;
        art.request(death);
        art.tick();
        return death;
    }

    @Test
    public void killersArePlayersOnlyOnEvidence()
    {
        KillerArt empty = killerArt(List.of());
        assertFalse("an unmatched name alone is not a player", classify(empty, "Zezima", false).killerIsPlayer);
        assertTrue("an unmatched name seen as a player is one", classify(empty, "Zezima", true).killerIsPlayer);
        assertFalse("an NPC name stays an NPC", classify(empty, "Goblin", true).killerIsPlayer);

        Map<String, Object> values = new HashMap<>();
        values.put("getName", "Pker");
        values.put("getWorldLocation", WILDERNESS);
        DeathBroadcast scene = classify(killerArt(List.of(KillCamRenderTest.stub(Player.class, values))), "Pker", false);
        assertTrue("a player in the scene is the killer", scene.killerIsPlayer);
        assertTrue(scene.wildernessPk());
    }
}
