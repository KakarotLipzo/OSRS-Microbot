package net.runelite.client.plugins.microbot.quinnmain.bot.tasks;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

import net.runelite.client.plugins.microbot.quinnmain.bot.core.TaskContext;


/**
 * Base type for a module that trains one specific {@link Sk}. The {@link
 * com.quinn.osrs.main.core.GoalEngine} owns a registry of these and picks one by weighted
 * random among the skills that are below target and allowed for the account type.
 *
 * <p>Concrete trainers (Woodcutting, Mining, melee combat, …) arrive in Phase 1+. Each decides
 * for itself, via {@link #isDoable(TaskContext)}, whether it can run right now for this account
 * (e.g. an F2P-only method being available, or required items being obtainable).
 */
public abstract class SkillTask {

    /** The skill this module trains. */
    public abstract Sk skill();

    /** Can this trainer actually run right now (account type, level, method availability)? */
    public abstract boolean isDoable(TaskContext ctx);

    /** Do one slice of training. @return milliseconds to sleep before the next loop. */
    public abstract int execute(TaskContext ctx);

    public String name() { return skill().name() + " trainer"; }
}
