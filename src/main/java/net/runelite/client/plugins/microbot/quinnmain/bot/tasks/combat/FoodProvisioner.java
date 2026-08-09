package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.combat;

import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.MakeInterface;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.Nav;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.PriceLookup;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.TaskContext;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.fishing.FishMethod;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.fishing.FishingArea;
import net.runelite.client.plugins.microbot.quinnmain.game.Game;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi.GameObj;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi.Npc;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;

import java.util.function.Predicate;

/**
 * Bootstraps food from scratch (broke, low-level, can't buy): net-fish shrimps at the safe Lumbridge
 * Swamp and cook them on the castle range. Ported to the {@link GameApi} facade. One step per call;
 * returns -1 only when it genuinely can't proceed (no net & none affordable).
 */
public class FoodProvisioner {

    private static final int COINS_ID = 995, SMALL_NET = 303, RAW_SHRIMP = 317, COOKED_SHRIMP = 315, BURNT_SHRIMP = 323;
    private static final int COOK_BATCH = 10;
    private static final Pos FISH_TILE = FishingArea.LUMBRIDGE_SWAMP.anchor;
    private static final Pos RANGE_TILE = new Pos(3211, 3215, 0);
    private static final Pos GE_TILE = new Pos(3164, 3486, 0);

    private long lastDiag = 0, lastFishAction = 0;

    private static GameApi g() { return Game.api(); }
    private GameObj closestObj(Predicate<GameObj> p, int r) {
        GameApi a = g(); if (a == null) return null; GameObj best = null; double bd = Double.MAX_VALUE;
        for (GameObj o : a.objectsWithin(r)) { if (o == null) continue; try { if (!p.test(o)) continue; } catch (Throwable t) { continue; } double d = o.distance(); if (d < bd) { bd = d; best = o; } }
        return best;
    }
    private Npc closestNpc(Predicate<Npc> p, int r) {
        GameApi a = g(); if (a == null) return null; Npc best = null; double bd = Double.MAX_VALUE;
        for (Npc n : a.npcsWithin(r)) { if (n == null) continue; try { if (!p.test(n)) continue; } catch (Throwable t) { continue; } double d = n.distance(); if (d < bd) { bd = d; best = n; } }
        return best;
    }

    public int provision(TaskContext ctx) {
        GameApi a = g(); if (a == null) return 600;
        dropBurnt();
        if (!a.invContains(SMALL_NET)) { int n = acquireNet(ctx); if (n != 0) return n; }
        if (!a.invContains(SMALL_NET)) return -1;
        int raw = a.invCount(RAW_SHRIMP);
        if (raw > 0 && (raw >= COOK_BATCH || a.invIsFull())) return cook(ctx);
        if (a.invIsFull()) return cook(ctx);
        return fish(ctx);
    }

    private int acquireNet(TaskContext ctx) {
        GameApi a = g();
        if (a.invContains(SMALL_NET)) return 0;
        if (ctx.bank.has(SMALL_NET)) {
            if (!a.bankIsOpen()) { if (!Nav.openBank(BankLoc.LUMBRIDGE)) return 600; return 400; }
            a.withdraw(SMALL_NET, 1);
            a.waitUntil(() -> a.invContains(SMALL_NET), 1500);
            ctx.log("[provision] withdrew small fishing net for the food bootstrap.");
            a.closeBank();
            return 700;
        }
        if (ctx.config.isGeBuySupplies()) { int offer = netOffer(); if (a.invCount(COINS_ID) >= offer) return geBuyNet(ctx, offer); }
        return -1;
    }

    private int netOffer() { int live = PriceLookup.high(SMALL_NET); int base = live > 0 ? live : 200; return (int) Math.ceil(base * 1.05) + 1; }

    private int geBuyNet(TaskContext ctx, int offer) {
        GameApi a = g();
        if (!a.geOpen()) {
            Pos me = a.playerPosition();
            if (me == null || me.distance(GE_TILE) > 8) { Nav.walkTo(GE_TILE); return 600; }
            a.openGe(); a.waitUntil(a::geOpen, 3000); return 1000;
        }
        boolean ok = a.geBuy(SMALL_NET, 1, offer);
        ctx.log("[provision] GE essential-buy small fishing net @" + offer + "gp ok=" + ok);
        a.sleep(3000); a.geCollectAll(); a.waitUntil(() -> a.invContains(SMALL_NET), 4000);
        a.geClose();
        return 1000;
    }

    private int fish(TaskContext ctx) {
        GameApi a = g();
        if (a.isAnimating()) return 1200 + (int) (Math.random() * 900);
        if (a.isMoving()) return smallSleep();
        if (System.currentTimeMillis() - lastFishAction < 2500) return smallSleep();
        Npc spot = findSpot();
        if (spot == null || spot.distance() > 12) { maybeDiag(ctx, spot); Nav.walkTo(FISH_TILE); return 600; }
        boolean started = spot.interact("Net");
        if (!started) started = spot.useItem(SMALL_NET);
        if (started) {
            lastFishAction = System.currentTimeMillis();
            ctx.log("[provision] net-fishing shrimps at " + spot.name() + "#" + spot.id());
            a.waitUntil(() -> a.isAnimating() || a.invIsFull(), 4000);
        } else { maybeDiag(ctx, spot); }
        return smallSleep();
    }

    private Npc findSpot() {
        Npc byId = closestNpc(n -> n.id() == FishMethod.SHRIMP.spotNpcId, 20);
        if (byId != null) return byId;
        return closestNpc(n -> n.name() != null && n.name().toLowerCase().contains("fishing spot"), 20);
    }

    private void maybeDiag(TaskContext ctx, Npc spot) {
        long now = System.currentTimeMillis();
        if (now - lastDiag < 5000) return;
        lastDiag = now;
        Pos me = g().playerPosition();
        ctx.log("[provision] can't net yet: at " + me + " dist-to-anchor=" + (int) (me == null ? -1 : me.distance(FISH_TILE))
                + (spot != null ? " (spot @" + (int) spot.distance() + ")" : " (no spot loaded)"));
    }

    private int cook(TaskContext ctx) {
        GameApi a = g();
        if (a.isAnimating()) return smallSleep();
        if (!a.invContains(RAW_SHRIMP)) return 600;
        GameObj range = findRange();
        if (range == null) { ctx.log("[provision] no range nearby; walking to the Lumbridge castle range."); Nav.walkTo(RANGE_TILE); return 600; }
        if (range.distance() > 6) { Nav.walkTo(range.position()); return 600; }
        if (range.useItem(RAW_SHRIMP)) {
            ctx.log("[provision] cooking shrimps on " + range.name() + "#" + range.id());
            a.waitUntil(() -> a.isAnimating() || a.dialogueOpen() || MakeInterface.isOpen(), 3000);
            if (!a.isAnimating()) { MakeInterface.click(null); a.waitUntil(a::isAnimating, 2000); }
            a.waitUntil(() -> !a.invContains(RAW_SHRIMP) || (!a.isAnimating() && !a.dialogueOpen()), 60000);
        }
        return smallSleep();
    }

    private GameObj findRange() {
        return closestObj(o -> o.name() != null && (o.name().toLowerCase().contains("range") || o.name().toLowerCase().contains("stove")), 8);
    }

    private void dropBurnt() {
        GameApi a = g();
        for (int i = 0; i < 28 && a.invContains(BURNT_SHRIMP); i++) { if (!a.invInteract(BURNT_SHRIMP, "Drop")) break; a.sleep(200); }
    }

    private int smallSleep() { return 480 + (int) (Math.random() * 420); }
}
