package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.crafting;

import net.runelite.client.plugins.microbot.quinnmain.bot.core.MakeInterface;
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
 * Crafting trainer (processing) — F2P method chosen by {@code crafting.method} (AUTO default): gold
 * jewellery at a furnace, leather sewing, or gem cutting. Ported to the {@link GameApi} facade; the
 * craft menu is driven via {@link MakeInterface} (matched by product name).
 */
public class CraftingTask extends SkillTask {

    private static final int COINS_ID = 995;
    private static final Pos GE_TILE = new Pos(3164, 3486, 0);
    private static final long NO_MAT_COOLDOWN_MS = 10 * 60 * 1000L;

    private long noMaterialUntil = 0;

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

    @Override public Sk skill() { return Sk.CRAFTING; }
    @Override public String name() { return "Crafting"; }

    @Override
    public boolean isDoable(TaskContext ctx) {
        if (System.currentTimeMillis() < noMaterialUntil) return false;
        CraftJob job = chooseJob(ctx);
        if (job == null) return false;
        if (ctx.bank.hasSeenBank() && !ctx.bank.has(job.primaryId) && !g().invContains(job.primaryId)
                && !ctx.config.isGeBuySupplies()) return false;
        return true;
    }

    @Override
    public int execute(TaskContext ctx) {
        GameApi a = g(); if (a == null) return 600;
        CraftJob job = chooseJob(ctx);
        if (job == null) { ctx.log("[craft] no " + ctx.config.getCraftingMethod() + " job available at this level."); return 2000; }
        boolean haveTool = a.invContains(job.toolId);
        boolean haveConsumable = job.consumableId == 0 || a.invContains(job.consumableId);
        boolean havePrimary = a.invContains(job.primaryId);
        if (haveTool && haveConsumable && havePrimary) return make(ctx, job);
        return restock(ctx, job);
    }

    private CraftJob chooseJob(TaskContext ctx) {
        String cm = ctx.config.getCraftingMethod();
        boolean auto = cm == null || cm.trim().isEmpty() || cm.equalsIgnoreCase("AUTO");
        CraftMethod pin = auto ? null : CraftMethod.parse(cm);
        int lvl = ctx.account.level(Sk.CRAFTING);
        CraftJob best = null, bestSourceable = null;
        for (CraftJob j : CraftJob.values()) {
            if (j.craftLevel > lvl) continue;
            if (pin != null) {
                if (j.method != pin) continue;
                if (best == null || j.craftLevel > best.craftLevel) best = j;
                continue;
            }
            if (best == null || j.xpRank > best.xpRank) best = j;
            if (canSource(ctx, j) && (bestSourceable == null || j.xpRank > bestSourceable.xpRank)) bestSourceable = j;
        }
        return bestSourceable != null ? bestSourceable : best;
    }

    private boolean canSource(TaskContext ctx, CraftJob j) {
        if (ctx.bank.has(j.primaryId) || g().invContains(j.primaryId)) return true;
        return ctx.config.isGeBuySupplies() && affordable(ctx, j.primaryId, j.primaryFallbackPrice, 28 - j.reservedSlots());
    }

    // ── making ─────────────────────────────────────────────────────────────────────────────────
    private int make(TaskContext ctx, CraftJob job) {
        GameApi a = g();
        if (a.isAnimating()) return smallSleep();
        boolean opened;
        if (job.method.needsFurnace) {
            GameObj furnace = findFurnace();
            if (furnace == null) { ctx.log("[craft] no furnace nearby; walking to " + job.method.bankNow() + " furnace."); Nav.walkTo(job.method.furnaceNow()); return 600; }
            if (furnace.distance() > 6) { Nav.walkTo(furnace.position()); return 600; }
            opened = furnace.useItem(job.primaryId);
        } else {
            opened = a.useItemOnItem(job.toolId, job.primaryId);
        }
        if (opened) {
            ctx.log("[craft] making " + job.productName + ".");
            a.waitUntil(() -> a.isAnimating() || MakeInterface.isOpen(job.productName), 3000);
            if (!a.isAnimating()) {
                String act = MakeInterface.click(job.productName);
                if (act != null) ctx.log("[craft] selected " + job.productName + " (" + act + ").");
                else ctx.log("[craft] craft button not found — " + MakeInterface.describe());
                a.waitUntil(a::isAnimating, 2000);
            }
            a.waitUntil(() -> !a.invContains(job.primaryId) || (!a.isAnimating() && !a.dialogueOpen()), 120000);
        }
        return smallSleep();
    }

    private GameObj findFurnace() { return closestObj(o -> o.name() != null && o.name().toLowerCase().contains("furnace"), 8); }

    // ── restock ──────────────────────────────────────────────────────────────────────────────
    private int restock(TaskContext ctx, CraftJob job) {
        GameApi a = g();
        if (!a.bankIsOpen()) { if (!Nav.openBank(job.method.bankNow())) return 600; return 400; }
        a.depositInventory();
        a.waitUntil(() -> a.invEmptySlots() >= 28, 2000);

        int t = obtain(ctx, job.toolId, nameOf(job.toolId), 1);
        if (t != 0) return t < 0 ? pause(ctx, "tool") : t;
        if (job.consumableId != 0) {
            int c = obtain(ctx, job.consumableId, nameOf(job.consumableId), 27);
            if (c != 0) return c < 0 ? pause(ctx, "thread") : c;
        }
        int primaryQty = 28 - job.reservedSlots();
        int pr = obtainPrimary(ctx, job, primaryQty);
        if (pr != 0) return pr < 0 ? pause(ctx, job.primaryName) : pr;

        a.closeBank();
        return 800;
    }

    private int obtain(TaskContext ctx, int id, String name, int qty) {
        GameApi a = g();
        if (a.invContains(id)) return 0;
        if (a.bankContains(id)) { a.withdraw(id, qty); a.waitUntil(() -> a.invContains(id), 1500); return 0; }
        if (ctx.config.isGeBuySupplies() && affordable(ctx, id, cheapFallback(id), qty)) { a.closeBank(); return geBuy(ctx, name, id, cheapFallback(id), qty); }
        return -1;
    }

    private int obtainPrimary(TaskContext ctx, CraftJob job, int qty) {
        GameApi a = g();
        if (a.invContains(job.primaryId)) return 0;
        if (a.bankContains(job.primaryId)) { a.withdraw(job.primaryId, qty); a.waitUntil(() -> a.invContains(job.primaryId), 1500); return 0; }
        if (ctx.config.isGeBuySupplies() && affordable(ctx, job.primaryId, job.primaryFallbackPrice, qty)) { a.closeBank(); return geBuy(ctx, job.primaryName, job.primaryId, job.primaryFallbackPrice, qty); }
        return -1;
    }

    private int pause(TaskContext ctx, String what) {
        noMaterialUntil = System.currentTimeMillis() + NO_MAT_COOLDOWN_MS;
        ctx.log("[craft] no " + what + " and can't buy it — pausing Crafting for ~10 min.");
        GameApi a = g(); if (a.bankIsOpen()) a.closeBank();
        return 3000;
    }

    // ── GE ─────────────────────────────────────────────────────────────────────────────────────
    private int geBuy(TaskContext ctx, String name, int id, int fallbackPrice, int qty) {
        GameApi a = g();
        int shop = SupplyBuy.tryStore(ctx, id, qty);
        if (shop != SupplyBuy.NO_STORE) return shop;
        int offer = offer(id, fallbackPrice), cost = offer * qty;
        Pos me = a.playerPosition();
        if (me == null || me.distance(GE_TILE) > 8) {
            if (a.geOpen()) a.geClose();
            ctx.log("[craft] travelling to GE to buy " + qty + "x " + name + ".");
            Nav.walkTo(GE_TILE); return 600;
        }
        if (a.invCount(COINS_ID) < cost) {
            if (a.geOpen()) a.geClose();
            if (!a.bankIsOpen()) { if (!Nav.openBank(null)) return 600; return 400; }
            ctx.bank.maybeSnapshot();
            int need = cost - a.invCount(COINS_ID);
            if (a.bankCount(COINS_ID) < need) { ctx.log("[craft] can't afford " + qty + "x " + name + "; skipping."); a.closeBank(); return 1500; }
            a.withdraw(COINS_ID, need);
            a.waitUntil(() -> a.invCount(COINS_ID) >= cost, 1500);
            a.closeBank(); return 500;
        }
        if (!a.geOpen()) { a.openGe(); a.waitUntil(a::geOpen, 3000); return 1000; }
        boolean ok = a.geBuy(id, qty, offer);
        ctx.log("[craft] GE buy " + qty + "x " + name + " @" + offer + "gp ok=" + ok);
        a.sleep(3000); a.geCollectAll(); a.waitUntil(() -> a.invContains(id), 4000);
        a.geClose();
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
    private int cheapFallback(int id) {
        switch (id) { case 1592: return 10; case 1733: return 5; case 1734: return 5; case 1755: return 5; default: return 50; }
    }
    private String nameOf(int id) {
        switch (id) { case 1592: return "Ring mould"; case 1733: return "Needle"; case 1734: return "Thread"; case 1755: return "Chisel"; default: return "item " + id; }
    }
    private int smallSleep() { return 480 + (int) (Math.random() * 420); }
}
