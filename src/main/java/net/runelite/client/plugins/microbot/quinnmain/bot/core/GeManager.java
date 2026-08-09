package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Game;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi.GeOffer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Grand Exchange selling + offer management — the piece every money method depends on. Ported from
 * OSRS-Main to the {@link GameApi} facade's GE-offer model. Tracks each offer; a stale offer that has
 * moved nothing in {@link #STALE_MS} is cancelled and re-listed (sells undercut down to a floor, buys
 * bumped up to a ceiling), so a mispriced offer never silently ties up capital forever.
 */
public class GeManager {

    private static final long STALE_MS = 4 * 60 * 1000L;
    private static final int UNDERCUT_STEP_PCT = 5;
    private static final double PRICE_FLOOR_PCT = 0.55;
    private static final double PRICE_CEILING_PCT = 1.30;

    private static GameApi g() { return Game.api(); }
    private List<GeOffer> offers() { GameApi a = g(); return a == null ? java.util.Collections.emptyList() : a.geOffers(); }

    private static final class Offer {
        final int itemId;
        long placedAt;
        int price;
        int undercuts;
        int lastTransferred;
        boolean buy = false;
        boolean placed = true;
        int maxBuy = 0;
        int minSell = 0;
        Offer(int itemId, int price) { this.itemId = itemId; this.price = price; this.placedAt = System.currentTimeMillis(); }
    }

    private final Map<Integer, Offer> tracked = new HashMap<>();
    private long uncredited = 0;

    // ── queries ──────────────────────────────────────────────────────────────────────────────
    public int freeSlots() { GameApi a = g(); return a == null ? 0 : Math.max(0, 3 - a.geUsedSlots()); }

    public boolean hasCollectable() {
        for (GeOffer o : offers()) { try { if (o != null && o.readyToCollect()) return true; } catch (Throwable ignored) { } }
        return false;
    }

    public boolean hasStaleTracked() {
        long now = System.currentTimeMillis();
        for (Offer o : tracked.values())
            if (o.placed && o.lastTransferred == 0 && now - o.placedAt >= STALE_MS) return true;
        return !pendingRelist().isEmpty() || !pendingRebuy().isEmpty();
    }

    public java.util.List<Integer> pendingRelist() {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        for (Offer o : tracked.values()) if (!o.placed && !o.buy) ids.add(o.itemId);
        return ids;
    }
    public java.util.List<Integer> pendingRebuy() {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        for (Offer o : tracked.values()) if (!o.placed && o.buy) ids.add(o.itemId);
        return ids;
    }
    public int pendingPrice(int itemId) { Offer o = tracked.get(itemId); return (o != null && !o.placed) ? o.price : 0; }
    public void forgetPending(int itemId) { Offer o = tracked.get(itemId); if (o != null && !o.placed) tracked.remove(itemId); }
    public int trackedCount() { return tracked.size(); }
    public long takeRealised() { long gp = uncredited; uncredited = 0; return gp; }

    public boolean isSelling(int itemId) {
        try { for (GeOffer o : offers()) if (o != null && o.sell() && o.itemId() == itemId) return true; } catch (Throwable ignored) { }
        return false;
    }
    public boolean isBuying(int itemId) {
        try { for (GeOffer o : offers()) if (o != null && o.buy() && o.itemId() == itemId) return true; } catch (Throwable ignored) { }
        return false;
    }

    // ── the loop ─────────────────────────────────────────────────────────────────────────────
    public int tick(TaskContext ctx) {
        try {
            int collected = collectFinished(ctx);
            if (collected > 0) return collected;
            return repriceStale(ctx);
        } catch (Throwable t) { ctx.log("[ge] tick failed: " + t); return 0; }
    }

    private int collectFinished(TaskContext ctx) {
        GameApi a = g(); if (a == null) return 0;
        boolean any = false;
        long coins = 0;
        for (GeOffer o : offers()) {
            if (o == null || !o.readyToCollect()) continue;
            any = true;
            if (o.sell()) { try { coins += Math.max(0, o.transferredValue()); } catch (Throwable ignored) { } }
        }
        if (!any) return 0;
        if (!ensureOpen(ctx)) return 800;
        if (a.geCollectToBank()) {
            uncredited += coins;
            ctx.log("[ge] collected finished offers to the bank" + (coins > 0 ? " (+" + coins + "gp)." : "."));
            a.sleep(750);
            tracked.values().removeIf(o -> o.placed && !isSelling(o.itemId));
            return 900;
        }
        return 600;
    }

    private int repriceStale(TaskContext ctx) {
        GameApi a = g(); if (a == null) return 0;
        long now = System.currentTimeMillis();
        for (GeOffer o1 : offers()) {
            if (o1 == null || (!o1.sell() && !o1.buy())) continue;
            Offer o = tracked.get(o1.itemId());
            if (o == null || !o.placed || o.buy != o1.buy()) continue;

            int moved = safeTransferred(o1);
            if (moved != o.lastTransferred) { o.lastTransferred = moved; o.placedAt = now; continue; }
            if (moved > 0 || now - o.placedAt < STALE_MS) continue;

            int next;
            if (o.buy) {
                int ceiling = o.maxBuy > 0 ? o.maxBuy : (int) Math.max(o.price + 1, PRICE_CEILING_PCT * basePrice(o.itemId));
                next = (int) Math.min(ceiling, Math.round(o.price * (1.0 + UNDERCUT_STEP_PCT / 100.0)));
                if (next <= o.price) {
                    if (o.maxBuy > 0) {
                        if (!ensureOpen(ctx)) return 800;
                        ctx.log("[ge] flip buy " + o.itemId + " unfilled at " + o.price + "gp (at its ceiling) — cancelling to free the capital.");
                        a.geCancel(o1.slot()); a.sleep(750); a.geCollectToBank();
                        tracked.remove(o.itemId);
                        return 900;
                    }
                    ctx.log("[ge] buy " + o.itemId + " unfilled at " + o.price + "gp and already at the price ceiling — leaving it.");
                    o.placedAt = now; continue;
                }
            } else {
                int floor = o.minSell > 0 ? o.minSell : (int) Math.max(1, PRICE_FLOOR_PCT * basePrice(o.itemId));
                next = (int) Math.max(floor, Math.round(o.price * (1.0 - UNDERCUT_STEP_PCT / 100.0)));
                if (next >= o.price) {
                    ctx.log("[ge] " + o.itemId + " unsold at " + o.price + "gp and already at the price floor — leaving it.");
                    o.placedAt = now; continue;
                }
            }

            if (!ensureOpen(ctx)) return 800;
            ctx.log("[ge] " + (o.buy ? "buy " : "") + o.itemId + " hasn't moved in " + (STALE_MS / 60_000)
                    + "m at " + o.price + "gp — relisting at " + next + "gp (reprice #" + (o.undercuts + 1) + ").");
            a.geCancel(o1.slot()); a.sleep(850); a.geCollectToBank(); a.sleep(650);
            o.price = next; o.undercuts++; o.placedAt = now; o.placed = false;
            return 900;
        }
        return 0;
    }

    private int safeTransferred(GeOffer o) { try { return o.transferredAmount(); } catch (Throwable t) { return 0; } }

    // ── selling ──────────────────────────────────────────────────────────────────────────────
    public int sell(TaskContext ctx, int itemId, String name, int quantity) { return sell(ctx, itemId, name, quantity, basePrice(itemId), 0); }

    public int sell(TaskContext ctx, int itemId, String name, int quantity, int listPrice, int floor) {
        GameApi a = g(); if (a == null) return 0;
        if (quantity <= 0) return 0;
        if (isSelling(itemId)) return 0;
        if (freeSlots() <= 0) return 0;
        if (!TradeRestrictions.canSellOnGe(ctx.account, itemId)) {
            ctx.log("[ge] " + name + " (#" + itemId + ") is new-account trade-restricted — not listing until it qualifies.");
            return 0;
        }
        if (!ensureOpen(ctx)) return 800;
        Offer prior = tracked.get(itemId);
        int price = prior != null ? prior.price : listPrice;
        if (price <= 0) { ctx.log("[ge] no price for " + name + " (#" + itemId + ") — skipping the sale."); return 0; }
        boolean ok = a.geSell(itemId, quantity, price);
        ctx.log("[ge] selling " + quantity + "x " + name + " @ " + price + "gp ok=" + ok);
        if (ok) {
            Offer o = new Offer(itemId, price);
            if (prior != null) o.undercuts = prior.undercuts;
            o.minSell = floor > 0 ? floor : (prior != null ? prior.minSell : 0);
            tracked.put(itemId, o);
            a.sleep(1000);
            return 1000;
        }
        return 600;
    }

    // ── buying ───────────────────────────────────────────────────────────────────────────────
    public int buy(TaskContext ctx, int itemId, String name, int quantity, int maxUnitPrice) { return buy(ctx, itemId, name, quantity, maxUnitPrice, 0); }

    public int buy(TaskContext ctx, int itemId, String name, int quantity, int maxUnitPrice, int ceiling) {
        GameApi a = g(); if (a == null) return 0;
        if (quantity <= 0) return 0;
        if (isBuying(itemId)) return 0;
        if (freeSlots() <= 0) return 0;
        if (!ensureOpen(ctx)) return 800;
        Offer prior = tracked.get(itemId);
        int price = prior != null && !prior.placed ? prior.price : buyPrice(itemId);
        if (maxUnitPrice > 0) price = Math.min(price, maxUnitPrice);
        if (price <= 0) { ctx.log("[ge] no price for " + name + " (#" + itemId + ") — skipping the buy."); return 0; }
        boolean ok = a.geBuy(itemId, quantity, price);
        ctx.log("[ge] buying " + quantity + "x " + name + " @ " + price + "gp ok=" + ok);
        if (ok) {
            Offer o = new Offer(itemId, price);
            o.buy = true;
            if (prior != null) o.undercuts = prior.undercuts;
            o.maxBuy = ceiling > 0 ? ceiling : (prior != null ? prior.maxBuy : 0);
            tracked.put(itemId, o);
            a.sleep(1000);
            return 1000;
        }
        return 600;
    }

    private int buyPrice(int itemId) {
        try {
            int high = PriceLookup.high(itemId);
            if (high > 0) return (int) Math.round(high * 1.05) + 1;
            int low = PriceLookup.low(itemId);
            return low > 0 ? (int) Math.round(low * 1.10) + 1 : 0;
        } catch (Throwable t) { return 0; }
    }

    private int basePrice(int itemId) {
        try {
            int low = PriceLookup.low(itemId);
            if (low > 0) return low;
            int high = PriceLookup.high(itemId);
            return high > 0 ? (int) Math.round(high * 0.95) : 0;
        } catch (Throwable t) { return 0; }
    }

    private boolean ensureOpen(TaskContext ctx) {
        GameApi a = g(); if (a == null) return false;
        try {
            if (a.geOpen()) return true;
            a.openGe();
            a.waitUntil(a::geOpen, 4000);
            return a.geOpen();
        } catch (Throwable t) { return false; }
    }

    public void close() { GameApi a = g(); try { if (a != null && a.geOpen()) a.geClose(); } catch (Throwable ignored) { } }
}
