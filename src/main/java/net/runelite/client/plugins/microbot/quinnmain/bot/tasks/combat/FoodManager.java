package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.combat;

import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.Nav;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.PriceLookup;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.TaskContext;
import net.runelite.client.plugins.microbot.quinnmain.game.Game;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;

/**
 * Food/eating shared by all combat methods. Eats the best carried food when hurt, and keeps food
 * supplied: bank → HP-appropriate GE buy (essential minimum bypasses the reserve) → fish+cook shrimps.
 * Ported to the {@link GameApi} facade.
 */
public class FoodManager {

    private static final int COINS_ID = 995;
    private static final Pos GE_TILE = new Pos(3164, 3486, 0);
    public static final int EAT_AT_PERCENT = 50;
    private static final int WITHDRAW_QTY = 15, BUY_TARGET_QTY = 100, BUY_MIN_QTY = 5;
    private static final int LOW_FOOD = 3, STOCK_TARGET = 15;
    private static final long REAL_CHECK_TTL_MS = 120_000L;

    private final FoodProvisioner provisioner = new FoodProvisioner();
    private boolean stocking = false;
    private long lastRealBankCheck = 0;

    private static GameApi g() { return Game.api(); }

    public boolean hasFood(TaskContext ctx) { return bestInInventory(ctx) != null; }

    public int totalFood(TaskContext ctx) {
        GameApi a = g(); int c = 0;
        for (Food f : foodCandidates(ctx)) c += a.invCount(f.id);
        return c;
    }

    public int keepStocked(TaskContext ctx, BankLoc bank) {
        int have = totalFood(ctx);
        if (!stocking && have >= LOW_FOOD) return 0;
        if (have >= STOCK_TARGET) { stocking = false; return 0; }
        stocking = true;
        int r = stockStep(ctx, bank);
        if (r < 0) { stocking = false; return 0; }
        return r;
    }

    public int handleEat(TaskContext ctx) {
        GameApi a = g(); if (a == null) return 0;
        if (a.healthPercent() > ctx.config.getEatAtPercent()) return 0;
        Food f = bestInInventory(ctx);
        if (f == null) return 0;
        if (a.invInteract(f.id, "Eat")) {
            ctx.log("[food] eating " + f.name + " at " + a.healthPercent() + "% hp.");
            a.waitUntil(() -> a.healthPercent() > ctx.config.getEatAtPercent(), 1500);
            return 700;
        }
        return 0;
    }

    private int stockStep(TaskContext ctx, BankLoc bank) {
        GameApi a = g();
        boolean live = totalFood(ctx) == 0 && System.currentTimeMillis() - lastRealBankCheck > REAL_CHECK_TTL_MS;
        if (bankHasFood(ctx) || live) {
            if (!a.bankIsOpen()) { if (!Nav.openBank(bank)) return 600; return 400; }
            ctx.bank.maybeSnapshot();
            Food banked = bestInBank(ctx);
            if (banked != null) {
                int qty = ctx.config.getFoodQty(banked.name(), WITHDRAW_QTY);
                a.withdraw(banked.id, qty);
                a.waitUntil(() -> a.invContains(banked.id), 2000);
                ctx.log("[food] withdrew " + qty + "x " + banked.name + " for combat.");
                a.closeBank();
                return 800;
            }
            lastRealBankCheck = System.currentTimeMillis();
            a.closeBank();
        }
        if (ctx.config.isGeBuySupplies()) {
            Food buy = pickGeFood(ctx);
            if (buy != null) { int r = geBuy(ctx, buy); if (r >= 0) return r; }
        }
        return provisioner.provision(ctx);
    }

    private boolean bankHasFood(TaskContext ctx) {
        for (Food f : foodCandidates(ctx)) if (ctx.bank.has(f.id)) return true;
        return false;
    }

    // ── selection ────────────────────────────────────────────────────────────────────────────
    private java.util.List<Food> foodCandidates(TaskContext ctx) {
        java.util.List<Food> enabled = new java.util.ArrayList<>();
        for (Food f : Food.values()) if (ctx.config.isFoodEnabled(f.name())) enabled.add(f);
        return enabled.isEmpty() ? java.util.Arrays.asList(Food.values()) : enabled;
    }

    private Food bestInInventory(TaskContext ctx) {
        GameApi a = g(); Food best = null;
        for (Food f : foodCandidates(ctx)) { if (!a.invContains(f.id)) continue; if (best == null || f.heal > best.heal) best = f; }
        return best;
    }
    private Food bestInBank(TaskContext ctx) {
        GameApi a = g(); Food best = null;
        for (Food f : foodCandidates(ctx)) { if (!a.bankContains(f.id)) continue; if (best == null || f.heal > best.heal) best = f; }
        return best;
    }

    private Food pickGeFood(TaskContext ctx) {
        int maxHp;
        try { maxHp = g().skillLevelReal("HITPOINTS"); } catch (Throwable e) { maxHp = 10; }
        Food best = null;
        for (Food f : foodCandidates(ctx)) {
            if (f.heal > maxHp) continue;
            if (!canAffordEssential(ctx, f, BUY_MIN_QTY)) continue;
            if (best == null || f.heal > best.heal) best = f;
        }
        if (best != null) return best;
        for (Food f : foodCandidates(ctx)) if (canAffordEssential(ctx, f, BUY_MIN_QTY)) return f;
        return null;
    }

    // ── GE ─────────────────────────────────────────────────────────────────────────────────────
    private int geBuy(TaskContext ctx, Food food) {
        GameApi a = g();
        int offer = unitOffer(food);
        if (offer <= 0) return -1;
        int total = totalCoins(ctx);
        int qty = Math.min(BUY_TARGET_QTY, Math.max(0, total - ctx.config.getGoldReserve()) / offer);
        boolean essential = false;
        if (qty < BUY_MIN_QTY) {
            if (total / offer >= BUY_MIN_QTY) { qty = BUY_MIN_QTY; essential = true; }
            else { ctx.log("[food] can't afford " + food.name + " at GE (have " + total + "gp); will fish+cook instead."); return -1; }
        }
        final int cost = qty * offer;
        Pos me = a.playerPosition();
        if (me == null || me.distance(GE_TILE) > 8) {
            if (a.geOpen()) a.geClose();
            ctx.log("[food] travelling to GE to buy " + food.name + ".");
            Nav.walkTo(GE_TILE); return 600;
        }
        if (a.invCount(COINS_ID) < cost) {
            if (a.geOpen()) a.geClose();
            if (!a.bankIsOpen()) { if (!Nav.openBank(BankLoc.GRAND_EXCHANGE)) return 600; return 400; }
            ctx.bank.maybeSnapshot();
            int need = cost - a.invCount(COINS_ID);
            if (a.bankCount(COINS_ID) >= need) { a.withdraw(COINS_ID, need); a.waitUntil(() -> a.invCount(COINS_ID) >= cost, 1500); ctx.log("[food] withdrew " + need + "gp to buy " + qty + "x " + food.name + "."); }
            a.closeBank();
            return 500;
        }
        if (!a.geOpen()) { a.openGe(); a.waitUntil(a::geOpen, 3000); return 1000; }
        boolean ok = a.geBuy(food.id, qty, offer);
        ctx.log("[food] GE buy " + qty + "x " + food.name + " @" + offer + "gp ok=" + ok + (essential ? " (essential)" : ""));
        a.sleep(3000); a.geCollectAll(); a.waitUntil(() -> a.invContains(food.id), 4000);
        a.geClose();
        return 1000;
    }

    private int unitOffer(Food food) { int live = PriceLookup.high(food.id); int base = live > 0 ? live : food.gePrice; return (int) Math.ceil(base * 1.05) + 1; }
    private int totalCoins(TaskContext ctx) {
        int c = 0;
        try { c += g().invCount(COINS_ID); } catch (Throwable ignored) { }
        try { if (ctx.bank != null) c += ctx.bank.count(COINS_ID); } catch (Throwable ignored) { }
        return c;
    }
    private boolean canAffordEssential(TaskContext ctx, Food food, int qty) { return totalCoins(ctx) - (unitOffer(food) * qty) >= 0; }
}
