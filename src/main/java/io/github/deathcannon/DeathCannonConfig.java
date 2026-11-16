package io.github.deathcannon;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(DeathCannonConfig.GROUP)
public interface DeathCannonConfig extends Config
{
    String GROUP = "hardcoreDeathCannon";
    String MINIMUM_TOTAL = "minimumTotal";
    /** Earlier key: totals strictly above this value played. Migrated to {@link #MINIMUM_TOTAL} on start-up. */
    String LEGACY_THRESHOLD = "threshold";

    @Range(min = 0, max = 2376)
    @ConfigItem(keyName = MINIMUM_TOTAL, name = "Minimum total level",
        description = "Play for broadcasts with at least this total level. A death exactly at the value plays. 0 plays all.",
        position = 0)
    default int minimumTotal()
    {
        return 251;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(keyName = "volume", name = "Volume",
        description = "Volume for the original cues. Custom cues use this when game sound effects are muted. Set to 0 for silence.", position = 1)
    default int volume()
    {
        return 45;
    }

    @Range(min = 1, max = 15)
    @ConfigItem(keyName = "popupSeconds", name = "Popup duration (seconds)",
        description = "How long each memorial stays on screen.", position = 2)
    default int popupSeconds()
    {
        return 5;
    }

    @Range(min = 50, max = 150)
    @ConfigItem(keyName = "popupScale", name = "Popup scale (%)",
        description = "Size of the memorial. Hold Alt and drag a visible popup to move it.", position = 3)
    default int popupScale()
    {
        return 71;
    }

    @ConfigItem(keyName = "compactPopup", name = "Compact gravestone",
        description = "Show the player name, total level, and any clanmate label on the gravestone.", position = 4)
    default boolean compactPopup()
    {
        return false;
    }

    @ConfigItem(keyName = "showHighestSkill", name = "Show highest skill",
        description = "Add the player's highest Hardcore Worlds Hiscores skill and level. Ties go to the skill with more XP.",
        position = 5)
    default boolean showHighestSkill()
    {
        return true;
    }

    @ConfigItem(keyName = "showBossKc", name = "Show highest boss KC",
        description = "Add the player's highest boss or raid kill count from the Hardcore Worlds Hiscores, when they have one.",
        position = 6)
    default boolean showBossKc()
    {
        return true;
    }

    @ConfigItem(keyName = "cannonSound", name = "Cannon sound",
        description = "Play the cannon blast when a memorial starts.", position = 7)
    default boolean cannonSound()
    {
        return true;
    }

    @ConfigItem(keyName = "memorialMusic", name = "Memorial music",
        description = "Play the fanfare when a memorial starts.", position = 8)
    default boolean memorialMusic()
    {
        return true;
    }

    @Range(min = -1, max = 65535)
    @ConfigItem(keyName = "cannonSoundId", name = "Cannon sound ID",
        description = "OSRS sound effect ID. Set to -1 for the original cannon sound.", position = 9)
    default int cannonSoundId()
    {
        return -1;
    }

    @Range(min = -1, max = 65535)
    @ConfigItem(keyName = "memorialSoundId", name = "Memorial sound ID",
        description = "OSRS sound effect ID. Set to -1 for the original memorial music.", position = 10)
    default int memorialSoundId()
    {
        return -1;
    }

    @ConfigItem(keyName = "clanOnly", name = "Clan only",
        description = "Only show death memorials for members of your own clan.", position = 11)
    default boolean clanOnly()
    {
        return false;
    }
}
