package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.firemaking;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

/**
 * Logs we can burn, with the log item ID, the Firemaking level to light them, and an XP-rate rank
 * (≈ firemaking XP per log). The trainer burns the highest-ranked log it has banked that the level
 * allows — which naturally consumes the logs the Woodcutting trainer banks (normal → oak → willow…).
 *
 * <p>Firemaking is fully F2P (no members-only logs), so there's no members gate. Maple/yew/magic are
 * defined for completeness; the current F2P Woodcutting set only banks normal/oak/willow, so the
 * higher logs simply never appear in the bank until those trees are cut.
 */
public enum LogType {
    NORMAL ("Logs",        1511, 1,  40),
    OAK    ("Oak logs",    1521, 15, 60),
    WILLOW ("Willow logs", 1519, 30, 90),
    MAPLE  ("Maple logs",  1517, 45, 135),
    YEW    ("Yew logs",    1515, 60, 202),
    MAGIC  ("Magic logs",  1513, 75, 303);

    public final String logName;
    public final int logId;
    public final int fmLevel;
    public final int xpRank;

    LogType(String logName, int logId, int fmLevel, int xpRank) {
        this.logName = logName;
        this.logId = logId;
        this.fmLevel = fmLevel;
        this.xpRank = xpRank;
    }
}
