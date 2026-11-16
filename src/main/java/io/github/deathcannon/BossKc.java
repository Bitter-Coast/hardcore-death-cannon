package io.github.deathcannon;

import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;

/**
 * The player's highest boss or raid kill count on the Hiscores.
 *
 * <p>Only entries Jagex lists in the Hiscores boss section count ({@link HiscoreSkillType#BOSS}),
 * which includes the raids. Clue scrolls, minigames and other activities are excluded. Raid
 * variants such as Chambers of Xeric and its Challenge Mode are separate Hiscores entries; each
 * is compared on its own and they are never added together, so the displayed entry is always one
 * real Hiscores row. When two entries have the same kill count, the one listed first on the
 * Hiscores wins, which puts a normal raid ahead of its harder variant.
 */
final class BossKc
{
    final HiscoreSkill boss;
    final int kills;

    BossKc(HiscoreSkill boss, int kills)
    {
        this.boss = boss;
        this.kills = kills;
    }

    String name()
    {
        return boss.getName();
    }

    /** Returns null when the result is missing or has no positive boss or raid kill count. */
    static BossKc fromHiscores(HiscoreResult result)
    {
        if (result == null)
        {
            return null;
        }
        BossKc best = null;
        for (HiscoreSkill entry : HiscoreSkill.values())
        {
            if (entry.getType() != HiscoreSkillType.BOSS)
            {
                continue;
            }
            net.runelite.client.hiscore.Skill score = result.getSkill(entry);
            // Unranked entries come back as -1; for bosses the kill count is reported as the level.
            int kills = score == null ? 0 : score.getLevel();
            if (kills > 0 && (best == null || kills > best.kills))
            {
                best = new BossKc(entry, kills);
            }
        }
        return best;
    }
}
