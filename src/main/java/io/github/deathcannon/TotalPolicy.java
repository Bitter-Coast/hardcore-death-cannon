package io.github.deathcannon;

import java.math.BigDecimal;

/**
 * Inclusive minimum total level. Exact broadcast totals compare directly. An abbreviated total
 * such as {@code 1.2k} is decided only when every total it could stand for is on the same side
 * of the minimum, or once a Hiscores total consistent with the abbreviation is known.
 */
final class TotalPolicy
{
    static final int MAX_TOTAL = 2376;

    enum Decision
    {
        SHOW, HIDE, UNKNOWN
    }

    private TotalPolicy()
    {
    }

    static Decision decide(DeathBroadcast death, int minimum)
    {
        if (minimum <= 0)
        {
            return Decision.SHOW;
        }
        if (!death.abbreviated())
        {
            return death.level >= minimum ? Decision.SHOW : Decision.HIDE;
        }
        int[] range = abbreviatedRange(death.levelText);
        if (range == null)
        {
            return Decision.UNKNOWN;
        }
        int exact = consistentHiscoreTotal(death, range);
        if (exact > 0)
        {
            return exact >= minimum ? Decision.SHOW : Decision.HIDE;
        }
        if (range[0] >= minimum)
        {
            return Decision.SHOW;
        }
        if (range[1] < minimum)
        {
            return Decision.HIDE;
        }
        return Decision.UNKNOWN;
    }

    /** The total to print: the broadcast text, or the exact Hiscores total when it fits an abbreviation. */
    static String displayTotal(DeathBroadcast death)
    {
        if (death.abbreviated())
        {
            int exact = consistentHiscoreTotal(death, abbreviatedRange(death.levelText));
            if (exact > 0)
            {
                return Integer.toString(exact);
            }
        }
        return death.levelText;
    }

    /**
     * Hiscores can lag behind the broadcast or miss a player entirely, so their total is used
     * only when it falls inside the range the abbreviation allows.
     */
    private static int consistentHiscoreTotal(DeathBroadcast death, int[] range)
    {
        int exact = death.hiscoreTotal;
        return range != null && exact >= range[0] && exact <= range[1] ? exact : 0;
    }

    /**
     * Every total an abbreviation could represent. Jagex's rounding rule is not documented, so
     * the range covers both truncation and rounding to the nearest shown digit, capped at the
     * maximum total: {@code 1.2k} is 1150-1299 and {@code 1k} is 500-1999. Returns null for
     * unreadable text or a range that lies entirely above the maximum total.
     */
    static int[] abbreviatedRange(String text)
    {
        if (text == null || text.length() < 2 || !text.endsWith("k"))
        {
            return null;
        }
        String digits = text.substring(0, text.length() - 1);
        int dot = digits.indexOf('.');
        int decimals = dot < 0 ? 0 : digits.length() - dot - 1;
        if (decimals > 3)
        {
            return null;
        }
        try
        {
            int unit = (int) Math.pow(10, 3 - decimals);
            int base = new BigDecimal(digits).movePointRight(3).intValueExact();
            int low = Math.max(1, base - unit / 2);
            int high = Math.min(MAX_TOTAL, base + unit - 1);
            return low > high ? null : new int[] {low, high};
        }
        catch (NumberFormatException | ArithmeticException ex)
        {
            return null;
        }
    }
}
