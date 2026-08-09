package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.fishing;

import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.Nav;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.PriceLookup;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.SupplyBuy;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.TaskContext;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.SkillTask;
import net.runelite.client.plugins.microbot.quinnmain.game.Game;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi.Npc;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.game.Sk;

import java.util.function.Predicate;

/**
 * Fishing trainer — auto method progression (shrimp net → fly fishing F2P), gear (tool + bait)
 * provisioning, spot-commit fishing, bank/power-drop. Ported from OSRS-Main to the {@link GameApi}
 * facade (fishing spots are NPCs; tool is used on the spot).
 */
public class FishingTask extends SkillTask {

    private static final int COINS_ID = 995;
    private static final int BAIT_MIN = 25;
    private static final int BAIT_BUY_QTY = 1000;
    private static final Pos GE_TILE = new Pos(3164, 3486, 0);

    private static GameApi g() { return Game.api(); }

    private Npc closestNpc(Predicate<Npc> pred, int radius) {
        GameApi a = g(); if (a == null) return null;
        Npc best = null; double bd = Double.MAX_VALUE;
        for (Npc n : a.npcsWithin(radius)) {
            if (n == null) continue;
            try { if (!pred.test(n)) continue; } catch (Throwable t) { continue; }
            double d = n.distance();
            if (d < bd) { bd = d; best = n; }
        }
        return best;
    }

    @Override public Sk skill() { return Sk.FISHING; }
    @Override public String name() { return "Fishing"; }
    @Override public boolean isDoable(TaskContext ctx) { return true; }

    @Override
    public int execute(TaskContext ctx) {
        GameApi a = g(); if (a == null) return 600;
        FishMethod method = chooseMethod(ctx);
        if (method == null) { ctx.log("[fish] no method available for this account/level."); return 2000; }
        if (a.invIsFull()) return ctx.config.isFishingPowerDrop() ? dropFish(ctx, method) : doBank(ctx, method);
        int gear = acquireGear(ctx, method);
        if (gear == -1) {
            FishMethod fb = fallbackReady(ctx);
            if (fb != null && fb != method) { ctx.log("[fish] can't gear " + method + "; falling back to " + fb + "."); return fish(ctx, fb); }
            ctx.log("[fish] no gearable method right now; skipping.");
            return 4000;
        }
        if (gear > 0) return gear;
        return fish(ctx, method);
    }

    // ── method + area ──────────────────────────────────────────────────────────────────────────
    private FishMethod chooseMethod(TaskContext ctx) {
        int lvl = ctx.account.level(Sk.FISHING);
        boolean members = ctx.account.isMembers();
        FishMethod best = null;
        for (FishMethod m : FishMethod.values()) {
            if (m.fishLevel > lvl) continue;
            if (m.members && !members) continue;
            if (m.needsBoat && !ctx.config.isFishingKaramja()) continue;
            if (activeArea(ctx, m) == null) continue;
            if (!ctx.config.isActivityEnabled(Sk.FISHING, m.name())) continue;
            if (best == null || m.xpRank > best.xpRank) best = m;
        }
        return best;
    }

    private FishMethod fallbackReady(TaskContext ctx) {
        GameApi a = g();
        int lvl = ctx.account.level(Sk.FISHING);
        boolean members = ctx.account.isMembers();
        FishMethod best = null;
        for (FishMethod m : FishMethod.values()) {
            if (m.fishLevel > lvl || (m.members && !members)) continue;
            if (m.needsBoat && !ctx.config.isFishingKaramja()) continue;
            if (!a.invContains(m.toolId)) continue;
            if (m.needsBait() && a.invCount(m.baitId) <= 0) continue;
            if (activeArea(ctx, m) == null) continue;
            if (!ctx.config.isActivityEnabled(Sk.FISHING, m.name())) continue;
            if (best == null || m.xpRank > best.xpRank) best = m;
        }
        return best;
    }

    private FishingArea activeArea(TaskContext ctx, FishMethod method) {
        for (FishingArea a : method.areas)
            if (a.usableNow() && ctx.config.isAreaEnabled(Sk.FISHING, method.name(), a.name())) return a;
        return null;
    }
    private Pos anchorOf(TaskContext ctx, FishMethod method) { FishingArea a = activeArea(ctx, method); return (a != null ? a : method.areas[0]).anchor; }
    private BankLoc bankOf(TaskContext ctx, FishMethod method) { FishingArea a = activeArea(ctx, method); return (a != null ? a : method.areas[0]).bank; }

    // ── fishing ────────────────────────────────────────────────────────────────────────────────
    private long lastFishAction = 0;
    private Pos committedSpot = null;

    private int fish(TaskContext ctx, FishMethod method) {
        GameApi a = g();
        if (a.isAnimating()) return 1200 + (int) (Math.random() * 900);
        if (a.isMoving()) return smallSleep();
        if (System.currentTimeMillis() - lastFishAction < 2500) return smallSleep();

        Npc spot = pickSpot(method);
        if (spot == null || spot.distance() > 12) {
            committedSpot = null;
            maybeDiagnose(ctx, method, spot);
            Nav.walkTo(anchorOf(ctx, method));
            return 600;
        }
        boolean started = spot.interact(method.action);
        if (!started) started = spot.useItem(method.toolId);
        if (started) {
            committedSpot = spot.position();
            lastFishAction = System.currentTimeMillis();
            ctx.log("[fish] " + method.action + " at " + spot.name() + "#" + spot.id());
            a.waitUntil(() -> a.isAnimating() || a.invIsFull(), 4000);
        } else {
            maybeDiagnose(ctx, method, spot);
        }
        return smallSleep();
    }

    private Npc pickSpot(FishMethod method) {
        if (committedSpot != null) {
            Npc same = closestNpc(n -> n.name() != null && n.name().toLowerCase().contains("fishing spot")
                    && committedSpot.equals(n.position()), 20);
            if (same != null) return same;
        }
        return findSpot(method);
    }

    private Npc findSpot(FishMethod method) {
        if (method.spotNpcId > 0) {
            Npc byId = closestNpc(n -> n.id() == method.spotNpcId, 20);
            if (byId != null) return byId;
        }
        Npc exact = closestNpc(n -> n.name() != null && n.name().toLowerCase().contains("fishing spot") && n.hasAction(method.action), 20);
        if (exact != null) return exact;
        return closestNpc(n -> n.name() != null && n.name().toLowerCase().contains("fishing spot"), 20);
    }

    private long lastGearLog = 0;
    private long lastDiag = 0;
    private void maybeDiagnose(TaskContext ctx, FishMethod method, Npc spot) {
        long now = System.currentTimeMillis();
        if (now - lastDiag < 5000) return;
        lastDiag = now;
        GameApi a = g();
        Pos me = a.playerPosition();
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Npc npc : a.npcsWithin(15)) {
            if (npc == null || npc.name() == null || !npc.name().toLowerCase().contains("fishing spot")) continue;
            sb.append(npc.name()).append("#").append(npc.id()).append("@").append((int) npc.distance()).append("  ");
            if (++n >= 6) break;
        }
        ctx.log("[fish] can't " + method.action + " yet: at " + me + " dist-to-anchor="
                + (int) (me == null ? -1 : me.distance(anchorOf(ctx, method)))
                + (spot != null ? " (spot @" + (int) spot.distance() + ")" : "")
                + "; loaded fishing spots: " + (n == 0 ? "NONE" : sb.toString()));
    }

    // ── full inventory ─────────────────────────────────────────────────────────────────────────
    private int dropFish(TaskContext ctx, FishMethod method) {
        GameApi a = g();
        if (method.needsBait()) a.invDropAllExcept(method.toolId, method.baitId);
        else a.invDropAllExcept(method.toolId);
        ctx.log("[fish] power-dropping fish (max XP).");
        a.waitUntil(() -> !a.invIsFull(), 3000);
        return 700;
    }

    private int doBank(TaskContext ctx, FishMethod method) {
        GameApi a = g();
        if (!a.bankIsOpen()) { if (!Nav.openBank(bankOf(ctx, method))) return 600; return 400; }
        if (method.needsBait()) a.depositAllExcept(method.toolId, method.baitId);
        else a.depositAllExcept(method.toolId);
        a.waitUntil(() -> !a.invIsFull(), 3000);
        if (method.needsBait() && a.invCount(method.baitId) < BAIT_MIN && a.bankContains(method.baitId)) {
            a.withdrawAll(method.baitId);
            a.waitUntil(() -> a.invCount(method.baitId) >= BAIT_MIN || !a.bankContains(method.baitId), 2000);
        }
        a.closeBank();
        return 800;
    }

    // ── gear acquisition ───────────────────────────────────────────────────────────────────────
    private int acquireGear(TaskContext ctx, FishMethod method) {
        GameApi a = g();
        boolean needTool = !a.invContains(method.toolId);
        boolean needBait = method.needsBait() && a.invCount(method.baitId) < BAIT_MIN;
        if (!needTool && !needBait) return 0;

        if (!a.bankIsOpen()) {
            long now = System.currentTimeMillis();
            if (now - lastGearLog > 5000) {
                lastGearLog = now;
                ctx.log("[fish] need " + (needTool ? "tool " : "") + (needBait ? "bait" : "") + "; visiting " + bankOf(ctx, method) + ".");
            }
            if (!Nav.openBank(bankOf(ctx, method))) return 600;
            return 400;
        }
        if (method.needsBait()) a.depositAllExcept(method.toolId, method.baitId);
        else a.depositAllExcept(method.toolId);

        if (needTool) {
            if (a.bankContains(method.toolId)) {
                a.withdraw(method.toolId, 1);
                a.waitUntil(() -> a.invContains(method.toolId), 2000);
                return 700;
            }
            if (ctx.config.isGeBuySupplies() && affordable(ctx, method.toolId, 1)) { a.closeBank(); return geBuy(ctx, method.toolId, 1); }
            return -1;
        }
        if (a.bankContains(method.baitId)) {
            a.withdrawAll(method.baitId);
            a.waitUntil(() -> a.invCount(method.baitId) >= BAIT_MIN || !a.bankContains(method.baitId), 2000);
        }
        if (a.invCount(method.baitId) > 0) return 700;
        if (ctx.config.isGeBuySupplies() && affordable(ctx, method.baitId, BAIT_BUY_QTY)) { a.closeBank(); return geBuy(ctx, method.baitId, BAIT_BUY_QTY); }
        return -1;
    }

    private int geBuy(TaskContext ctx, int itemId, int qty) {
        GameApi a = g();
        int shop = SupplyBuy.tryStore(ctx, itemId, qty);
        if (shop != SupplyBuy.NO_STORE) return shop;
        int offer = unitOffer(itemId), cost = offer * qty;
        Pos me = a.playerPosition();
        if (me == null || me.distance(GE_TILE) > 8) {
            if (a.geOpen()) a.geClose();
            ctx.log("[fish] travelling to GE to buy " + qty + "x item " + itemId + ".");
            Nav.walkTo(GE_TILE); return 600;
        }
        if (a.invCount(COINS_ID) < cost) {
            if (a.geOpen()) a.geClose();
            if (!a.bankIsOpen()) { if (!Nav.openBank(null)) return 600; return 400; }
            ctx.bank.maybeSnapshot();
            int need = cost - a.invCount(COINS_ID);
            if (a.bankCount(COINS_ID) < need) { ctx.log("[fish] can't afford " + qty + "x item " + itemId + "; skipping."); a.closeBank(); return 1500; }
            a.withdraw(COINS_ID, need);
            a.waitUntil(() -> a.invCount(COINS_ID) >= cost, 1500);
            a.closeBank(); return 500;
        }
        if (!a.geOpen()) { a.openGe(); a.waitUntil(a::geOpen, 3000); return 1000; }
        boolean ok = a.geBuy(itemId, qty, offer);
        ctx.log("[fish] GE buy " + qty + "x item " + itemId + " @" + offer + "gp ok=" + ok);
        a.sleep(3000);
        a.geCollectAll();
        a.waitUntil(() -> a.invContains(itemId), 4000);
        a.geClose();
        return 1000;
    }

    private int unitOffer(int itemId) { int live = PriceLookup.high(itemId); int base = live > 0 ? live : staticUnit(itemId); return (int) Math.ceil(base * 1.05) + 1; }
    private int staticUnit(int itemId) {
        switch (itemId) {
            case 314: return 10; case 313: return 20; case 303: return 200; case 309: return 500; case 307: return 200; default: return 200;
        }
    }
    private int totalCoins(TaskContext ctx) {
        int c = 0;
        try { c += g().invCount(COINS_ID); } catch (Throwable ignored) { }
        try { if (ctx.bank != null) c += ctx.bank.count(COINS_ID); } catch (Throwable ignored) { }
        return c;
    }
    private boolean affordable(TaskContext ctx, int itemId, int qty) { return totalCoins(ctx) - (unitOffer(itemId) * qty) >= ctx.config.getGoldReserve(); }
    private int smallSleep() { return 480 + (int) (Math.random() * 420); }
}
