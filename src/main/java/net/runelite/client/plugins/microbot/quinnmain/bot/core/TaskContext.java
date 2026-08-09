package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.bot.antiban.BreakManager;
import net.runelite.client.plugins.microbot.quinnmain.bot.antiban.Humanizer;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

/**
 * The shared services bundle handed to every task each loop: config, playtime, account state, anti-ban,
 * and bank memory. Ported from OSRS-Main (Logger → {@link Log}).
 */
public class TaskContext {

    public final ConfigStore config;
    public final PlaytimeTracker playtime;
    public final AccountState account;
    public final Humanizer humanizer;
    public final BreakManager breaks;
    public final BankMemory bank;

    private static final long REPEAT_LOG_MS = 10_000L;
    private String lastMsg = null;
    private long lastMsgLoggedAt = 0L;
    private int suppressed = 0;

    public TaskContext(ConfigStore config, PlaytimeTracker playtime, AccountState account,
                       Humanizer humanizer, BreakManager breaks, BankMemory bank) {
        this.config = config;
        this.playtime = playtime;
        this.account = account;
        this.humanizer = humanizer;
        this.breaks = breaks;
        this.bank = bank;
    }

    /** Log, collapsing identical repeats to at most one line per {@link #REPEAT_LOG_MS}. */
    public void log(String msg) {
        long now = System.currentTimeMillis();
        if (msg != null && msg.equals(lastMsg)) {
            suppressed++;
            if (now - lastMsgLoggedAt < REPEAT_LOG_MS) return;
            Log.log(msg + " (x" + suppressed + ")");
            lastMsgLoggedAt = now; suppressed = 0;
            return;
        }
        lastMsg = msg; suppressed = 0; lastMsgLoggedAt = now;
        Log.log(msg);
    }
}
