package io.github.deathcannon;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DeathBroadcast
{
    private static final String PLAYER_AND_LEVEL =
        "([a-z0-9 _-]{1,12})\\s*\\(((?:\\d{1,4}|\\d,\\d{3})|\\d(?:\\.\\d{1,3})?k)\\)";
    /** The killing blow, then the region the broadcast names, for example {@code in Karamja} or {@code in the Wilderness}. */
    private static final String HIT = "(?:a|an) ([\\d,]+) damage hit\\b(?:.*? in (?:the )?(.+?)|.*?)[.!]?";
    private static final Pattern NAMED_KILLER = Pattern.compile(
        "(.+?) killed " + PLAYER_AND_LEVEL + " with " + HIT, Pattern.CASE_INSENSITIVE);
    private static final Pattern UNNAMED_KILLER = Pattern.compile(
        PLAYER_AND_LEVEL + " was killed by " + HIT, Pattern.CASE_INSENSITIVE);
    private static final Pattern COLOR_CODES = Pattern.compile("@[a-z_]+@", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");

    final String player;
    final String levelText;
    /** The broadcast total. For an abbreviated value such as {@code 1.2k} this is the plain reading (1200). */
    final int level;
    /** The killer named by the broadcast, or null when it names only the damage hit. */
    final String killer;
    /** The killing blow as printed in the broadcast, for example {@code 12} or {@code 1,024}. */
    final String damage;
    /** The region named at the end of the broadcast, without a leading "the", or null when it names none. */
    final String region;
    final boolean clanmate;
    final PlayerSighting sighting;
    volatile TopSkill topSkill;
    /** Highest boss or raid kill count from the same Hiscores lookup, or null when there is none. */
    volatile BossKc bossKc;
    /** Overall level from the Hardcore Worlds Hiscores, or 0 until a lookup returns one. */
    volatile int hiscoreTotal;
    /** Flat-shaded render of the killer's real game model, or null when it cannot be resolved. */
    volatile BufferedImage killerArt;
    /** True only on evidence: a player with the killer's name in the scene, or a sighting with no NPC of that name. */
    volatile boolean killerIsPlayer;
    /** The killer was seen as a player on a Hardcore World before this broadcast. */
    volatile boolean killerSeen;

    private DeathBroadcast(String player, String levelText, int level, String killer, String damage, String region,
        PlayerSighting sighting, boolean clanmate)
    {
        this.player = player;
        this.levelText = levelText;
        this.level = level;
        this.killer = killer;
        this.damage = damage;
        this.region = region;
        this.clanmate = clanmate;
        this.sighting = sighting;
    }

    DeathBroadcast withSighting(PlayerSighting sighting)
    {
        return sighting == null ? this
            : new DeathBroadcast(player, levelText, level, killer, damage, region, sighting, clanmate);
    }

    DeathBroadcast withClanmate(boolean clanmate)
    {
        return clanmate ? new DeathBroadcast(player, levelText, level, killer, damage, region, sighting, true) : this;
    }

    boolean abbreviated()
    {
        return levelText.endsWith("k");
    }

    /** Damage as a number for choosing the hitsplat sprite; -1 if it does not fit. */
    int damageValue()
    {
        try
        {
            return Integer.parseInt(damage.replace(",", ""));
        }
        catch (NumberFormatException ex)
        {
            return -1;
        }
    }

    /** A killer known to be a player, in a death the broadcast places in the Wilderness. */
    boolean wildernessPk()
    {
        return killerIsPlayer && "wilderness".equalsIgnoreCase(region);
    }

    static DeathBroadcast preview(String name, int level)
    {
        return new DeathBroadcast(name, Integer.toString(level), level, "Goblin", "12", null, null, false);
    }

    static DeathBroadcast parse(String message)
    {
        String plain = plainText(message);
        Matcher match = NAMED_KILLER.matcher(plain);
        boolean named = match.matches();
        if (!named)
        {
            match = UNNAMED_KILLER.matcher(plain);
            if (!match.matches())
            {
                return null;
            }
        }

        int victim = named ? 2 : 1;
        String player = match.group(victim).trim();
        String display = match.group(victim + 1).toLowerCase(Locale.ROOT);
        String damage = match.group(victim + 2);
        String killer = named ? match.group(1).trim() : null;
        String region = match.group(victim + 3);
        region = region == null || region.isBlank() ? null : region.trim();
        boolean abbreviated = display.endsWith("k");
        int level = abbreviated
            ? new BigDecimal(display.substring(0, display.length() - 1)).movePointRight(3).intValueExact()
            : Integer.parseInt(display.replace(",", ""));
        // A rounded abbreviation such as 2.4k can still stand for the maximum total.
        boolean possible = abbreviated ? TotalPolicy.abbreviatedRange(display) != null
            : level > 0 && level <= TotalPolicy.MAX_TOTAL;
        return player.isEmpty() || !possible
            ? null : new DeathBroadcast(player, display, level, killer, damage, region, null, false);
    }

    static String plainText(String message)
    {
        String plain = TAGS.matcher(message.replace("<br>", " ")).replaceAll("").replace('\u00a0', ' ');
        return COLOR_CODES.matcher(plain).replaceAll("").trim().replaceAll("\\s+", " ");
    }
}
