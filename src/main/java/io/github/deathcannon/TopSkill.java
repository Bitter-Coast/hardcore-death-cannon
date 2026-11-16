package io.github.deathcannon;

import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;

/** The player's highest skill: the highest level, ties going to the skill with more XP. */
final class TopSkill
{
    final Skill skill;
    final int level;
    private final long experience;

    private TopSkill(Skill skill, int level, long experience)
    {
        this.skill = skill;
        this.level = level;
        this.experience = experience;
    }

    static TopSkill fromHiscores(HiscoreResult result)
    {
        if (result == null)
        {
            return null;
        }
        TopSkill best = null;
        for (Skill skill : Skill.values())
        {
            HiscoreSkill hiscoreSkill = hiscoreSkill(skill);
            net.runelite.client.hiscore.Skill ranked = hiscoreSkill == null ? null : result.getSkill(hiscoreSkill);
            if (ranked != null)
            {
                best = choose(best, skill, ranked.getLevel(), ranked.getExperience());
            }
        }
        return best;
    }

    /** The Overall level from a Hiscores result, or 0 when it is unranked or missing. */
    static int overallLevel(HiscoreResult result)
    {
        net.runelite.client.hiscore.Skill overall = result == null ? null : result.getSkill(HiscoreSkill.OVERALL);
        return overall == null ? 0 : Math.max(0, overall.getLevel());
    }

    static TopSkill fromLocalPlayer(Client client)
    {
        TopSkill best = null;
        for (Skill skill : Skill.values())
        {
            if (hiscoreSkill(skill) != null)
            {
                best = choose(best, skill, client.getRealSkillLevel(skill), client.getSkillExperience(skill));
            }
        }
        return best;
    }

    private static HiscoreSkill hiscoreSkill(Skill skill)
    {
        // Overall is the total level, not a skill, so it never counts as the highest skill.
        try
        {
            HiscoreSkill match = HiscoreSkill.valueOf(skill.name());
            return match.getType() == HiscoreSkillType.SKILL ? match : null;
        }
        catch (IllegalArgumentException ex)
        {
            return null;
        }
    }

    static TopSkill choose(TopSkill best, Skill skill, int level, long experience)
    {
        return level > 0 && (best == null || level > best.level
            || (level == best.level && experience > best.experience)) ? new TopSkill(skill, level, experience) : best;
    }
}
