package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;
import net.runelite.client.plugins.microbot.quinnmain.game.Game;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;


import java.util.Map;

/**
 * Decides <b>how</b> to acquire a quest item: buy it (Grand Exchange or a shop) when the account can afford
 * it and the cost is a small slice of its wealth, else <b>self-gather</b> it. This is Quinn's rule —
 * "if it's a significant cost/impact to the account, self-gather; if not, buy it (when the GE/shop is
 * possible)". Buying is unrestricted for a new account (only <i>selling</i> ~43 items is gated, see
 * {@link TradeRestrictions}), so the only real gate on buying is having the coins.
 *
 * <p>Usage: a quest wraps each item as {@code Provision.provide(ctx, id, name, qty, estPrice, gatherFn)}.
 * The {@code gatherFn} is the always-available fallback (mine/kill/pick/shop/smith/cook), so the quest
 * completes with no GE and no coins if it has to.
 */
public final class Provision {

    private static GameApi g() { return Game.api(); }
    private static int INV(int id) { GameApi a = Game.api(); return a == null ? 0 : a.invCount(id); }

    private Provision() { }

    /** The self-gather fallback for an item: returns 0 when the item is in hand, &gt;0 (a delay) while working. */
    public interface Gatherer { int gather(TaskContext ctx); }

    /** Below this absolute gp cost an item is always "cheap enough to just buy" (if affordable). */
    private static final int ALWAYS_CHEAP = 150;
    /** Buy only if the cost is at most this fraction of the account's total value (else it's "significant"). */
    private static final int VALUE_FRACTION_DIV = 10;   // ≤ 10% of (cash + bank value)

    /**
     * Provide {@code qty} of {@code itemId}. Tries to BUY (GE) when it's cheap relative to the account's
     * wealth and affordable; otherwise self-gathers via {@code gatherer}.
     * @return 0 once we hold {@code qty}; &gt;0 (a delay) while acquiring it.
     */
    public static int provide(TaskContext ctx, int itemId, String name, int qty, int estUnitPrice, Gatherer gatherer) {
        if (INV(itemId) >= qty) return 0;

        // Bank-first: if we already own it (bought earlier, over-bought, or banked by the pre-quest clean-bag),
        // withdraw it rather than spend coins buying / time gathering it again.
        int wd = withdrawFromBank(ctx, itemId, name, qty);
        if (wd >= 0) return wd;   // <0 = not owned in bank → fall through to buy/gather

        int need = qty - INV(itemId);
        long cost = (long) need * Math.max(1, estUnitPrice);

        if (shouldBuy(ctx, cost)) {
            int d = GeBuy.ensure(itemId, name, qty, Math.max(estUnitPrice * 2, estUnitPrice + 50));
            if (d != GeBuy.CANT_BUY) return d;   // 0 = have it, >0 = buying; CANT_BUY falls through to gather
        }
        return gatherer.gather(ctx);
    }

    /**
     * Batch bank-withdrawal: if ANY of {@code ids} we already own is sitting in the bank, make a SINGLE bank
     * visit and withdraw every one of them (the shortfall each), then close. Call this once at the top of a
     * quest's execute so already-owned ingredients come out together instead of one open/close per item.
     * @return 0 when there's nothing (more) to withdraw; &gt;0 (a delay) while walking to / using the bank.
     */
    public static int withdrawAll(TaskContext ctx, int[] ids, int[] qtys) {
        try {
            boolean anyBanked = false;
            for (int i = 0; i < ids.length; i++) {
                if (INV(ids[i]) >= qtys[i]) continue;
                int banked = g().bankIsOpen() ? g().bankCount(ids[i]) : ctx.bank.count(ids[i]);
                if (banked > 0) { anyBanked = true; break; }
            }
            if (!anyBanked) { if (g().bankIsOpen()) g().closeBank(); return 0; }
            if (!g().bankIsOpen()) { if (!Nav.openBank(null)) return 700; return 400; }
            for (int i = 0; i < ids.length; i++) {
                final int id = ids[i], qty = qtys[i];
                int have = INV(id);
                int need = qty - have;
                if (need <= 0) continue;
                int banked = g().bankCount(id);
                if (banked <= 0) continue;
                int take = Math.min(need, banked);
                final int target = have + take;
                g().withdraw(id, take);
                g().waitUntil(() -> INV(id) >= target, 1500);
                Log.log("[provision] withdrew " + take + "x #" + id + " from bank (batch).");
            }
            g().closeBank();
            return 500;
        } catch (Throwable t) {
            Log.log("[provision] withdrawAll failed (" + t + ").");
            return 0;
        }
    }

    /**
     * If {@code itemId} is already sitting in the bank, walk to a bank and withdraw the shortfall.
     * @return 0 once we hold {@code qty}; &gt;0 (a delay) while walking/withdrawing; {@code -1} if it isn't
     *         banked (so the caller should buy or self-gather instead).
     */
    private static int withdrawFromBank(TaskContext ctx, int itemId, String name, int qty) {
        try {
            int have = INV(itemId);
            if (have >= qty) return 0;
            boolean bankOpen = g().bankIsOpen();
            int banked = bankOpen ? g().bankCount(itemId) : ctx.bank.count(itemId);
            if (banked <= 0) { if (bankOpen) g().closeBank(); return -1; }   // not owned → buy/gather
            if (!bankOpen) {
                if (!Nav.openBank(null)) return 700;                      // travel to the nearest bank
                return 400;
            }
            int take = Math.min(qty - have, banked);
            g().withdraw(itemId, take);
            int target = have + take;
            g().waitUntil(() -> INV(itemId) >= target, 2000);
            Log.log("[provision] withdrew " + take + "x " + name + " from bank (" + banked + " banked).");
            g().closeBank();
            return 500;
        } catch (Throwable t) {
            Log.log("[provision] bank-withdraw " + name + " failed (" + t + ").");
            return -1;
        }
    }

    /**
     * Should we BUY this (vs self-gather)? Only when we can afford it now AND the cost is either trivially
     * small or a modest slice of the account's value — a "significant" cost is gathered instead.
     */
    public static boolean shouldBuy(TaskContext ctx, long cost) {
        try {
            long cash = GeBuy.coins();
            if (cash < cost) return false;                       // can't afford now → gather (earn gp later)
            if (cost <= ALWAYS_CHEAP) return true;               // pocket change → just buy
            long value = cash + bankValue(ctx);
            return cost <= value / VALUE_FRACTION_DIV;            // ≤ ~10% of wealth → buy, else gather
        } catch (Throwable t) {
            return false;   // unsure → gather (the always-available path)
        }
    }

    // Bank valuation is HTTP-priced (PriceLookup), so cache it — the buy/gather decision runs every loop.
    private static long cachedBankValue = 0L;
    private static long cachedAt = 0L;
    private static final long VALUE_TTL_MS = 120_000L;

    /**
     * gp value of the banked stock (coins at face value, everything else at its GE price via
     * {@link PriceLookup}). Read from the persistent {@link BankMemory} snapshot so it works away from a
     * bank, and cached for {@value #VALUE_TTL_MS}ms because pricing hits the wiki API. A missing price
     * counts as 0 (untradeable / unknown) — conservative, only ever lowering the buy threshold.
     */
    private static long bankValue(TaskContext ctx) {
        try {
            long now = System.currentTimeMillis();
            if (now - cachedAt < VALUE_TTL_MS) return cachedBankValue;
            long total = 0;
            for (Map.Entry<Integer, Integer> e : ctx.bank.contentsCopy().entrySet()) {
                int id = e.getKey(), qty = Math.max(0, e.getValue());
                if (qty == 0) continue;
                long unit;
                if (id == 995) unit = 1;                         // coins
                else {
                    int p = PriceLookup.high(id);
                    if (p <= 0) p = PriceLookup.low(id);
                    unit = Math.max(0, p);
                }
                total += (long) qty * unit;
            }
            cachedBankValue = total;
            cachedAt = now;
            return total;
        } catch (Throwable t) {
            return cachedBankValue;   // reuse the last good value on any hiccup
        }
    }
}
