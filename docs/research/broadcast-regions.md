# Death broadcast regions and region maps

Research note for the kill-cam map fallback. Collected 2026-11-16.

## Question

Hardcore Worlds death broadcasts end with a region, for example:

> TzHaar-Xil killed EvScapes Fat(33) with a 2 damage hit in Karamja

What region names can appear, and is there an authoritative map image for each?

## Short answer

- **No source gives an exhaustive list of broadcast regions.** The broadcast text is built on the game server. It is not in the client cache, and Jagex has not published its format.
- **Verified exact set (cache):** the Leagues area enum in the client cache lists 11 named areas. Every region seen in broadcasts so far is one of them, spelled the same way. The plugin maps exactly these 11 names.
- **Observed set (incomplete):** a crowd-sourced tracker shows 10 of the 11 in live broadcasts. Tirannwn has not been seen there, but it is a Leagues area with its own map.
- **Map imagery:** the cache holds one Leagues area-menu sprite per area, plus the empty world map they sit on. These are bundled as the region maps.

## 1. Verified exact set: cache enum 3412

Read from the local OSRS client cache (updated 2026-11-16) with RuneLite's cache library (`net.runelite:cache` 1.12.6, `EnumLoader`, config archive 8).

| Enum | Purpose (inferred from neighbouring values) | Values |
| --- | --- | --- |
| 3412 | Leagues area names | 0 Global, 1 Misthalin, 2 Karamja, 3 Asgarnia, 4 Kandarin, 5 Morytania, 6 Kharidian Desert, 7 Tirannwn, 8 Fremennik Province, 10 Kourend & Kebos, 11 Wilderness, 20 Kourend & Kebos, 21 Varlamore, 24 Unlocked Areas |
| 3340 | The same areas, written with articles | 1 Misthalin … 6 The Kharidian Desert, 8 The Fremennik Province, 11 The Wilderness, 10/20 Kourend & Kebos, 21 Varlamore |
| 3414 | Short labels | Misthalin, Karamja, Asgarnia, Kandarin, Morytania, Desert, Tirannwn, Fremennik, Wilderness, Kourend, Varlamore |

Leaving out the UI entries (Global, Unlocked Areas) and the duplicate Kourend & Kebos gives 11 names:

**Misthalin, Karamja, Asgarnia, Kandarin, Morytania, Kharidian Desert, Tirannwn, Fremennik Province, Wilderness, Kourend & Kebos, Varlamore.**

Cache structs in config archive 34 describe each area. For example: "The Tirannwn area covers the entirety of Tirannwn along with the Temple of Light and the Underground Pass. Instances of areas in Tirannwn and underground areas with an entrance in Tirannwn will also be included." Varlamore covers "the entirety of Varlamore, including the island of Aldarin."

**What this proves:** these are Jagex's own region names, with the exact spellings and boundaries the game uses for Leagues.

**What it does not prove:** that the Hardcore Worlds broadcast uses this enum, or that it never names anything else. A string search of every decoded cache file finds no `damage hit` text, so the broadcast template is not in the cache. The match rests on spelling. "Kharidian Desert", "Fremennik Province" and "Kourend & Kebos" all appear in broadcasts exactly as in enum 3412.

Other cache lists that were checked and ruled out:

- Enum 2096, a town list with the default "No Location", contains "Kharidian Desert" and "Wilderness" but not "Karamja", which real broadcasts use.
- Enum 595, the achievement diary areas, uses "Desert", "Fremennik", "Varrock" and other names that do not match.

## 2. Observed set: live broadcasts (incomplete)

| Source | What it shows |
| --- | --- |
| Real broadcast supplied by the user | `TzHaar-Xil killed EvScapes Fat(33) with a 2 damage hit in Karamja`: no space before the level, no trailing full stop |
| [hcwtoolkit.com/deaths](https://hcwtoolkit.com/deaths), "Where they die" | Wilderness, Misthalin, Kandarin, Asgarnia, Varlamore, Kharidian Desert, Karamja, Kourend & Kebos, Morytania, Fremennik Province. The page says it is "A sample: the deaths that players running the plugin saw broadcast, not every death." It does not say how regions are derived and does not claim the list is complete. 6,430 deaths over 7 days at 2026-11-16 21:47 UTC. |
| [hcwtoolkit.com/tools/death-map](https://hcwtoolkit.com/tools/death-map) | The same data shown on a map, rendered client-side. The map's image source is not documented. |
| [Novafifth/hardcore-world-tool-kit](https://github.com/Novafifth/hardcore-world-tool-kit) @ `f405e46` (the tracker's RuneLite plugin, from [plugin-hub `plugins/hardcore-world-tool-kit`](https://github.com/runelite/plugin-hub/blob/master/plugins/hardcore-world-tool-kit)) | The plugin uploads the raw broadcast text. Region parsing happens on its server, which is not public. Its test fixtures include `... with a 25 damage hit in the Wilderness.` and `... with a 24 damage hit in Kandarin.`. These are fixtures, not verified live messages, but they show the article before Wilderness, as in enum 3340. |

Tirannwn is the only cache area not yet observed. It may be rare, or it may be reported differently. A death with no region (for example on Tutorial Island, at sea or in an unmapped instance) has not been observed, so its wording is unknown.

Pages read with no region details: [OSRS Wiki: Hardcore Worlds](https://oldschool.runescape.wiki/w/Hardcore_Worlds), [OSRS Wiki: Hardcore Worlds/Records](https://oldschool.runescape.wiki/w/Hardcore_Worlds/Records), [Jagex: The Reaper's Raffle – Hardcore Worlds](https://secure.runescape.com/m=news/the-reapers-raffle--hardcore-worlds?oldschool=1). None of them gives the death broadcast format or a region list.

## 3. Region maps: cache sprites 8059–8070

RuneLite names these sprites in `net.runelite.api.gameval.SpriteID` ([runelite/runelite](https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/gameval/SpriteID.java)):

| Sprite | RuneLite name | Bundled as |
| --- | --- | --- |
| 8059 | `LEAGUE_6_MAP_EMPTY` | `regions/empty.png` |
| 8060 | `LEAGUE_6_MAP_MISTHALIN` | `regions/misthalin.png` |
| 8061 | `LEAGUE_6_MAP_KARAMJA` | `regions/karamja.png` |
| 8062 | `LEAGUE_6_MAP_WILDERNESS` | `regions/wilderness.png` |
| 8063 | `LEAGUE_6_MAP_ASGARNIA` | `regions/asgarnia.png` |
| 8064 | `LEAGUE_6_MAP_KANDARIN` | `regions/kandarin.png` |
| 8065 | `LEAGUE_6_MAP_DESERT` | `regions/desert.png` |
| 8066 | `LEAGUE_6_MAP_FREMENNIK` | `regions/fremennik.png` |
| 8067 | `LEAGUE_6_MAP_TIRANNWN` | `regions/tirannwn.png` |
| 8068 | `LEAGUE_6_MAP_MORYTANIA` | `regions/morytania.png` |
| 8069 | `LEAGUE_6_MAP_KOUREND` | `regions/kourend.png` |
| 8070 | `LEAGUE_6_MAP_VARLAMORE` | `regions/varlamore.png` |

All twelve share a 468 × 214 canvas. Each area sprite has its own offset on that canvas. They were exported with `SpriteManager`, and each was drawn at its offset onto a transparent canvas of that size, so the bundled overlays line up with the empty map with no offset table. Pixels are unchanged. The bundled set is about 60 KB.

Leagues 4 and 5 have the same family of sprites (5448–5458, 5865–5876), and Trailblazer has an older one (2721–2730, without Kourend or Varlamore). League 6 is the newest set and the only one that covers all 11 areas.

How the plugin uses them: it shows the empty map with one area overlaid, cropped around that area's largest landmass to the viewport's aspect. Some areas include distant islands, and cropping to all of them would leave the main landmass tiny. This is a regional view. It never marks a tile or claims an exact place of death.

## 4. Coverage limits

- The 11-name set is exact for the cache. It is not proven exhaustive for broadcasts. A region outside it is still parsed and shown as text, with no map.
- Matching ignores case and a leading `the` (`in the Wilderness` gives `Wilderness`).
- If Jagex adds an area (a new Leagues area, or a Sailing ocean region), it shows as text until a matching sprite is bundled.
- The sprites are schematic Leagues art, not terrain renders. They show which region is meant, not what it looks like at ground level.
