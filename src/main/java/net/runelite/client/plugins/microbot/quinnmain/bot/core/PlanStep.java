package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

/**
 * One entry in the ordered {@link PlanEngine} queue (design §2.3). A step names a thing to do — a
 * skill (optionally pinned to one activity), a quest, or a money maker — and the {@link Cond}ition
 * that ends it. Steps are stored in {@link ConfigStore} and run top to bottom before the level goals.
 *
 * <p>{@code elapsedMs} accumulates the active time spent on a TIME step so "for 2h" survives a
 * restart; it's ignored for the other conditions.
 */
public class PlanStep {

    public enum Kind { SKILL, QUEST, MONEY }

    /** How the step ends: reach a LEVEL, gain a QTY, run for TIME (minutes), or quest DONE. */
    public enum Cond { LEVEL, QTY, TIME, DONE }

    public final int id;
    public Kind kind;
    /** Sk name / quest key / money-method key (matches the registry the engine resolves against). */
    public String name;
    /** Sk steps only: the activity key to pin (null = let the trainer pick from enabled activities). */
    public String activity;
    public Cond cond;
    /** LEVEL 1-99 · QTY count · TIME minutes · null for DONE. */
    public int value;
    /** Accumulated active ms for a TIME step (persisted so the timer survives a restart). */
    public long elapsedMs;

    public PlanStep(int id, Kind kind, String name, String activity, Cond cond, int value) {
        this.id = id;
        this.kind = kind;
        this.name = name;
        this.activity = activity;
        this.cond = cond;
        this.value = value;
    }
}
