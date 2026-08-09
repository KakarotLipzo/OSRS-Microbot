package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.combat;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

import net.runelite.client.plugins.microbot.quinnmain.bot.core.TaskContext;

import java.util.EnumMap;
import java.util.Map;

/**
 * Temporarily benches a monster the bot walked to but couldn't find.
 *
 * <p>Every {@link MonsterType} anchor is a best-effort walk target, and some are estimates from wiki
 * directions rather than confirmed tiles. Without this, a wrong anchor is a hang: the engine picks
 * the monster, walks to the anchor, finds nothing, walks again, forever — with no XP and no log that
 * says why.
 *
 * <p>So arriving at an anchor and finding none of the target benches it for {@link #BENCH_MS}, and
 * the engine drops to the next tier down (ultimately COW, which is verified). It's logged once per
 * benching, which turns a silent hang into a line naming the bad anchor.
 */
public final class MonsterBench {

    /** Long enough to stop thrashing, short enough that a busy world recovers on its own. */
    private static final long BENCH_MS = 10 * 60 * 1000L;
    /** How close we must be to the anchor before "none here" counts as evidence. */
    private static final int ARRIVED_TILES = 12;

    private final Map<MonsterType, Long> benchedUntil = new EnumMap<>(MonsterType.class);

    public boolean isBenched(MonsterType m) {
        Long until = benchedUntil.get(m);
        return until != null && System.currentTimeMillis() < until;
    }

    /**
     * Record that we're at {@code m}'s anchor with none in sight.
     *
     * @param distanceToAnchor how far we currently are from the anchor; benching only counts once
     *                         we've actually arrived, so a long walk isn't mistaken for an empty spot.
     * @return true if the monster was just benched.
     */
    public boolean noneFoundAt(TaskContext ctx, MonsterType m, double distanceToAnchor) {
        if (distanceToAnchor > ARRIVED_TILES || isBenched(m)) return false;
        benchedUntil.put(m, System.currentTimeMillis() + BENCH_MS);
        ctx.log("[combat] no " + m.label() + " found at its spot " + m.anchor
                + " — benching it for " + (BENCH_MS / 60_000) + " min and dropping a tier."
                + " If this repeats, the anchor is wrong.");
        return true;
    }

    /** Bench {@code m} now for the standard duration (e.g. we couldn't descend to its dungeon). */
    public void benchNow(MonsterType m) {
        benchedUntil.put(m, System.currentTimeMillis() + BENCH_MS);
    }

    // Pursuit timer: how long we've been inside a dungeon trying to REACH a monster (past the entrance),
    // for deep targets like Flesh Crawlers where the maze between floors can stall the web-walk. Distinct
    // from DungeonNav's surface→inside give-up, which clears the moment we're inside the entrance floor.
    private final Map<MonsterType, Long> pursuitSince = new EnumMap<>(MonsterType.class);

    /** True once we've been pursuing {@code m} for {@code limitMs} without engaging — time to bench it. */
    public boolean pursuitExpired(MonsterType m, long limitMs) {
        long now = System.currentTimeMillis();
        Long s = pursuitSince.get(m);
        if (s == null) { pursuitSince.put(m, now); return false; }
        return now - s > limitMs;
    }

    /** Reset the pursuit clock (call when we actually engage the monster). */
    public void clearPursuit(MonsterType m) { pursuitSince.remove(m); }

    public void clear(MonsterType m) { benchedUntil.remove(m); pursuitSince.remove(m); }
}
