package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.smithing;

import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.MakeInterface;
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
 * Smithing trainer — smelt ore→bars (Edgeville furnace) then anvil bars→items (any anvil near a bank),
 * with an opt-in GE buy-bars path. Ported from OSRS-Main to the {@link GameApi} facade. The shared make
 * screen is driven via {@link MakeInterface}.
 */
public class SmithingTask extends SkillTask {

    private static final BankLoc EDGEVILLE_BANK = BankLoc.EDGEVILLE;
    private static final Pos EDGEVILLE_FURNACE = new Pos(3108, 3499, 0);
    private static BankLoc smeltBankNow() { return EDGEVILLE_BANK; }
    private static Pos furnaceNow() { return EDGEVILLE_FURNACE; }

    private static final int HAMMER = 2347;
    private static final int COINS_ID = 995;
    private static final Pos GE_TILE = new Pos(3164, 3486, 0);
    private static final long NO_WORK_COOLDOWN_MS = 10 * 60 * 1000L;
    private static final int BAR_BUY_QTY = 26;

    private long noWorkUntil = 0;

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
    private static int inv(int id) { GameApi a = g(); return a == null ? 0 : a.invCount(id); }

    @Override public Sk skill() { return Sk.SMITHING; }
    @Override public String name() { return "Smithing"; }

    @Override
    public boolean isDoable(TaskContext ctx) {
        if (System.currentTimeMillis() < noWorkUntil) return false;
        if (ctx.bank.hasSeenBank() && !hasWorkAnywhere(ctx)) return false;
        return true;
    }

    private boolean hasWorkAnywhere(TaskContext ctx) { return hasOreAnywhere(ctx) || hasBarsAnywhere(ctx); }
    private boolean hasOreAnywhere(TaskContext ctx) {
        int lvl = ctx.account.level(Sk.SMITHING);
        for (BarType b : BarType.values()) {
            if (b.smithLevel > lvl) continue;
            boolean haveAll = true;
            for (int oreId : b.oreIds) if (!ctx.bank.has(oreId) && !g().invContains(oreId)) { haveAll = false; break; }
            if (haveAll) return true;
        }
        return false;
    }
    private boolean hasBarsAnywhere(TaskContext ctx) {
        int lvl = ctx.account.level(Sk.SMITHING);
        for (AnvilProduct p : AnvilProduct.values()) {
            if (p.smithLevel > lvl) continue;
            if (ctx.bank.count(p.barItemId) + inv(p.barItemId) >= p.barsPerItem) return true;
        }
        return false;
    }

    @Override
    public int execute(TaskContext ctx) {
        GameApi a = g(); if (a == null) return 600;
        BarType invBar = barFromInventory(ctx);
        if (invBar != null) return smelt(ctx, invBar);
        AnvilProduct invProduct = productFromInventory(ctx);
        if (invProduct != null) return anvilSmith(ctx, invProduct);
        if (ctx.config.isSmithingBuyBars() && !hasOreAnywhere(ctx) && !hasBarsAnywhere(ctx)) {
            int b = buyBarsFlow(ctx);
            if (b != 0) return b;
        }
        return restock(ctx);
    }

    // ── buy-bars ─────────────────────────────────────────────────────────────────────────────
    private AnvilProduct bestBuyableProduct(TaskContext ctx) {
        int lvl = ctx.account.level(Sk.SMITHING);
        AnvilProduct best = null;
        for (AnvilProduct p : AnvilProduct.values()) {
            if (p.smithLevel > lvl) continue;
            int batch = BAR_BUY_QTY - (BAR_BUY_QTY % p.barsPerItem);
            if (batch < p.barsPerItem) continue;
            if (!affordable(ctx, p.barItemId, barFallback(p.barItemId), batch)) continue;
            if (best == null || p.rank() > best.rank()) best = p;
        }
        return best;
    }

    private int buyBarsFlow(TaskContext ctx) {
        GameApi a = g();
        AnvilProduct product = bestBuyableProduct(ctx);
        if (product == null) return 0;
        int qty = BAR_BUY_QTY - (BAR_BUY_QTY % product.barsPerItem);
        boolean needHammer = !a.invContains(HAMMER);
        boolean needBars = a.invCount(product.barItemId) < qty;
        if (!needHammer && !needBars) return 0;
        int barUnit = barOffer(product.barItemId);
        int cost = (needBars ? barUnit * qty : 0) + (needHammer ? hammerOffer() : 0);

        if (a.invCount(COINS_ID) < cost) {
            if (a.geOpen()) a.geClose();
            if (!a.bankIsOpen()) { if (!Nav.openBank(null)) return 600; return 400; }
            ctx.bank.maybeSnapshot();
            if (needHammer && a.bankContains(HAMMER)) { a.withdraw(HAMMER, 1); a.waitUntil(() -> a.invContains(HAMMER), 1500); }
            int need = cost - a.invCount(COINS_ID);
            if (a.bankCount(COINS_ID) < need) {
                noWorkUntil = System.currentTimeMillis() + NO_WORK_COOLDOWN_MS;
                ctx.log("[smith] buy-bars: can't afford " + qty + "x " + barName(product.barItemId) + " bar — pausing ~10 min.");
                a.closeBank(); return 3000;
            }
            a.withdraw(COINS_ID, need);
            a.waitUntil(() -> a.invCount(COINS_ID) >= cost, 1500);
            a.closeBank(); return 500;
        }
        Pos me = a.playerPosition();
        if (me == null || me.distance(GE_TILE) > 8) {
            if (a.geOpen()) a.geClose();
            ctx.log("[smith] buy-bars: travelling to the GE for " + qty + "x " + barName(product.barItemId) + " bar.");
            Nav.walkTo(GE_TILE); return 600;
        }
        if (!a.geOpen()) { a.openGe(); a.waitUntil(a::geOpen, 3000); return 1000; }
        if (needHammer && !a.invContains(HAMMER)) {
            boolean ok = a.geBuy(HAMMER, 1, hammerOffer());
            ctx.log("[smith] buy-bars: GE buy hammer ok=" + ok);
            a.sleep(2500); a.geCollectAll(); a.waitUntil(() -> a.invContains(HAMMER), 3000);
            return 900;
        }
        boolean ok = a.geBuy(product.barItemId, qty, barUnit);
        ctx.log("[smith] buy-bars: GE buy " + qty + "x " + barName(product.barItemId) + " bar @" + barUnit + "gp ok=" + ok);
        a.sleep(3000); a.geCollectAll(); a.waitUntil(() -> a.invCount(product.barItemId) >= qty, 4000);
        a.geClose();
        return 1000;
    }

    private int barOffer(int barId) { int live = PriceLookup.high(barId); int base = live > 0 ? live : barFallback(barId); return (int) Math.ceil(base * 1.05) + 1; }
    private String barName(int barId) {
        switch (barId) { case 2349: return "Bronze"; case 2351: return "Iron"; case 2353: return "Steel"; case 2359: return "Mithril"; case 2361: return "Adamant"; case 2363: return "Rune"; default: return "#" + barId; }
    }
    private int barFallback(int barId) {
        switch (barId) { case 2349: return 90; case 2351: return 110; case 2353: return 400; case 2359: return 900; case 2361: return 1900; case 2363: return 12000; default: return 500; }
    }
    private int totalCoins(TaskContext ctx) {
        int c = 0;
        try { c += g().invCount(COINS_ID); } catch (Throwable ignored) { }
        try { if (ctx.bank != null) c += ctx.bank.count(COINS_ID); } catch (Throwable ignored) { }
        return c;
    }
    private boolean affordable(TaskContext ctx, int id, int fallbackPrice, int qty) { return totalCoins(ctx) - (barOffer(id) * qty) >= ctx.config.getGoldReserve(); }

    // ── smelting ─────────────────────────────────────────────────────────────────────────────
    private int smelt(TaskContext ctx, BarType bar) {
        GameApi a = g();
        if (a.isAnimating()) return smallSleep();
        GameObj furnace = findFurnace();
        if (furnace == null) { ctx.log("[smith] no furnace nearby; walking to " + smeltBankNow() + " furnace spot."); Nav.walkTo(furnaceNow()); return 600; }
        if (furnace.distance() > 6) { Nav.walkTo(furnaceNow()); return 600; }
        boolean opened = furnace.interact("Smelt");
        if (!opened) opened = furnace.useItem(bar.oreIds[0]);
        if (opened) {
            ctx.log("[smith] smelting " + bar.barName + " at " + furnace.name() + "#" + furnace.id());
            a.waitUntil(() -> a.isAnimating() || MakeInterface.isOpen(), 3000);
            if (!a.isAnimating()) {
                String action = MakeInterface.click(bar.barName);
                if (action != null) ctx.log("[smith] selected " + bar.barName + " in smelt interface.");
                else ctx.log("[smith] smelt button not found — " + MakeInterface.describe());
                a.waitUntil(a::isAnimating, 2000);
            }
            awaitBatch(ctx, bar.oreIds[0], bar.oreCounts[0]);
        }
        return smallSleep();
    }

    private void awaitBatch(TaskContext ctx, int materialId, int perItem) {
        GameApi a = g();
        final long IDLE_MS = 4500, HARD_CAP_MS = 180_000;
        long start = System.currentTimeMillis(), lastChange = start;
        int last = a.invCount(materialId);
        while (System.currentTimeMillis() - lastChange < IDLE_MS) {
            if (System.currentTimeMillis() - start > HARD_CAP_MS) { ctx.log("[smith] batch hit the cap — re-initiating."); return; }
            if (a.continueDialogue()) lastChange = System.currentTimeMillis();
            int now = a.invCount(materialId);
            if (now != last) { last = now; lastChange = System.currentTimeMillis(); }
            if (now < perItem) return;
            a.sleep(330);
        }
    }

    private GameObj findFurnace() { return closestObj(o -> o.name() != null && o.name().toLowerCase().contains("furnace"), 8); }

    // ── anvil ────────────────────────────────────────────────────────────────────────────────
    private static final class AnvilTarget {
        final Pos anchor; final BankLoc bank; final GameObj inReach;
        AnvilTarget(Pos anchor, BankLoc bank, GameObj inReach) { this.anchor = anchor; this.bank = bank; this.inReach = inReach; }
    }

    private AnvilTarget chooseAnvil() {
        GameApi a = g();
        GameObj anvil = findAnvil();
        if (anvil != null && anvil.distance() <= 10) return new AnvilTarget(anvil.position(), null, anvil); // null bank = nearest
        Pos me = a.playerPosition();
        AnvilSite best = null; double bestD = Double.MAX_VALUE;
        for (AnvilSite s : AnvilSite.values()) {
            if (!s.usableNow()) continue;
            double d = me == null ? 0 : me.distance(s.anchor);
            if (d < bestD) { bestD = d; best = s; }
        }
        if (best == null) best = AnvilSite.VARROCK_WEST;
        return new AnvilTarget(best.anchor, best.bank, null);
    }

    private int anvilSmith(TaskContext ctx, AnvilProduct product) {
        GameApi a = g();
        if (a.isAnimating()) return smallSleep();
        AnvilTarget target = chooseAnvil();
        GameObj anvil = target.inReach != null ? target.inReach : findAnvil();
        if (anvil == null || anvil.distance() > 6) { ctx.log("[smith] no anvil in reach; walking to an anvil near " + target.bank + "."); Nav.walkTo(target.anchor); return 600; }
        if (!a.invContains(HAMMER)) { ctx.log("[smith] no hammer carried — returning to the bank for one."); return restock(ctx); }

        boolean opened = anvil.interact("Smith");
        if (!opened) opened = anvil.useItem(product.barItemId);
        if (opened) {
            ctx.log("[smith] smithing " + product.productName + " at " + anvil.name() + "#" + anvil.id() + " @ " + anvil.position());
            a.waitUntil(() -> MakeInterface.isOpen(product.productName) || a.isAnimating(), 3000);
            if (MakeInterface.isOpen(product.productName)) {
                String action = MakeInterface.click(product.productName);
                if (action != null) ctx.log("[smith] selected " + product.productName + " in the anvil interface.");
                else ctx.log("[smith] anvil button not found — " + MakeInterface.describe());
                a.waitUntil(a::isAnimating, 2000);
            } else {
                ctx.log("[smith] anvil interface not found for '" + product.productName + "' — " + MakeInterface.describeVisible(12));
            }
            awaitBatch(ctx, product.barItemId, product.barsPerItem);
        }
        return smallSleep();
    }

    private GameObj findAnvil() { return closestObj(o -> o.name() != null && o.name().toLowerCase().contains("anvil"), 10); }

    // ── restock ──────────────────────────────────────────────────────────────────────────────
    private int restock(TaskContext ctx) {
        GameApi a = g();
        if (!a.bankIsOpen()) { if (!Nav.openBank(bankToVisit(ctx))) return 600; return 400; }
        BarType smeltBar = bestBarAvailable(ctx);
        if (smeltBar != null) return withdrawOre(ctx, smeltBar);
        AnvilProduct product = pickProduct(ctx, p -> a.bankCount(p.barItemId) >= p.barsPerItem);
        if (product != null) {
            if (a.bankContains(HAMMER) || a.invContains(HAMMER)) return withdrawBars(ctx, product);
            int buy = buyHammer(ctx);
            if (buy > 0) return buy;
            noWorkUntil = System.currentTimeMillis() + NO_WORK_COOLDOWN_MS;
            ctx.log("[smith] have bars but NO HAMMER and can't buy one — pausing ~10 min (put a hammer #2347 in the bank).");
            a.closeBank(); return 3000;
        }
        noWorkUntil = System.currentTimeMillis() + NO_WORK_COOLDOWN_MS;
        ctx.log("[smith] no ore or bars to work in bank — pausing Smithing for ~10 min.");
        a.closeBank(); return 3000;
    }

    private int buyHammer(TaskContext ctx) {
        GameApi a = g();
        if (!ctx.config.isGeBuySupplies()) return -1;
        int offer = hammerOffer();
        if (a.invCount(COINS_ID) < offer) return -1;
        if (!a.geOpen()) {
            if (a.bankIsOpen()) a.closeBank();
            Pos me = a.playerPosition();
            if (me == null || me.distance(GE_TILE) > 8) { ctx.log("[smith] no hammer — travelling to the GE to buy one."); Nav.walkTo(GE_TILE); return 600; }
            a.openGe(); a.waitUntil(a::geOpen, 3000); return 1000;
        }
        boolean ok = a.geBuy(HAMMER, 1, offer);
        ctx.log("[smith] GE buy 1x hammer @" + offer + "gp ok=" + ok);
        a.sleep(3000); a.geCollectAll(); a.waitUntil(() -> a.invContains(HAMMER), 4000);
        a.geClose();
        return 1000;
    }

    private int hammerOffer() { int live = PriceLookup.high(HAMMER); int base = live > 0 ? live : 60; return (int) Math.ceil(base * 1.1) + 5; }

    private BankLoc bankToVisit(TaskContext ctx) {
        if (!ctx.bank.hasSeenBank()) return smeltBankNow();
        if (hasOreAnywhere(ctx)) return smeltBankNow();
        if (hasBarsAnywhere(ctx)) return chooseAnvil().bank;
        return smeltBankNow();
    }

    private int withdrawOre(TaskContext ctx, BarType bar) {
        GameApi a = g();
        a.depositInventory(); a.waitUntil(() -> a.invEmptySlots() >= 28, 2000);
        int batches = 28 / bar.oresPerBar();
        for (int i = 0; i < bar.oreIds.length; i++) { a.withdraw(bar.oreIds[i], batches * bar.oreCounts[i]); a.sleep(350); }
        ctx.log("[smith] withdrew ore for " + bar.barName + " (" + batches + " bars).");
        a.closeBank(); return 800;
    }

    private int withdrawBars(TaskContext ctx, AnvilProduct product) {
        GameApi a = g();
        a.depositInventory(); a.waitUntil(() -> a.invEmptySlots() >= 28, 2000);
        if (!a.bankContains(HAMMER)) {
            noWorkUntil = System.currentTimeMillis() + NO_WORK_COOLDOWN_MS;
            ctx.log("[smith] no hammer in bank — can't anvil; pausing ~10 min.");
            a.closeBank(); return 3000;
        }
        a.withdraw(HAMMER, 1); a.waitUntil(() -> a.invContains(HAMMER), 1500);
        int slots = 27;
        int bars = (slots / product.barsPerItem) * product.barsPerItem;
        a.withdraw(product.barItemId, bars);
        a.waitUntil(() -> a.invCount(product.barItemId) >= product.barsPerItem, 2000);
        ctx.log("[smith] withdrew " + bars + "x bar to smith " + product.productName + ".");
        a.closeBank(); return 800;
    }

    // ── selection ────────────────────────────────────────────────────────────────────────────
    private BarType barFromInventory(TaskContext ctx) {
        int lvl = ctx.account.level(Sk.SMITHING);
        BarType best = null;
        for (BarType b : BarType.values()) {
            if (b.smithLevel > lvl) continue;
            if (!hasAllOres(b, false)) continue;
            if (!ctx.config.isActivityEnabled(Sk.SMITHING, b.name())) continue;
            if (best == null || b.xpRank > best.xpRank) best = b;
        }
        return best;
    }
    private BarType bestBarAvailable(TaskContext ctx) {
        int lvl = ctx.account.level(Sk.SMITHING);
        BarType best = null;
        for (BarType b : BarType.values()) {
            if (b.smithLevel > lvl) continue;
            if (!hasAllOres(b, true)) continue;
            if (!ctx.config.isActivityEnabled(Sk.SMITHING, b.name())) continue;
            if (best == null || b.xpRank > best.xpRank) best = b;
        }
        return best;
    }
    private boolean hasAllOres(BarType bar, boolean checkBank) {
        GameApi a = g();
        for (int oreId : bar.oreIds) { boolean have = checkBank ? a.bankContains(oreId) : a.invContains(oreId); if (!have) return false; }
        return true;
    }

    private AnvilProduct pickProduct(TaskContext ctx, Predicate<AnvilProduct> haveBars) {
        int lvl = ctx.account.level(Sk.SMITHING);
        String pinned = ctx.config.getAnvilShape();
        boolean auto = pinned == null || AnvilProduct.AUTO.equalsIgnoreCase(pinned);
        AnvilProduct best = null, bestPinned = null;
        for (AnvilProduct p : AnvilProduct.values()) {
            if (p.smithLevel > lvl) continue;
            if (!haveBars.test(p)) continue;
            if (best == null || p.rank() > best.rank()) best = p;
            if (!auto && p.shapeKey.equalsIgnoreCase(pinned) && (bestPinned == null || p.rank() > bestPinned.rank())) bestPinned = p;
        }
        return bestPinned != null ? bestPinned : best;
    }

    private AnvilProduct productFromInventory(TaskContext ctx) {
        if (!g().invContains(HAMMER)) return null;
        return pickProduct(ctx, p -> g().invCount(p.barItemId) >= p.barsPerItem);
    }

    private int smallSleep() { return 480 + (int) (Math.random() * 420); }
}
