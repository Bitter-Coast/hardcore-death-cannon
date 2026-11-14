package io.github.deathcannon;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DeathBroadcast
{
    private static final String PLAYER_AND_LEVEL =
        "([a-z0-9 _-]{1,12})\\s*\\(((?:\\d{1,4}|\\d,\\d{3})|\\d(?:\\.\\d{1,3})?k)\\)";
    private static final String HIT = "(?:a|an) ([\\d,]+) damage hit\\b.*";
    private static final Pattern NAMED_KILLER = Pattern.compile(
        "(.+?) killed " + PLAYER_AND_LEVEL + " with " + HIT, Pattern.CASE_INSENSITIVE);
    private static final Pattern UNNAMED_KILLER = Pattern.compile(
        PLAYER_AND_LEVEL + " was killed by " + HIT, Pattern.CASE_INSENSITIVE);
    private static final Pattern COLOR_CODES = Pattern.compile("@[a-z_]+@", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");

    final String player;
    final String levelText;
    final int level;
    final String cause;
    final boolean lastSeen;
    final boolean clanmate;
    final PlayerSighting sighting;
    volatile TopSkill topSkill;

    private DeathBroadcast(String player, String levelText, int level, String cause, PlayerSighting sighting, boolean lastSeen, boolean clanmate)
    {
        this.player = player;
        this.levelText = levelText;
        this.level = level;
        this.cause = cause;
        this.lastSeen = lastSeen;
        this.clanmate = clanmate;
        this.sighting = sighting;
    }

    DeathBroadcast withSighting(PlayerSighting sighting, boolean lastSeen)
    {
        return sighting == null ? this : new DeathBroadcast(player, levelText, level, cause, sighting, lastSeen, clanmate);
    }

    DeathBroadcast withClanmate(boolean clanmate)
    {
        return clanmate ? new DeathBroadcast(player, levelText, level, cause, sighting, lastSeen, true) : this;
    }

    static DeathBroadcast preview(String name, int level)
    {
        return new DeathBroadcast(name, Integer.toString(level), level, "Goblin (12 damage)", null, false, false);
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
        String cause = named ? match.group(1).trim() + " (" + damage + " damage)" : damage + " damage hit";
        int level = display.endsWith("k")
            ? new BigDecimal(display.substring(0, display.length() - 1)).movePointRight(3).intValueExact()
            : Integer.parseInt(display.replace(",", ""));
        return player.isEmpty() || level <= 0 || level > 2376
            ? null : new DeathBroadcast(player, display, level, cause, null, false, false);
    }

    static String plainText(String message)
    {
        String plain = TAGS.matcher(message.replace("<br>", " ")).replaceAll("").replace('\u00a0', ' ');
        return COLOR_CODES.matcher(plain).replaceAll("").trim().replaceAll("\\s+", " ");
    }
}
