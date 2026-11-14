package io.github.deathcannon;

import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;

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
            net.runelite.client.hiscore.Skill ranked = result.getSkill(HiscoreSkill.valueOf(skill.name()));
            if (ranked != null)
            {
                best = choose(best, skill, ranked.getLevel(), ranked.getExperience());
            }
        }
        return best;
    }

    static TopSkill fromLocalPlayer(Client client)
    {
        TopSkill best = null;
        for (Skill skill : Skill.values())
        {
            best = choose(best, skill, client.getRealSkillLevel(skill), client.getSkillExperience(skill));
        }
        return best;
    }

    private static TopSkill choose(TopSkill best, Skill skill, int level, long experience)
    {
        return level > 0 && (best == null || level > best.level
            || (level == best.level && experience > best.experience)) ? new TopSkill(skill, level, experience) : best;
    }
}
