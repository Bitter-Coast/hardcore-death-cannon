package io.github.deathcannon;

import static io.github.deathcannon.TotalPolicy.Decision.HIDE;
import static io.github.deathcannon.TotalPolicy.Decision.SHOW;
import static io.github.deathcannon.TotalPolicy.Decision.UNKNOWN;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TotalPolicyTest
{
    private static DeathBroadcast death(String total)
    {
        DeathBroadcast death = DeathBroadcast.parse("Hill Giant killed Tester (" + total + ") with a 14 damage hit.");
        assertNotNull(total, death);
        return death;
    }

    @Test
    public void zeroShowsEverything()
    {
        assertEquals(SHOW, TotalPolicy.decide(death("32"), 0));
        assertEquals(SHOW, TotalPolicy.decide(death("1k"), 0));
    }

    @Test
    public void exactTotalsAreInclusive()
    {
        assertEquals(SHOW, TotalPolicy.decide(death("1,250"), 1250));
        assertEquals(HIDE, TotalPolicy.decide(death("1,249"), 1250));
        assertEquals(SHOW, TotalPolicy.decide(death("2376"), 2376));
    }

    @Test
    public void abbreviatedTotalsDecideOnlyWhenTheWholeRangeAgrees()
    {
        assertArrayEquals(new int[] {1150, 1299}, TotalPolicy.abbreviatedRange("1.2k"));
        assertEquals(SHOW, TotalPolicy.decide(death("1.2k"), 1150));
        assertEquals(HIDE, TotalPolicy.decide(death("1.2k"), 1300));
        assertEquals(UNKNOWN, TotalPolicy.decide(death("1.2k"), 1200));
        assertEquals(UNKNOWN, TotalPolicy.decide(death("1.2k"), 1299));
    }

    @Test
    public void consistentHiscoresTotalSettlesAndIsDisplayed()
    {
        DeathBroadcast death = death("1.2k");
        assertEquals("1.2k", TotalPolicy.displayTotal(death));
        death.hiscoreTotal = 1234;
        assertEquals(SHOW, TotalPolicy.decide(death, 1234));
        assertEquals(HIDE, TotalPolicy.decide(death, 1235));
        assertEquals("1234", TotalPolicy.displayTotal(death));
    }

    @Test
    public void inconsistentHiscoresTotalIsIgnored()
    {
        DeathBroadcast death = death("1.2k");
        // A stale or reset Hiscores entry outside the abbreviation's range is not trusted.
        death.hiscoreTotal = 900;
        assertEquals(UNKNOWN, TotalPolicy.decide(death, 1200));
        assertEquals("1.2k", TotalPolicy.displayTotal(death));
        death.hiscoreTotal = 0;
        assertEquals(UNKNOWN, TotalPolicy.decide(death, 1200));
    }

    @Test
    public void exactTotalsIgnoreHiscores()
    {
        DeathBroadcast death = death("1100");
        death.hiscoreTotal = 1500;
        assertEquals(HIDE, TotalPolicy.decide(death, 1200));
        assertEquals("1100", TotalPolicy.displayTotal(death));
    }

    @Test
    public void roundedAbbreviationCanStillMeanTheMaximumTotal()
    {
        assertArrayEquals(new int[] {2350, 2376}, TotalPolicy.abbreviatedRange("2.4k"));
        assertEquals(SHOW, TotalPolicy.decide(death("2.4k"), 2350));
        assertNull(TotalPolicy.abbreviatedRange("2.9k"));
        assertNull(DeathBroadcast.parse("Goblin killed Tester (2.9k) with a 1 damage hit."));
        assertArrayEquals(new int[] {500, 1999}, TotalPolicy.abbreviatedRange("1k"));
    }
}
