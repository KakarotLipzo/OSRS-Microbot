package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

/**
 * A self-contained unit of high-level work (tutorial, or a post-tutorial activity router).
 * The main loop runs the first Task whose {@link #accept(TaskContext)} returns true.
 *
 * <p>Every method receives the shared {@link TaskContext} so tasks reach the engine's
 * services (config, playtime, account state, anti-ban) without global statics.
 */
public interface Task {

    /** Short human-readable name, for logging/paint. */
    String name();

    /** @return true if this task should run right now, given current game state. */
    boolean accept(TaskContext ctx);

    /** Do one slice of work. @return milliseconds to sleep before the next loop. */
    int execute(TaskContext ctx);
}
