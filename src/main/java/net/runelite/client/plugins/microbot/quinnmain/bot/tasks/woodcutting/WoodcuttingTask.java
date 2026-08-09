package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.woodcutting;

import net.runelite.client.plugins.microbot.quinnmain.bot.core.Nav;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.PriceLookup;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.SupplyBuy;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.TaskContext;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.SkillTask;
import net.runelite.client.plugins.microbot.quinnmain.game.Game;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi.GameObj;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.game.Sk;

import java.util.function.Predicate;

/**
 * Woodcutting trainer — auto tree progression (Normal→Oak→Willow F2P), best usable axe
 * (wield/bank-upgrade/GE-buy), bank when full. Ported from OSRS-Main to the {@link GameApi} facade
 * (same shape as {@link net.runelite.client.plugins.microbot.quinnmain.bot.tasks.mining.MiningTask}).
 */
public class WoodcuttingTask extends SkillTask {

    private static final int COINS_ID = 995;
    private static final Pos GE_TILE = new Pos(3164, 3486, 0);

    private static GameApi g() { return Game.api(); }

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

    @Override public Sk skill() { return Sk.WOODCUTTING; }
    @Override public String name() { return "Woodcutting"; }
    @Override public boolean isDoable(TaskContext ctx) { return true; }

    @Override
    public int execute(TaskContext ctx) {
        GameApi a = g(); if (a == null) return 600;
        TreeType tree = chooseTree(ctx);
        if (tree == null) { ctx.log("[wc] no tree type available for this account/level."); return 2000; }
        if (a.invIsFull()) return doBank(ctx, tree);
        int axeStep = ensureAxe(ctx);
        if (axeStep > 0) return axeStep;
        return chop(ctx, tree);
    }

    // ── tree selection ─────────────────────────────────────────────────────────────────────────
    private TreeType chooseTree(TaskContext ctx) {
        int wc = ctx.account.level(Sk.WOODCUTTING);
        boolean members = ctx.account.isMembers();
        TreeType best = null;
        for (TreeType t : TreeType.values()) {
            if (t.wcLevel > wc) continue;
            if (t.members && !members) continue;
            if (!ctx.config.isActivityEnabled(Sk.WOODCUTTING, t.name())) continue;
            if (enabledSafeAreaOrNull(ctx, t) == null) continue;
            if (best == null || t.xpRank > best.xpRank) best = t;
        }
        return best;
    }

    private WoodcuttingArea activeArea(TaskContext ctx, TreeType tree) {
        WoodcuttingArea a = enabledSafeAreaOrNull(ctx, tree);
        if (a != null) return a;
        a = enabledAreaOrNull(ctx, tree);
        return a != null ? a : tree.areas[0];
    }
    private WoodcuttingArea enabledAreaOrNull(TaskContext ctx, TreeType tree) {
        for (WoodcuttingArea a : tree.areas) if (ctx.config.isAreaEnabled(Sk.WOODCUTTING, tree.name(), a.name())) return a;
        return null;
    }
    private WoodcuttingArea enabledSafeAreaOrNull(TaskContext ctx, TreeType tree) {
        for (WoodcuttingArea a : tree.areas)
            if (a.usableNow() && ctx.config.isAreaEnabled(Sk.WOODCUTTING, tree.name(), a.name())) return a;
        return null;
    }

    // ── chopping ───────────────────────────────────────────────────────────────────────────────
    private int chop(TaskContext ctx, TreeType tree) {
        GameApi a = g();
        if (a.isAnimating()) return smallSleep();
        GameObj t = findTree(tree);
        if (t == null) {
            WoodcuttingArea area = activeArea(ctx, tree);
            ctx.log("[wc] no " + tree.objectName + " nearby; walking to the " + area.label + " spot.");
            Nav.walkTo(area.anchor);
            return 600;
        }
        if (t.distance() > 6) { Nav.walkTo(t.position()); return 600; }
        if (t.interact("Chop down")) {
            ctx.log("[wc] chopping " + t.name() + "#" + t.id());
            a.waitUntil(() -> a.isAnimating() || a.invIsFull(), 4000);
        }
        return smallSleep();
    }

    private GameObj findTree(TreeType tree) {
        return closestObj(o -> o.hasAction("Chop down") && tree.matches(o.name()), 14);
    }

    // ── banking ────────────────────────────────────────────────────────────────────────────────
    private int doBank(TaskContext ctx, TreeType tree) {
        GameApi a = g();
        if (!a.bankIsOpen()) { if (!Nav.openBank(null)) return 600; return 400; }
        AxeTier held = bestHeldUsable(ctx);
        if (held != null && a.invContains(held.itemId)) a.depositAllExcept(held.itemId);
        else a.depositInventory();
        a.waitUntil(() -> !a.invIsFull(), 3000);
        AxeTier bankBetter = bestUsableInBank(ctx);
        if (bankBetter != null && (held == null || bankBetter.ordinal() > held.ordinal())) {
            if (a.withdraw(bankBetter.itemId, 1)) {
                a.waitUntil(() -> a.invContains(bankBetter.itemId), 2000);
                ctx.log("[wc] upgraded axe from bank: " + bankBetter.itemName);
            }
        }
        a.closeBank();
        return 800;
    }

    // ── axe management ───────────────────────────────────────────────────────────────────────────
    private int ensureAxe(TaskContext ctx) {
        GameApi a = g();
        int attack = ctx.account.level(Sk.ATTACK);
        AxeTier held = bestHeldUsable(ctx);
        if (held != null) {
            if (held.wieldableAt(attack) && !a.isWearing(held.itemId) && a.invContains(held.itemId)) {
                if (a.invInteract(held.itemId, "Wield") || a.invInteract(held.itemId, "Wear") || a.invInteract(held.itemId, "Equip")) {
                    ctx.log("[wc] wielding " + held.itemName + "."); a.sleep(650); return 700;
                }
            }
            if (ctx.config.isGeBuyAxes()) {
                AxeTier better = bestAffordableUpgrade(ctx, held);
                if (better != null && !owns(better)) return geBuyAndEquip(ctx, better);
            }
            return 0;
        }
        if (a.bankIsOpen()) {
            if (a.invEmptySlots() < 28) { a.depositInventory(); a.waitUntil(() -> a.invEmptySlots() >= 28, 2000); }
            AxeTier bankAxe = bestUsableInBank(ctx);
            if (bankAxe != null) {
                if (a.withdraw(bankAxe.itemId, 1)) { ctx.log("[wc] withdrew " + bankAxe.itemName + " from bank."); a.waitUntil(() -> a.invContains(bankAxe.itemId), 2000); }
                return 800;
            }
            if (ctx.config.isGeBuyAxes()) {
                AxeTier buy = bestAffordable(ctx);
                if (buy != null) { a.closeBank(); return geBuyAndEquip(ctx, buy); }
            }
            ctx.log("[wc] no usable axe in bank and none affordable — cannot train right now.");
            return 5000;
        }
        ctx.log("[wc] no axe carried; opening nearest bank to fetch one.");
        if (!Nav.openBank(null)) return 600;
        return 400;
    }

    private int geBuyAndEquip(TaskContext ctx, AxeTier tier) {
        GameApi a = g();
        int shop = SupplyBuy.tryStore(ctx, tier.itemId, 1);
        if (shop != SupplyBuy.NO_STORE) return shop;
        int offer = buyOffer(tier), cost = offer;
        double dist = distanceTo(GE_TILE);
        if (dist > 8) {
            if (a.geOpen()) a.geClose();
            ctx.log("[wc] travelling to GE to buy " + tier.itemName + " (dist " + (int) dist + ").");
            Nav.walkTo(GE_TILE); return 600;
        }
        if (!a.invContains(tier.itemId) && a.invCount(COINS_ID) < cost) {
            if (a.geOpen()) a.geClose();
            if (!a.bankIsOpen()) { if (!Nav.openBank(null)) return 600; return 400; }
            ctx.bank.maybeSnapshot();
            int need = cost - a.invCount(COINS_ID);
            if (a.bankCount(COINS_ID) < need) {
                ctx.log("[wc] can't afford " + tier.itemName + " (offer " + offer + "gp); skipping.");
                a.closeBank(); return 1500;
            }
            a.withdraw(COINS_ID, need);
            a.waitUntil(() -> a.invCount(COINS_ID) >= cost, 1500);
            a.closeBank(); return 500;
        }
        if (!a.geOpen()) { a.openGe(); a.waitUntil(a::geOpen, 3000); return 1000; }
        if (!a.invContains(tier.itemId)) {
            boolean ok = a.geBuy(tier.itemId, 1, offer);
            ctx.log("[wc] GE buy " + tier.itemName + " @" + offer + "gp ok=" + ok);
            a.sleep(3000);
            a.geCollectAll();
            a.waitUntil(() -> a.invContains(tier.itemId), 4000);
        }
        a.geClose();
        return 1000;
    }

    private int buyOffer(AxeTier tier) {
        int live = PriceLookup.high(tier.itemId);
        int base = live > 0 ? live : tier.fallbackPrice;
        return (int) Math.ceil(base * 1.05) + 1;
    }

    private double distanceTo(Pos t) {
        GameApi a = g(); Pos me = a == null ? null : a.playerPosition();
        return (me == null || t == null) ? 999 : me.distance(t);
    }

    private AxeTier bestHeldUsable(TaskContext ctx) {
        int wc = ctx.account.level(Sk.WOODCUTTING); boolean m = ctx.account.isMembers();
        AxeTier[] v = AxeTier.values();
        for (int i = v.length - 1; i >= 0; i--) if (v[i].usableAt(wc, m) && owns(v[i])) return v[i];
        return null;
    }
    private AxeTier bestUsableInBank(TaskContext ctx) {
        int wc = ctx.account.level(Sk.WOODCUTTING); boolean m = ctx.account.isMembers();
        AxeTier[] v = AxeTier.values();
        for (int i = v.length - 1; i >= 0; i--) if (v[i].usableAt(wc, m) && g().bankContains(v[i].itemId)) return v[i];
        return null;
    }
    private AxeTier bestAffordable(TaskContext ctx) {
        int wc = ctx.account.level(Sk.WOODCUTTING); boolean m = ctx.account.isMembers();
        AxeTier[] v = AxeTier.values();
        for (int i = v.length - 1; i >= 0; i--) if (v[i].usableAt(wc, m) && affordable(ctx, v[i])) return v[i];
        return null;
    }
    private AxeTier bestAffordableUpgrade(TaskContext ctx, AxeTier held) {
        int wc = ctx.account.level(Sk.WOODCUTTING); boolean m = ctx.account.isMembers();
        AxeTier[] v = AxeTier.values();
        for (int i = v.length - 1; i >= 0; i--) {
            if (v[i].ordinal() <= held.ordinal()) break;
            if (v[i].usableAt(wc, m) && affordable(ctx, v[i])) return v[i];
        }
        return null;
    }
    private boolean owns(AxeTier ax) { GameApi a = g(); return a.isWearing(ax.itemId) || a.invContains(ax.itemId); }

    private int totalCoins(TaskContext ctx) {
        int c = 0;
        try { c += g().invCount(COINS_ID); } catch (Throwable ignored) { }
        try { if (ctx.bank != null) c += ctx.bank.count(COINS_ID); } catch (Throwable ignored) { }
        return c;
    }
    private boolean affordable(TaskContext ctx, AxeTier ax) { return totalCoins(ctx) - buyOffer(ax) >= ctx.config.getGoldReserve(); }

    private int smallSleep() { return 480 + (int) (Math.random() * 420); }
}
