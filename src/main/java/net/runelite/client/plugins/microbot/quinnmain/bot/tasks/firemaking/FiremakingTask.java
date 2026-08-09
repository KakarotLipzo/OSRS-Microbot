package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.firemaking;

import net.runelite.client.plugins.microbot.quinnmain.bot.core.Nav;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.PriceLookup;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.TaskContext;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.SkillTask;
import net.runelite.client.plugins.microbot.quinnmain.game.Game;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi.GameObj;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.game.Sk;

import java.util.function.Predicate;

/**
 * Firemaking trainer — burns the logs Woodcutting banks (no object: light logs on the ground in a lane).
 * Ported to the {@link GameApi} facade. Highest-XP log the level allows, withdraw tinderbox + a batch,
 * walk to a lane, light (game auto-walks west burning the batch), rebank; GE-buys logs if the bank is dry.
 */
public class FiremakingTask extends SkillTask {

    private static final int TINDERBOX = 590;
    private static final int COINS_ID = 995;
    private static final Pos GE_TILE = new Pos(3164, 3486, 0);
    private static final long NO_LOGS_COOLDOWN_MS = 10 * 60 * 1000L;
    private static final int LOG_BUY_QTY = 27;

    private long noLogsUntil = 0;
    private boolean headingToLane = false;

    private static GameApi g() { return Game.api(); }
    private FiremakingArea area(TaskContext ctx) { return FiremakingArea.parse(ctx.config.getFiremakingArea()); }

    private GameObj closestObj(Predicate<GameObj> pred, int radius) {
        GameApi a = g(); if (a == null) return null;
        GameObj best = null; double bd = Double.MAX_VALUE;
        for (GameObj o : a.objectsWithin(radius)) {
            if (o == null) continue;
            try { if (!pred.test(o)) continue; } catch (Throwable t) { continue; }
            double d = o.distance();
            if (d < bd) { bd = d; best = o; }
        }
        return best;
    }

    @Override public Sk skill() { return Sk.FIREMAKING; }
    @Override public String name() { return "Firemaking"; }

    @Override
    public boolean isDoable(TaskContext ctx) {
        if (System.currentTimeMillis() < noLogsUntil) return false;
        if (ctx.bank.hasSeenBank() && !hasBurnableLogsAnywhere(ctx)) return false;
        return true;
    }

    private boolean hasBurnableLogsAnywhere(TaskContext ctx) {
        int lvl = ctx.account.level(Sk.FIREMAKING);
        for (LogType t : LogType.values()) {
            if (t.fmLevel > lvl) continue;
            if (ctx.bank.has(t.logId) || g().invContains(t.logId)) return true;
        }
        return false;
    }

    @Override
    public int execute(TaskContext ctx) {
        GameApi a = g(); if (a == null) return 600;
        if (bestLogInInventory(ctx) != null) {
            if (!a.invContains(TINDERBOX)) return restock(ctx);
            if (headingToLane && !Nav.arrived(area(ctx).anchor, 5)) { Nav.walkTo(area(ctx).anchor); return 500; }
            headingToLane = false;
            return burn(ctx);
        }
        return restock(ctx);
    }

    private int burn(TaskContext ctx) {
        GameApi a = g();
        if (a.isAnimating() || a.isMoving()) return smallSleep();
        LogType log = bestLogInInventory(ctx);
        if (log == null) return 600;

        Pos me = a.playerPosition();
        if (fireAt(me)) { hopOffFire(me); return 400; }

        if (!a.invContains(TINDERBOX) || !a.invContains(log.logId)) return 600;
        final int before = a.invCount(log.logId);
        if (a.useItemOnItem(TINDERBOX, log.logId)) {
            ctx.log("[fire] lighting " + log.logName + " (" + before + " left).");
            a.waitUntil(() -> a.isAnimating() || a.invCount(log.logId) < before, 3000);
        }
        return smallSleep();
    }

    private boolean fireAt(Pos t) {
        if (t == null) return false;
        return closestObj(o -> "Fire".equals(o.name()) && o.position() != null && o.position().equals(t), 3) != null;
    }

    private void hopOffFire(Pos me) {
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        GameApi a = g();
        for (int[] d : dirs) {
            Pos t = new Pos(me.getX() + d[0], me.getY() + d[1], me.getZ());
            if (!fireAt(t)) { a.walkTo(t); a.waitUntil(() -> !a.isMoving(), 2000); return; }
        }
    }

    private int restock(TaskContext ctx) {
        GameApi a = g();
        if (!a.bankIsOpen()) { if (!Nav.openBank(area(ctx).bank)) return 600; return 400; }

        if (!a.bankContains(TINDERBOX) && !a.invContains(TINDERBOX)) {
            noLogsUntil = System.currentTimeMillis() + NO_LOGS_COOLDOWN_MS;
            ctx.log("[fire] no tinderbox — can't firemake; pausing ~10 min.");
            a.closeBank(); return 3000;
        }

        LogType log = bestLogAvailable(ctx);
        if (log == null) {
            LogType buy = ctx.config.isGeBuySupplies() ? bestAffordableLog(ctx) : null;
            if (buy != null) {
                a.depositInventory();
                a.waitUntil(() -> a.invEmptySlots() >= 28, 2000);
                if (!a.invContains(TINDERBOX)) { a.withdraw(TINDERBOX, 1); a.waitUntil(() -> a.invContains(TINDERBOX), 1500); }
                a.closeBank();
                return geBuyLogs(ctx, buy);
            }
            noLogsUntil = System.currentTimeMillis() + NO_LOGS_COOLDOWN_MS;
            ctx.log("[fire] no burnable logs and can't buy any — pausing ~10 min.");
            a.closeBank(); return 3000;
        }

        a.depositInventory();
        a.waitUntil(() -> a.invEmptySlots() >= 28, 2000);
        if (!a.invContains(TINDERBOX)) { a.withdraw(TINDERBOX, 1); a.waitUntil(() -> a.invContains(TINDERBOX), 1500); }
        a.withdrawAll(log.logId);
        a.waitUntil(() -> a.invContains(log.logId), 2000);
        ctx.log("[fire] withdrew " + log.logName + " to burn.");
        a.closeBank();
        headingToLane = true;
        return 800;
    }

    private LogType bestAffordableLog(TaskContext ctx) {
        int lvl = ctx.account.level(Sk.FIREMAKING);
        LogType best = null;
        for (LogType t : LogType.values()) {
            if (t.fmLevel > lvl) continue;
            if (!ctx.config.isActivityEnabled(Sk.FIREMAKING, t.name())) continue;
            if (!affordable(ctx, t.logId, logFallback(t), LOG_BUY_QTY)) continue;
            if (best == null || t.xpRank > best.xpRank) best = t;
        }
        return best;
    }

    private int geBuyLogs(TaskContext ctx, LogType log) {
        GameApi a = g();
        int offer = offer(log.logId, logFallback(log)), cost = offer * LOG_BUY_QTY;
        Pos me = a.playerPosition();
        if (me == null || me.distance(GE_TILE) > 8) {
            if (a.geOpen()) a.geClose();
            ctx.log("[fire] travelling to GE to buy " + LOG_BUY_QTY + "x " + log.logName + ".");
            Nav.walkTo(GE_TILE); return 600;
        }
        if (a.invCount(COINS_ID) < cost) {
            if (a.geOpen()) a.geClose();
            if (!a.bankIsOpen()) { if (!Nav.openBank(null)) return 600; return 400; }
            ctx.bank.maybeSnapshot();
            int need = cost - a.invCount(COINS_ID);
            if (a.bankCount(COINS_ID) < need) {
                ctx.log("[fire] can't afford " + log.logName + " within reserve; pausing.");
                noLogsUntil = System.currentTimeMillis() + NO_LOGS_COOLDOWN_MS;
                a.closeBank(); return 1500;
            }
            a.withdraw(COINS_ID, need);
            a.waitUntil(() -> a.invCount(COINS_ID) >= cost, 1500);
            a.closeBank(); return 500;
        }
        if (!a.geOpen()) { a.openGe(); a.waitUntil(a::geOpen, 3000); return 1000; }
        boolean ok = a.geBuy(log.logId, LOG_BUY_QTY, offer);
        ctx.log("[fire] GE buy " + LOG_BUY_QTY + "x " + log.logName + " @" + offer + "gp ok=" + ok);
        a.sleep(3000);
        a.geCollectAll();
        a.waitUntil(() -> a.invContains(log.logId), 4000);
        a.geClose();
        if (a.invContains(log.logId)) headingToLane = true;
        return 1000;
    }

    private int offer(int id, int fallbackPrice) { int live = PriceLookup.high(id); int base = live > 0 ? live : fallbackPrice; return (int) Math.ceil(base * 1.05) + 1; }
    private int totalCoins(TaskContext ctx) {
        int c = 0;
        try { c += g().invCount(COINS_ID); } catch (Throwable ignored) { }
        try { if (ctx.bank != null) c += ctx.bank.count(COINS_ID); } catch (Throwable ignored) { }
        return c;
    }
    private boolean affordable(TaskContext ctx, int id, int fallbackPrice, int qty) { return totalCoins(ctx) - (offer(id, fallbackPrice) * qty) >= ctx.config.getGoldReserve(); }
    private int logFallback(LogType t) {
        switch (t) { case NORMAL: return 60; case OAK: return 40; case WILLOW: return 15; case MAPLE: return 25; case YEW: return 300; case MAGIC: return 1000; default: return 100; }
    }

    private LogType bestLogInInventory(TaskContext ctx) {
        GameApi a = g();
        int lvl = ctx.account.level(Sk.FIREMAKING);
        LogType best = null;
        for (LogType t : LogType.values()) {
            if (t.fmLevel > lvl) continue;
            if (!a.invContains(t.logId)) continue;
            if (!ctx.config.isActivityEnabled(Sk.FIREMAKING, t.name())) continue;
            if (best == null || t.xpRank > best.xpRank) best = t;
        }
        return best;
    }
    private LogType bestLogAvailable(TaskContext ctx) {
        GameApi a = g();
        int lvl = ctx.account.level(Sk.FIREMAKING);
        LogType best = null;
        for (LogType t : LogType.values()) {
            if (t.fmLevel > lvl) continue;
            if (!a.bankContains(t.logId) && !a.invContains(t.logId)) continue;
            if (!ctx.config.isActivityEnabled(Sk.FIREMAKING, t.name())) continue;
            if (best == null || t.xpRank > best.xpRank) best = t;
        }
        return best;
    }

    private int smallSleep() { return 480 + (int) (Math.random() * 420); }
}
