package io.github.deathcannon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DeathBroadcastTest
{
    @Test
    public void namedKillerKeepsKillerAndDamageApart()
    {
        DeathBroadcast death = DeathBroadcast.parse(
            "<col=ef1020>Hill Giant killed Some_Player (1,024) with a 1,234 damage hit!</col>");
        assertEquals("Some_Player", death.player);
        assertEquals("Hill Giant", death.killer);
        assertEquals("1,234", death.damage);
        assertEquals(1234, death.damageValue());
        assertEquals(1024, death.level);
        assertFalse(death.abbreviated());
    }

    @Test
    public void unnamedKillerHasNoKiller()
    {
        DeathBroadcast death = DeathBroadcast.parse("Tester (1.2K) was killed by an 8 damage hit.");
        assertNull(death.killer);
        assertEquals("8", death.damage);
        assertEquals("1.2k", death.levelText);
        assertTrue(death.abbreviated());
    }

    @Test
    public void capturesTheTrailingRegion()
    {
        DeathBroadcast real = DeathBroadcast.parse("TzHaar-Xil killed EvScapes Fat(33) with a 2 damage hit in Karamja");
        assertEquals("TzHaar-Xil", real.killer);
        assertEquals("EvScapes Fat", real.player);
        assertEquals("2", real.damage);
        assertEquals("Karamja", real.region);
        assertEquals("Wilderness",
            DeathBroadcast.parse("Pker(120) killed Tester(70) with a 25 damage hit in the Wilderness.").region);
        assertEquals("Kourend & Kebos",
            DeathBroadcast.parse("Tester (1.2k) was killed by an 8 damage hit in Kourend & Kebos!").region);
        assertEquals("Kharidian Desert",
            DeathBroadcast.parse("Tester (99) was killed by a 3 damage hit from a Goblin in the Kharidian Desert.").region);
        assertNull(DeathBroadcast.parse("Tester (99) was killed by a 3 damage hit from a Hill Giant.").region);
        assertNull(DeathBroadcast.parse("Tester (99) was killed by a 3 damage hit.").region);
    }

    @Test
    public void rejectsImpossibleTotalsAndOtherMessages()
    {
        assertNull(DeathBroadcast.parse("Goblin killed Tester (2377) with a 1 damage hit."));
        assertNull(DeathBroadcast.parse("Goblin killed Tester (0) with a 1 damage hit."));
        assertNull(DeathBroadcast.parse("Tester has reached level 99 Fishing."));
    }

    @Test
    public void previewUsesAnNpcKiller()
    {
        DeathBroadcast preview = DeathBroadcast.preview("Me", 500);
        assertEquals("Goblin", preview.killer);
        assertEquals("12", preview.damage);
    }

    @Test
    public void articlesAreStrippedForNameLookups()
    {
        assertEquals("Goblin", KillerArt.stripArticle("a Goblin"));
        assertEquals("Imp", KillerArt.stripArticle("An Imp"));
        assertEquals("Annie", KillerArt.stripArticle("Annie"));
        assertEquals("hill giant", KillerArt.nameKey("<col=ffff00>Hill Giant</col>"));
    }
}
