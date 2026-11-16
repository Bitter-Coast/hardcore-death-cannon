package io.github.deathcannon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import org.junit.Test;

public class HiscoreSelectionTest
{
    private final Map<HiscoreSkill, net.runelite.client.hiscore.Skill> skills = new EnumMap<>(HiscoreSkill.class);

    private void put(HiscoreSkill skill, int level, long experience)
    {
        skills.put(skill, new net.runelite.client.hiscore.Skill(1, level, experience));
    }

    private HiscoreResult result()
    {
        return new HiscoreResult("Tester", skills);
    }

    @Test
    public void highestSkillIgnoresOverallAndBreaksTiesByXp()
    {
        put(HiscoreSkill.OVERALL, 1500, 40_000_000);
        put(HiscoreSkill.ATTACK, 90, 5_400_000);
        put(HiscoreSkill.MINING, 90, 5_500_000);
        put(HiscoreSkill.COOKING, 80, 2_000_000);
        TopSkill top = TopSkill.fromHiscores(result());
        assertEquals(Skill.MINING, top.skill);
        assertEquals(90, top.level);
        assertEquals(1500, TopSkill.overallLevel(result()));
    }

    @Test
    public void highestLevelBeatsMoreXpAtLowerLevel()
    {
        put(HiscoreSkill.SLAYER, 91, 6_000_000);
        put(HiscoreSkill.HITPOINTS, 90, 13_000_000);
        assertEquals(Skill.SLAYER, TopSkill.fromHiscores(result()).skill);
    }

    @Test
    public void noRankedSkillsGivesNoSkillAndNoTotal()
    {
        put(HiscoreSkill.OVERALL, -1, -1);
        put(HiscoreSkill.ATTACK, -1, -1);
        assertNull(TopSkill.fromHiscores(result()));
        assertEquals(0, TopSkill.overallLevel(result()));
        assertNull(TopSkill.fromHiscores(null));
        assertEquals(0, TopSkill.overallLevel(null));
    }

    @Test
    public void bossKcPicksHighestBossIgnoringCluesMinigamesAndActivities()
    {
        put(HiscoreSkill.CLUE_SCROLL_ALL, 900, 0);
        put(HiscoreSkill.LAST_MAN_STANDING, 2000, 0);
        put(HiscoreSkill.SOUL_WARS_ZEAL, 5000, 0);
        put(HiscoreSkill.COLLECTIONS_LOGGED, 700, 0);
        put(HiscoreSkill.ZULRAH, 412, 0);
        put(HiscoreSkill.VORKATH, 380, 0);
        BossKc best = BossKc.fromHiscores(result());
        assertEquals(HiscoreSkill.ZULRAH, best.boss);
        assertEquals(412, best.kills);
    }

    @Test
    public void raidVariantsAreComparedSeparatelyNeverSummed()
    {
        put(HiscoreSkill.CHAMBERS_OF_XERIC, 60, 0);
        put(HiscoreSkill.CHAMBERS_OF_XERIC_CHALLENGE_MODE, 80, 0);
        put(HiscoreSkill.ZULRAH, 100, 0);
        // 60 + 80 would beat Zulrah if the variants were added together.
        BossKc best = BossKc.fromHiscores(result());
        assertEquals(HiscoreSkill.ZULRAH, best.boss);
        assertEquals(100, best.kills);

        skills.remove(HiscoreSkill.ZULRAH);
        assertEquals(HiscoreSkill.CHAMBERS_OF_XERIC_CHALLENGE_MODE, BossKc.fromHiscores(result()).boss);
    }

    @Test
    public void equalKillCountsGoToTheEntryListedFirst()
    {
        put(HiscoreSkill.THEATRE_OF_BLOOD_HARD_MODE, 50, 0);
        put(HiscoreSkill.THEATRE_OF_BLOOD, 50, 0);
        assertEquals(HiscoreSkill.THEATRE_OF_BLOOD, BossKc.fromHiscores(result()).boss);
    }

    @Test
    public void noPositiveBossKcGivesNothing()
    {
        assertNull(BossKc.fromHiscores(null));
        assertNull(BossKc.fromHiscores(result()));
        put(HiscoreSkill.ZULRAH, -1, -1);
        put(HiscoreSkill.VORKATH, 0, 0);
        put(HiscoreSkill.CLUE_SCROLL_ALL, 30, 0);
        assertNull(BossKc.fromHiscores(result()));
    }

    @Test
    public void oneLookupEnrichesTotalSkillAndKc()
    {
        DeathBroadcast death = DeathBroadcast.parse("Goblin killed Tester (1.2k) with a 12 damage hit.");
        put(HiscoreSkill.OVERALL, 1234, 1);
        put(HiscoreSkill.FISHING, 85, 3_300_000);
        put(HiscoreSkill.SCORPIA, 7, 0);
        DeathCannonPlugin.enrich(death, result());
        assertEquals(1234, death.hiscoreTotal);
        assertEquals(Skill.FISHING, death.topSkill.skill);
        assertEquals(HiscoreSkill.SCORPIA, death.bossKc.boss);
        assertEquals("1234", TotalPolicy.displayTotal(death));
    }
}
