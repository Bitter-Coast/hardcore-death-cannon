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
