package net.runelite.client.plugins.microbot.quinnmain.slice;

import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi.GameObj;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi.Pos;

/**
 * Shared "gather from a world object" loop — the reusable skilling engine that thin trainers sit on
 * (this mirrors OSRS-Main's design: thin trainers over a shared engine). Woodcutting and Mining are
 * the same loop with different {@link Spec}s (object names, action verb, product id). Fishing, cooking,
 * etc. extend the pattern as they're ported.
 *
 * <p>Client-neutral: talks to {@link GameApi} only, so it runs unchanged on any adapter.
 */
public final class GatherLoop {

    /** What a given gathering skill/activity needs — supplied by a thin trainer. */
    public interface Spec {
        Pos spot();
        String[] objectNames();
        String action();        // e.g. "Chop down", "Mine"
        int productId();        // the item produced (logs, ore) — used to drop/keep
        boolean bankWhenFull(); // true = bank, false = power-drop
    }

    private final GameApi game;
    private final Spec spec;

    public GatherLoop(GameApi game, Spec spec) {
        this.game = game;
        this.spec = spec;
    }

    /** One scheduler iteration (~600ms). */
    public void tick() {
        if (!game.isLoggedIn()) return;

        if (game.invIsFull()) {
            if (spec.bankWhenFull()) bank();
            else game.invDropAll(spec.productId());
            return;
        }

        if (game.isAnimating()) return;   // already gathering — let it run

        Pos spot = spec.spot();
        if (!game.arrived(spot, 6)) { game.walkTo(spot); return; }

        GameObj target = game.nearestObject(spec.objectNames());
        if (target == null) { game.walkTo(spot); return; }   // depleted nearby — nudge back to the anchor
        if (game.interactObject(target, spec.action())) {
            game.waitUntil(() -> game.isAnimating() || game.invIsFull(), 4000);
        }
    }

    private void bank() {
        if (!game.bankIsOpen()) {
            game.openNearestBank();
            game.waitUntil(game::bankIsOpen, 8000);
            return;
        }
        game.depositInventory();
        game.waitUntil(() -> game.invEmptySlots() > 20, 3000);
        game.closeBank();
    }
}
