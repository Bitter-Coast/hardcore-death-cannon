# Hardcore Death Cannon

When RuneLite receives a Hardcore Worlds death broadcast above your total-level threshold, it plays the original cannon blast and memorial fanfare. The memorial uses a rolled parchment, gravestone sprite 8920 frame 0, and skill icons from the Old School game cache. Qualifying announcements play in order.

## Run locally

1. Open a terminal at the project root.
2. Run `gradle run` with a JDK and Gradle installed.
3. In the client, enable **Hardcore Death Cannon**. The default total level threshold is **500**, the volume is **45%**, and the popup stays up for **5 seconds** at **70%** scale. Choose a duration from **1 to 15 seconds** and a scale from **50% to 150%**. The popup shrinks to fit smaller game viewports. A death at level 500 is below the trigger condition. Volume 0 leaves the animation on.
4. While logged in, enter `::deathcannon` in game chat to preview the effect with your own character model and highest skill on any world. Hold **Alt** and drag the visible popup to move it. RuneLite saves the position. Actual broadcasts trigger on Hardcore Worlds.

Turn on **Compact gravestone** to show just the player name and total level on the cache gravestone sprite. **Cannon sound** and **Memorial music** are separate switches. Both use the **Volume** setting.

The full memorial shows the killer and final hit when the broadcast names the killer, or the final hit alone otherwise. Click the original death broadcast in chat to replay the memorial using your current settings. Replay entries are kept for the latest 100 qualifying broadcast texts during the current plugin session.

For a Jagex Account, open **Jagex Account** in the RuneLite sidebar, click **Add Jagex Account**, and complete sign-in in the window that opens. Select your character in the sidebar, then click **Play Now**. [RuneLite developer sign-in guide](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)


## Broadcast coverage

The plugin reacts to broadcasts that Jagex sends to your client. It compares the level printed in the announcement, so an abbreviated value such as `1.2k` is treated as 1200. For qualifying broadcasts it asynchronously looks up the player's highest reported skill on the Hardcore Worlds Hiscores. Ties go to the skill with more XP. If the lookup is unavailable or too slow for the popup, the total-level row still appears.

The memorial uses Old School bitmap fonts and preserves the proportions of the original sprites at any popup scale. When a player's model is loaded in your scene, it shows a flat-shaded portrait based on the model's shape and colors. The plugin keeps up to 500 recent Hardcore Worlds sightings across world hops and RuneLite sessions. It saves new or updated sightings locally about every 30 seconds and on a normal exit. If a player dies elsewhere, the popup shows the local date, world, and last-seen location beside their cached portrait. It looks up nearby named areas from local OSRS world map data and shows **Unknown location** when a name cannot be resolved. The cache lives in `.runelite/plugin-data/hardcore-death-cannon/sightings.dat.gz`.
