package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.SkillTask;


import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The "brain". Given the account's live state and the per-skill config, decides which skill to
 * train next by <b>weighted random</b> among the skills that are:
 * <ul>
 *   <li>below their target level,</li>
 *   <li>allowed for the account type (F2P skips members-only skills), and</li>
 *   <li>backed by a registered {@link SkillTask} that can run right now.</li>
 * </ul>
 *
 * <p>Once a skill is chosen it is <b>sticky</b> — the engine keeps training it until it hits
 * target, becomes ineligible, or a <b>randomised continuous cap</b> (a fresh value in
 * {@code [skill.capMinutesMin, skill.capMinutesMax]} active minutes, rolled each time a skill is
 * picked) forces a switch to a different skill. A QoL override, when set, pins training to one skill.
 */
public class GoalEngine {

    /** Optional test hook: skip these skills in selection (empty = none). Keep empty for production. */
    private static final Set<Sk> TEST_DISABLED = EnumSet.noneOf(Sk.class);

    /** Optional test hook: hard-force one skill regardless of config/weights (null = off, normal play). */
    private static final Sk TEST_FORCE_SKILL = null;

    private final Map<Sk, SkillTask> tasks = new EnumMap<>(Sk.class);
    private final Random rng = new Random();

    private Sk current = null;
    private long currentActiveMs = 0;
    /** The randomised cap (ms) for the CURRENT skill session, re-rolled each time we switch skills. */
    private long currentCapMs = 0;
    private volatile boolean skipRequested = false;

    public void register(SkillTask task) {
        tasks.put(task.skill(), task);
    }

    /** UI hook: force the engine to re-roll to a different skill on the next {@link #select}. */
    public void requestSkip() { skipRequested = true; }

    /** Skills that actually have a registered trainer module (for the UI's force-skill picker). */
    public java.util.Set<Sk> registeredSkills() {
        return java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(tasks.keySet()));
    }

    public boolean hasAnyTasks() { return !tasks.isEmpty(); }

    public Sk current() { return current; }
    public long currentActiveMs() { return currentActiveMs; }

    /**
     * Let the {@link PlanEngine} drive which skill counts as "current" while it runs a skill step,
     * so the HUD, the flee-if-attacked guard, and the ETA all track the planned skill instead of the
     * last weighted pick. Passing null (for a quest/money step) clears it.
     */
    public void setCurrentExternal(Sk s) {
        if (current != s) { current = s; currentActiveMs = 0; currentCapMs = Long.MAX_VALUE; }
    }

    /** Accrue active playtime against the current skill (drives the 4h cap). */
    public void accrue(long deltaMs) {
        if (current != null) currentActiveMs += deltaMs;
    }

    /**
     * Choose the skill to train this loop (or null if nothing is eligible / no module exists).
     * Stable: returns the current skill unless it's finished, ineligible, or capped.
     */
    public Sk select(TaskContext ctx) {
        // Test hook: hard-force one skill (null in production, so this is skipped).
        if (TEST_FORCE_SKILL != null && tasks.containsKey(TEST_FORCE_SKILL)) {
            if (current != TEST_FORCE_SKILL) setCurrent(TEST_FORCE_SKILL, ctx);
            return TEST_FORCE_SKILL;
        }

        // QoL override pins training to one skill (if a module exists for it) — but a forced skill
        // still STOPS at its level goal. Once the target is reached the pin is released so the engine
        // resumes the weighted plan instead of grinding a skill that's already done.
        if (ctx.config.isQolOverride()) {
            Sk q = ctx.config.getQolSkill();
            if (q != null && tasks.containsKey(q)) {
                if (ctx.account.level(q) < ctx.config.getTarget(q)) {
                    if (current != q) setCurrent(q, ctx);
                    return q;
                }
                ctx.config.setQolOverride(false);
                ctx.config.setQolSkill(null);
                Log.log("[engine] forced " + q + " reached its goal (" + ctx.account.level(q)
                        + "/" + ctx.config.getTarget(q) + ") — releasing the pin, back to the weighted plan.");
            }
        }

        // UI "skip task": re-roll to a different eligible skill now (ignored if nothing else fits).
        if (skipRequested) {
            skipRequested = false;
            Sk other = roll(ctx, current);
            if (other != null) {
                setCurrent(other, ctx);
                Log.log("[engine] skip requested -> " + other);
                return other;
            }
            rerollCap(ctx); // couldn't switch; at least reset the timer with a fresh cap
        }

        boolean capped = current != null && currentActiveMs >= currentCapMs;
        if (!capped && current != null && isEligible(current, ctx)) {
            return current;
        }

        Sk chosen = roll(ctx, capped ? current : null);
        if (chosen != null) {
            if (chosen != current) setCurrent(chosen, ctx);
            return chosen;
        }
        // Nothing else eligible. If we were only switching because of the cap and the current
        // skill is still valid, roll a fresh cap and keep going rather than idling.
        if (capped && current != null && isEligible(current, ctx)) {
            rerollCap(ctx);
            return current;
        }
        return null;
    }

    public SkillTask taskFor(Sk s) { return tasks.get(s); }

    /**
     * A pick over ALL trainable skills ignoring whether a module exists — used only to LOG the
     * engine's intent while trainer modules don't exist yet, so the weighting is observable.
     */
    public Sk dryRunPick(TaskContext ctx) {
        List<Sk> pool = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        for (Sk s : SkillCatalog.TRAINABLE) {
            if (!ctx.account.isMembers() && SkillCatalog.isMembers(s)) continue;
            if (ctx.account.level(s) >= ctx.config.getTarget(s)) continue;
            pool.add(s);
            weights.add(Math.max(1, ctx.config.getWeight(s)));
        }
        return weightedPick(pool, weights);
    }

    // -- internals --

    private void setCurrent(Sk s, TaskContext ctx) {
        current = s;
        currentActiveMs = 0;
        rerollCap(ctx);
    }

    /** Roll a fresh continuous-training cap for the current skill session, in the configured range. */
    private void rerollCap(TaskContext ctx) {
        currentActiveMs = 0;
        int min = Math.max(1, ctx.config.getSkillCapMinutesMin());
        int max = Math.max(min, ctx.config.getSkillCapMinutesMax());
        int mins = min + (max > min ? rng.nextInt(max - min + 1) : 0);
        currentCapMs = mins * 60_000L;
        Log.log("[engine] " + (current != null ? current : "?") + " training cap this session = "
                + mins + " min (range " + min + "-" + max + ").");
    }

    /** The current skill session's randomised cap, in whole minutes (for the HUD/panel display). */
    public long currentCapMinutes() { return currentCapMs / 60_000L; }

    private boolean isEligible(Sk s, TaskContext ctx) {
        return ineligibleReason(s, ctx) == null;
    }

    private Sk roll(TaskContext ctx, Sk exclude) {
        List<Sk> pool = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        for (Sk s : tasks.keySet()) {
            if (s == exclude) { reasons.add(s + "=excluded"); continue; }
            String why = ineligibleReason(s, ctx);
            if (why != null) { reasons.add(s + "=" + why); continue; }
            pool.add(s);
            weights.add(Math.max(1, ctx.config.getWeight(s)));
        }
        Sk picked = weightedPick(pool, weights);
        Log.log("[engine] roll -> " + picked + " | eligible=" + pool + " | skipped=" + reasons);
        return picked;
    }

    /** @return null if eligible, else a short reason string (for the roll diagnostic). */
    private String ineligibleReason(Sk s, TaskContext ctx) {
        if (TEST_DISABLED.contains(s)) return "test-disabled";
        SkillTask t = tasks.get(s);
        if (t == null) return "no-task";
        if (ctx.config.getWeight(s) <= 0) return "weight-0";
        if (!ctx.account.isMembers() && SkillCatalog.isMembers(s)) return "members-only";
        int lvl = ctx.account.level(s);
        int tgt = ctx.config.getTarget(s);
        if (lvl >= tgt) return "at-target(" + lvl + "/" + tgt + ")";
        if (!t.isDoable(ctx)) return "not-doable";
        return null;
    }

    /** Effective per-skill cap in minutes (for the HUD, so display matches behaviour). */
    public long capMinutes(TaskContext ctx) { return currentCapMinutes(); }

    private Sk weightedPick(List<Sk> pool, List<Integer> weights) {
        if (pool.isEmpty()) return null;
        int total = 0;
        for (int w : weights) total += w;
        int r = rng.nextInt(total);
        for (int i = 0; i < pool.size(); i++) {
            r -= weights.get(i);
            if (r < 0) return pool.get(i);
        }
        return pool.get(pool.size() - 1);
    }
}
