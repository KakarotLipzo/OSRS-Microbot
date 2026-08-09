package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;
import net.runelite.client.plugins.microbot.quinnmain.game.Game;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store-first material provisioning for skilling (feature-backlog {@code feat-skilling-material-provision}).
 * A skilling task that needs a consumable/tool it lacks calls {@link #tryStore} first; if there's an F2P
 * shop for the item ({@link SupplyShop}) it walks there and buys it, otherwise (or if the shop can't
 * supply it) it returns {@link #NO_STORE} and the caller falls back to its existing Grand Exchange path.
 *
 * <p>Buying from a shop is preferred because it's cheaper, has no GE buy-limit or ~4-minute fill wait, and
 * works even while a young account is still GE-restricted. It's the same {@code Gather.shopBuy} path the
 * quests use, just driven by a shared item→shop catalog.
 *
 * <h2>Fail-safe</h2>
 * A shop that's out of stock / too broke to buy is skipped for {@link #STORE_SKIP_MS} (then the GE covers
 * it); a shop we simply can't <b>reach</b> in {@link #REACH_MS} (e.g. blocked by the Al Kharid toll while
 * broke) also gives up to the GE — so a shop trip can never become a hang.
 */
public final class SupplyBuy {

    private static int inv(int id) { GameApi a = Game.api(); return a == null ? 0 : a.invCount(id); }

    private SupplyBuy() { }

    /** Sentinel: no shop can supply this item right now — the caller should use its GE fallback. */
    public static final int NO_STORE = Integer.MIN_VALUE;

    private static final long STORE_SKIP_MS = 5 * 60 * 1000L;   // after a shop fails, don't retry it for this long
    private static final long REACH_MS = 60_000L;               // give up walking to a shop after this

    private static final Map<Integer, Long> skipUntil = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> tryingSince = new ConcurrentHashMap<>();

    /**
     * Try to buy {@code qty} of {@code itemId} from its F2P shop.
     *
     * @return {@code 0} once the shop supplied enough; {@code >0} (a delay) while walking to / buying at
     *         the shop; {@link #NO_STORE} when there's no shop for it, or the shop couldn't supply it —
     *         the caller then falls through to the Grand Exchange.
     */
    public static int tryStore(TaskContext ctx, int itemId, int qty) {
        try {
            if (inv(itemId) >= qty) { tryingSince.remove(itemId); return 0; }

            SupplyShop shop = SupplyShop.forItem(itemId);
            if (shop == null) return NO_STORE;

            long now = System.currentTimeMillis();
            Long skip = skipUntil.get(itemId);
            if (skip != null && now < skip) return NO_STORE;      // recently failed here → GE

            int before = inv(itemId);
            int r = Gather.shopBuy(itemId, shop.itemName, shop.npc, shop.tile, qty);
            if (r > 0) {
                // Still travelling to / interacting with the shop — but don't walk forever (toll gate etc.).
                long since = tryingSince.getOrDefault(itemId, 0L);
                if (since == 0L) { tryingSince.put(itemId, now); since = now; }
                if (now - since > REACH_MS) {
                    tryingSince.remove(itemId);
                    skipUntil.put(itemId, now + STORE_SKIP_MS);
                    ctx.log("[supply] couldn't reach " + shop.npc + " for " + shop.itemName
                            + " in time — using the GE.");
                    return NO_STORE;
                }
                return r;
            }
            tryingSince.remove(itemId);
            if (inv(itemId) >= qty) { skipUntil.remove(itemId); return 0; }  // bought enough
            if (inv(itemId) > before) return 400;     // partial buy — loop to top up

            // Shop couldn't supply (out of stock / broke) → skip it for a while and let the GE cover it.
            skipUntil.put(itemId, now + STORE_SKIP_MS);
            ctx.log("[supply] " + shop.itemName + " not available from " + shop.npc + " — using the GE.");
            return NO_STORE;
        } catch (Throwable t) {
            return NO_STORE;
        }
    }
}
