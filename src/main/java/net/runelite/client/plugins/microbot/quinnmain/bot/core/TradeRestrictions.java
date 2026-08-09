package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * OSRS <b>new-account trade restrictions</b> for the Grand Exchange
 * (<a href="https://oldschool.runescape.wiki/w/Account#New_account_trade_restrictions">wiki</a>).
 *
 * <p>A new free-to-play account may not <b>sell</b> a fixed list of ~43 raw/bulk items on the GE until
 * it has met <b>all three</b> of: <b>20 hours</b> logged-in time, <b>≥10 quest points</b>, and
 * <b>100 total level</b> (the rule exists to keep botted/black-market goods out of the economy). This
 * guard stops the bot from <i>attempting</i> those sells before the account qualifies — otherwise the
 * game silently rejects the listing and the money method loops. <b>Buying is not restricted</b>, so
 * supply buys (tools, bait, feathers, food, bars, a bond) are left untouched.
 *
 * <p><b>Detecting eligibility.</b> Total level and quest points are read directly
 * ({@link AccountState#totalLevel()} / {@link AccountState#questPoints()}). The 20-hour figure has no
 * client getter, but reaching 100 total level in F2P takes far longer than 20 hours, so
 * "total ≥ 100 AND QP ≥ 10" is a safe proxy for all three conditions. Membership lifts the restriction
 * outright. When a signal can't be read we assume the account is <b>still restricted</b> (the safe
 * direction — hold the loot rather than fail a listing).
 */
public final class TradeRestrictions {

    private TradeRestrictions() { }

    /**
     * Item ids a new F2P account may not sell until the requirements are met. Covers the wiki list's
     * raw/bulk goods — every ore/log/hide/raw-fish/rune/consumable the bot could ever gather and try to
     * sell is here; processed goods (chocolate dust, pastry dough, pie shells, strung amulets, a bond)
     * are NOT on the list and stay sellable. A missed id is a graceful failure, not a loop: the sell
     * choke point still refuses on {@link #canSellOnGe} and, worst case, the game rejects it.
     */
    private static final Set<Integer> RESTRICTED_SELL = new HashSet<>(Arrays.asList(
            // Logs
            1521, 1519, 1515,                                  // oak, willow, yew
            // Food — raw + cooked
            317, 315, 321, 319, 377, 379,                      // shrimps, anchovies, lobster
            // Ores + coal
            436, 438, 440, 442, 444, 453, 447, 449, 451,       // copper, tin, iron, silver, gold, coal, mithril, adamantite, runite
            // Materials / consumables
            434, 1761, 592, 229, 227, 1937,                    // clay, soft clay, ashes, vial, vial of water, jug of water
            313, 314, 221, 245,                                // fishing bait, feather, eye of newt, wine of zamorak
            556, 555, 557, 554, 558, 562,                      // air, water, earth, fire, mind, chaos runes
            1739, 1741, 1743,                                  // cowhide, leather, hard leather
            223, 2353,                                         // red spiders' eggs, steel bar
            1436, 7936                                         // rune essence, pure essence
    ));

    /** Minutes of play the 20-hour leg requires. */
    private static final int MIN_PLAY_MINUTES = 20 * 60;

    /**
     * True once the account has met ALL of the new-account trade requirements (or is a member): 20h play
     * time + 100 total level + 10 QP. Time Played is now read for real ({@link AccountState#timePlayedMinutes()},
     * VarClientInt 526); when it isn't populated yet (0) we fall back to the old "total ≥ 100 &amp; QP ≥ 10"
     * proxy — imperfect (100 total is reachable in under 20h) but the best we can do without the varc.
     */
    public static boolean restrictionsLifted(AccountState account) {
        try {
            if (account == null) return false;
            if (account.isMembers()) return true;
            boolean levelsAndQp = account.totalLevel() >= 100 && account.questPoints() >= 10;
            int mins = account.timePlayedMinutes();
            if (mins > 0) return levelsAndQp && mins >= MIN_PLAY_MINUTES;   // real 3-part check
            return levelsAndQp;                                            // varc not populated → proxy
        } catch (Throwable e) {
            return false;   // unsure → treat as restricted
        }
    }

    /** True if {@code itemId} may be SOLD on the GE right now (not restricted, or requirements met). */
    public static boolean canSellOnGe(AccountState account, int itemId) {
        return !RESTRICTED_SELL.contains(itemId) || restrictionsLifted(account);
    }

    /** True if the item is on the new-account sell-restriction list at all (regardless of eligibility). */
    public static boolean isSellRestricted(int itemId) {
        return RESTRICTED_SELL.contains(itemId);
    }
}
