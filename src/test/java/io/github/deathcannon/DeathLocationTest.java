package io.github.deathcannon;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.time.Instant;
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
    private static final WorldPoint WILDERNESS = new WorldPoint(3100, 3700, 0);
    /** Every region name in the cache's Leagues area enum 3412, as the broadcasts spell them. */
    private static final String[] REGIONS = {"Misthalin", "Karamja", "Asgarnia", "Kandarin", "Morytania",
        "Kharidian Desert", "Tirannwn", "Fremennik Province", "Wilderness", "Kourend & Kebos", "Varlamore"};

    private static BufferedImage card(DeathBroadcast death)
    {
        return DeathCannonOverlay.renderCard(death, TotalPolicy.displayTotal(death), null, null,
            DeathCannonOverlay.WIDTH, DeathCannonOverlay.HEIGHT);
    }

    private static DeathBroadcast seen(String message)
    {
        // A sighting far from the broadcast region: it may supply a portrait, never the place of death.
        return DeathBroadcast.parse(message).withSighting(new PlayerSighting(null, Instant.EPOCH, 301, WILDERNESS));
    }

    /** Pixels in the view with the Leagues sprites' exact land and region colours, which nothing else uses. */
    private static int mapPixels(BufferedImage card, int rgb)
    {
        int count = 0;
        for (int y = 67; y < 235; y++)
        {
            for (int x = 9; x < DeathCannonOverlay.WIDTH - 9; x++)
            {
                if ((card.getRGB(x, y) & 0xffffff) == rgb)
                {
                    count++;
                }
            }
        }
        return count;
    }

    private static int land(BufferedImage card)
    {
        return mapPixels(card, 0x36302e);
    }

    @Test
    public void everyCacheRegionHasABroadMap()
    {
        for (String region : REGIONS)
        {
            BufferedImage map = RegionMap.of(region, 302, 168);
            assertNotNull(region, map);
            // The crop keeps the view's aspect, so the region is never stretched.
            assertEquals(region, 302.0 / 168, (double) map.getWidth() / map.getHeight(), 0.03);
        }
        assertNull(RegionMap.of("Tutorial Island", 302, 168));
        assertNull(RegionMap.of(null, 302, 168));
    }

    @Test
    public void farIslandsDoNotShrinkTheMainLandmass()
    {
        BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_ARGB);
        for (int y = 2; y < 12; y++)
        {
            for (int x = 3; x < 13; x++)
            {
                image.setRGB(x, y, 0xff800000);
            }
        }
        image.setRGB(38, 18, 0xff800000);
        assertArrayEquals(new int[] {3, 2, 13, 12}, RegionMap.largestLandmass(image));
    }

    @Test
    public void regionMapComesOnlyFromTheBroadcast()
    {
        BufferedImage asgarnia = card(seen("Tester (1,500) was killed by a 31 damage hit in Asgarnia."));
        assertTrue("the world map is drawn", land(asgarnia) > 2000);
        assertTrue("the broadcast region is highlighted", mapPixels(asgarnia, 0x520000) > 500);

        // No region in the broadcast: the sighting does not stand in, so there is no map.
        assertEquals(0, land(card(seen("Tester (1,500) was killed by a 31 damage hit."))));
        // A named region without a bundled map is shown as text only.
        DeathBroadcast unknown = seen("Tester (1,500) was killed by a 31 damage hit in Tutorial Island.");
        assertEquals("Tutorial Island", unknown.region);
        assertEquals(0, land(card(unknown)));
    }

    @Test
    public void wildernessPkDrawsTheSkullLeftOfTheHitsplat()
    {
        DeathBroadcast death = seen("Pker killed Tester (1,500) with a 38 damage hit in the Wilderness.");
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

        DeathBroadcast town = seen("Pker killed Tester (1,500) with a 38 damage hit in Misthalin.");
        town.killerIsPlayer = true;
        assertFalse("a player killer outside the Wilderness is not a Wilderness PK", town.wildernessPk());
        DeathBroadcast nowhere = seen("Pker killed Tester (1,500) with a 38 damage hit.");
        nowhere.killerIsPlayer = true;
        assertFalse("a sighting in the Wilderness does not make a Wilderness PK", nowhere.wildernessPk());
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
        DeathBroadcast death = DeathBroadcast.parse(killer + " killed Tester (1,500) with a 38 damage hit in the Wilderness.");
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
        DeathBroadcast scene = classify(killerArt(List.of(KillCamRenderTest.stub(Player.class, values))), "Pker", false);
        assertTrue("a player in the scene is the killer", scene.killerIsPlayer);
        assertTrue(scene.wildernessPk());
    }
}
