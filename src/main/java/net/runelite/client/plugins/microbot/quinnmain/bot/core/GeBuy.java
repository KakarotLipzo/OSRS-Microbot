package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Game;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

/**
 * Minimal Grand Exchange buy helper for provisioning: get {@code qty} of a cheap item into inventory.
 * Ported to the facade — walking/opening/buying/collecting go through {@link GameApi}.
 *
 * <h2>Return contract</h2>
 * <ul><li><b>0</b> — inventory already holds {@code qty}.</li>
 *     <li><b>&gt;0</b> — a delay while working at the GE.</li>
 *     <li>{@link #CANT_BUY} — can't afford / GE won't transact → caller self-gathers.</li></ul>
 */
public final class GeBuy {

    private GeBuy() { }

    public static final int CANT_BUY = -1;
    private static final int COINS = 995;
    private static final int MAX_FAILS = 3;

    private static final java.util.Map<Integer, Integer> FAILS = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile boolean tradingBlocked = false;

    public static void setTradingBlocked(boolean blocked) { tradingBlocked = blocked; }
    public static boolean isTradingBlocked() { return tradingBlocked; }

    private static GameApi g() { return Game.api(); }
    public static int coins() { try { GameApi a = g(); return a == null ? 0 : a.invCount(COINS); } catch (Throwable t) { return 0; } }

    private static final java.util.Map<Integer, Long> LAST_LOG = new java.util.concurrent.ConcurrentHashMap<>();
    private static void logThrottled(int itemId, String msg) {
        long now = System.currentTimeMillis();
        Long last = LAST_LOG.get(itemId);
        if (last == null || now - last > 15000) { LAST_LOG.put(itemId, now); Log.log(msg); }
    }

    public static int ensure(int itemId, String name, int qty, int maxUnitPrice) {
        GameApi a = g();
        if (a == null) return CANT_BUY;
        try {
            int have = a.invCount(itemId);
            if (have >= qty) { if (a.geOpen()) a.geClose(); FAILS.remove(itemId); return 0; }
            int need = qty - have;

            if (tradingBlocked) { if (a.geOpen()) a.geClose(); return CANT_BUY; }

            long cost = (long) need * maxUnitPrice;
            if (coins() < cost) {
                logThrottled(itemId, "[gebuy] can't afford " + need + "x " + name + " — have " + coins()
                        + "gp, need up to " + cost + "gp. Caller should gather it.");
                if (a.geOpen()) a.geClose();
                return CANT_BUY;
            }

            if (a.geReadyToCollect()) { a.geCollectAll(); a.sleep(600); return 400; }
            if (!a.geOpen()) { a.openGe(); a.waitUntil(a::geOpen, 3000); return 500; }

            boolean ok = a.geBuy(itemId, need, maxUnitPrice);
            Log.log("[gebuy] buying " + need + "x " + name + " (#" + itemId + ") @<=" + maxUnitPrice + "gp ok=" + ok);
            if (!ok) {
                int f = FAILS.merge(itemId, 1, Integer::sum);
                if (f >= MAX_FAILS) {
                    Log.log("[gebuy] " + name + " buy failed " + f + "x — GE won't transact for this account. "
                            + "Latching GE-blocked; callers self-gather from here.");
                    FAILS.remove(itemId);
                    tradingBlocked = true;
                    if (a.geOpen()) a.geClose();
                    return CANT_BUY;
                }
            } else {
                FAILS.remove(itemId);
            }
            a.waitUntil(a::geReadyToCollect, 8000);
            return 1000;
        } catch (Throwable t) {
            Log.log("[gebuy] " + name + " buy errored (" + t + ") — caller should gather it.");
            return CANT_BUY;
        }
    }
}
