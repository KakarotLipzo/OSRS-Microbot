package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.runecraft;

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
 * Runecraft trainer (F2P) — auto rune progression (air→water→earth→fire→body), withdraw talisman +
 * pure essence (GE-buy if dry), walk to the ruins, enter, craft at the altar, exit portal, bank.
 * Ported to the {@link GameApi} facade. Ruins tiles are best-effort estimates (fail-safe diagnostics).
 */
public class RunecraftTask extends SkillTask {

    private static final int COINS_ID = 995;
    private static final Pos GE_TILE = new Pos(3164, 3486, 0);
    private static final int ESSENCE_BUY_QTY = 1000;
    private static final long NO_MAT_COOLDOWN_MS = 10 * 60 * 1000L;

    private long noMatUntil = 0;

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

    @Override public Sk skill() { return Sk.RUNECRAFTING; }
    @Override public String name() { return "Runecraft"; }

    @Override
    public boolean isDoable(TaskContext ctx) {
        if (System.currentTimeMillis() < noMatUntil) return false;
        return chooseMethod(ctx) != null;
    }

    @Override
    public int execute(TaskContext ctx) {
        GameApi a = g(); if (a == null) return 600;
        RuneMethod m = chooseMethod(ctx);
        if (m == null) { ctx.log("[rc] no rune method available at this level."); return 2000; }

        if (a.invCount(RuneMethod.PURE_ESSENCE) > 0) {
            GameObj altar = findAltar();
            if (altar != null) return craft(ctx, m, altar);
            GameObj ruins = findRuins();
            if (ruins != null && ruins.distance() <= 8) return enter(ctx, m, ruins);
            Pos me = a.playerPosition();
            if (me != null && me.distance(m.ruins) <= 6) diagnose(ctx, m);
            Nav.walkTo(m.ruins);
            return 700;
        }
        GameObj portal = findPortal();
        if (portal != null) return exit(ctx, portal);
        return restock(ctx, m);
    }

    private RuneMethod chooseMethod(TaskContext ctx) {
        int lvl = ctx.account.level(Sk.RUNECRAFTING);
        RuneMethod best = null;
        for (RuneMethod m : RuneMethod.values()) {
            if (m.rcLevel > lvl) continue;
            if (!ctx.config.isActivityEnabled(Sk.RUNECRAFTING, m.name())) continue;
            if (best == null || m.xpRank > best.xpRank) best = m;
        }
        return best;
    }

    // ── altar ────────────────────────────────────────────────────────────────────────────────
    private int craft(TaskContext ctx, RuneMethod m, GameObj altar) {
        GameApi a = g();
        if (a.isAnimating()) return smallSleep();
        if (altar.distance() > 2) { Nav.walkTo(altar.position()); return 500; }
        final int before = a.invCount(RuneMethod.PURE_ESSENCE);
        boolean ok = altar.useItem(RuneMethod.PURE_ESSENCE) || altar.interact("Craft-rune");
        if (ok) {
            ctx.log("[rc] crafting " + m.runeName + " (" + before + " essence).");
            a.waitUntil(() -> a.invCount(RuneMethod.PURE_ESSENCE) < before, 4000);
        }
        return smallSleep();
    }

    private int enter(TaskContext ctx, RuneMethod m, GameObj ruins) {
        GameApi a = g();
        if (ruins.distance() > 2) { Nav.walkTo(ruins.position()); return 500; }
        boolean ok = ruins.useItem(m.talismanId) || ruins.interact("Enter");
        if (ok) { ctx.log("[rc] entering the " + m.runeName + " altar."); a.waitUntil(() -> findAltar() != null, 4000); }
        return 700;
    }

    private int exit(TaskContext ctx, GameObj portal) {
        GameApi a = g();
        if (portal.distance() > 3) { Nav.walkTo(portal.position()); return 500; }
        boolean ok = portal.interact("Use") || portal.interact("Exit-through") || portal.interact("Enter");
        if (ok) { ctx.log("[rc] leaving the altar."); a.waitUntil(() -> findPortal() == null, 4000); }
        return 700;
    }

    private GameObj findAltar() { return closestObj(o -> o.name() != null && o.name().toLowerCase().contains("altar") && o.hasAction("Craft-rune"), 10); }
    private GameObj findRuins() { return closestObj(o -> o.name() != null && o.name().toLowerCase().contains("ruins"), 12); }
    private GameObj findPortal() { return closestObj(o -> o.name() != null && o.name().equalsIgnoreCase("Portal"), 10); }

    // ── restock ──────────────────────────────────────────────────────────────────────────────
    private int restock(TaskContext ctx, RuneMethod m) {
        GameApi a = g();
        if (!a.bankIsOpen()) { if (!Nav.openBank(m.bank)) return 600; return 400; }
        a.depositInventory();
        a.waitUntil(() -> a.invEmptySlots() >= 28, 2000);
        int t = obtain(ctx, m.talismanId, m.talismanName, 1, 500);
        if (t != 0) return t < 0 ? pause(ctx, "talisman") : t;
        int e = obtain(ctx, RuneMethod.PURE_ESSENCE, "Pure essence", 27, 5);
        if (e != 0) return e < 0 ? pause(ctx, "pure essence") : e;
        a.closeBank();
        return 800;
    }

    private int obtain(TaskContext ctx, int id, String name, int qty, int fallbackPrice) {
        GameApi a = g();
        if (a.invCount(id) >= qty) return 0;
        if (a.bankCount(id) > 0) {
            int take = Math.min(qty, a.bankCount(id));
            a.withdraw(id, take);
            a.waitUntil(() -> a.invContains(id), 1500);
            return 0;
        }
        int buyQty = id == RuneMethod.PURE_ESSENCE ? ESSENCE_BUY_QTY : qty;
        if (ctx.config.isGeBuySupplies() && affordable(ctx, id, fallbackPrice, buyQty)) { a.closeBank(); return geBuy(ctx, name, id, fallbackPrice, buyQty); }
        return -1;
    }

    private int pause(TaskContext ctx, String what) {
        noMatUntil = System.currentTimeMillis() + NO_MAT_COOLDOWN_MS;
        ctx.log("[rc] no " + what + " and can't buy it — pausing Runecraft for ~10 min.");
        GameApi a = g(); if (a.bankIsOpen()) a.closeBank();
        return 3000;
    }

    // ── GE ─────────────────────────────────────────────────────────────────────────────────────
    private int geBuy(TaskContext ctx, String name, int id, int fallbackPrice, int qty) {
        GameApi a = g();
        int offer = offer(id, fallbackPrice), cost = offer * qty;
        Pos me = a.playerPosition();
        if (me == null || me.distance(GE_TILE) > 8) {
            if (a.geOpen()) a.geClose();
            ctx.log("[rc] travelling to GE to buy " + qty + "x " + name + ".");
            Nav.walkTo(GE_TILE); return 600;
        }
        if (a.invCount(COINS_ID) < cost) {
            if (a.geOpen()) a.geClose();
            if (!a.bankIsOpen()) { if (!Nav.openBank(null)) return 600; return 400; }
            ctx.bank.maybeSnapshot();
            int need = cost - a.invCount(COINS_ID);
            if (a.bankCount(COINS_ID) < need) { ctx.log("[rc] can't afford " + qty + "x " + name + "; skipping."); a.closeBank(); return 1500; }
            a.withdraw(COINS_ID, need);
            a.waitUntil(() -> a.invCount(COINS_ID) >= cost, 1500);
            a.closeBank(); return 500;
        }
        if (!a.geOpen()) { a.openGe(); a.waitUntil(a::geOpen, 3000); return 1000; }
        boolean ok = a.geBuy(id, qty, offer);
        ctx.log("[rc] GE buy " + qty + "x " + name + " @" + offer + "gp ok=" + ok);
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

    private long lastDiag = 0;
    private void diagnose(TaskContext ctx, RuneMethod m) {
        long now = System.currentTimeMillis();
        if (now - lastDiag < 5000) return;
        lastDiag = now;
        GameApi a = g();
        StringBuilder sb = new StringBuilder();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (GameObj o : a.objectsWithin(12)) {
            if (o == null || o.name() == null || o.name().equals("null")) continue;
            String key = o.name() + "#" + o.id();
            if (seen.add(key)) sb.append(key).append('@').append(o.position()).append("  ");
            if (seen.size() >= 15) break;
        }
        ctx.log("[rc] no ruins for " + m.runeName + " at " + a.playerPosition()
                + " (anchor " + m.ruins + ") — nearby objects: " + (sb.length() == 0 ? "none" : sb));
    }

    private int smallSleep() { return 480 + (int) (Math.random() * 420); }
}
