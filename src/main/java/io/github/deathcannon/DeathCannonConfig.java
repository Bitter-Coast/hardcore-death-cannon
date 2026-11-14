package io.github.deathcannon;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("hardcoreDeathCannon")
public interface DeathCannonConfig extends Config
{
    @Range(min = 0, max = 2376)
    @ConfigItem(keyName = "threshold", name = "Total level threshold",
        description = "Play for broadcasts with a total level strictly above this value.", position = 0)
    default int threshold()
    {
        return 500;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(keyName = "volume", name = "Volume",
        description = "Cannon and music volume. Set to 0 for a silent animation.", position = 1)
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
        return 70;
    }

    @ConfigItem(keyName = "compactPopup", name = "Compact gravestone",
        description = "Show only the player name and total level on the gravestone.", position = 4)
    default boolean compactPopup()
    {
        return false;
    }

    @ConfigItem(keyName = "cannonSound", name = "Cannon sound",
        description = "Play the cannon blast when a memorial starts.", position = 5)
    default boolean cannonSound()
    {
        return true;
    }

    @ConfigItem(keyName = "memorialMusic", name = "Memorial music",
        description = "Play the fanfare when a memorial starts.", position = 6)
    default boolean memorialMusic()
    {
        return true;
    }
}
