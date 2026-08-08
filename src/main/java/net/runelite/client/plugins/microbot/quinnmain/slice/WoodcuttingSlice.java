package net.runelite.client.plugins.microbot.quinnmain.slice;

import net.runelite.client.plugins.microbot.quinnmain.QuinnMainConfig;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi.GameObj;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi.Pos;

/**
 * <h2>Vertical-slice proof: a woodcutting loop written entirely against {@link GameApi}.</h2>
 *
 * This class contains <b>zero</b> {@code Rs2*} / DreamBot references — it is exactly the shape every
 * ported trainer will take. If this compiles and runs on Microbot, the port pattern is proven and the
 * remaining ~100 files are mechanical repetition of it (see PORT_PLAN.md).
 *
 * <p>It deliberately uses only facade methods that {@code MicrobotGameApi} fully implements, so it is
 * genuinely runnable once built in a Microbot fork — not a stub.
 */
public final class WoodcuttingSlice {

    private final GameApi game;
    private final QuinnMainConfig cfg;

    public WoodcuttingSlice(GameApi game, QuinnMainConfig cfg) {
        this.game = game;
        this.cfg = cfg;
    }

    /** One iteration of the loop — called every ~600ms by the script's scheduler. */
    public void tick() {
        if (!game.isLoggedIn()) return;

        // 1. Full inventory → offload (bank or drop), then resume.
        if (game.invIsFull()) {
            if (cfg.bankWhenFull()) bank();
            else game.invDropAll(cfg.logId());
            return;
        }

        // 2. Already chopping? Let the animation run.
        if (game.isAnimating()) return;

        // 3. Walk to the tree area if we're not there yet.
        Pos spot = new Pos(cfg.spotX(), cfg.spotY(), 0);
        if (!game.arrived(spot, 6)) {
            game.walkTo(spot);
            return;
        }

        // 4. Find a tree and chop it; wait until we actually start swinging (progress-based, not a fixed sleep).
        GameObj tree = game.nearestObject(cfg.treeName());
        if (tree == null) {
            game.walkTo(spot);   // trees respawn elsewhere in the cluster; nudge back to the anchor
            return;
        }
        if (game.interactObject(tree, "Chop down")) {
            game.waitUntil(() -> game.isAnimating() || game.invIsFull(), 4000);
        }
    }

    private void bank() {
        if (!game.bankIsOpen()) {
            game.openNearestBank();               // walks + opens
            game.waitUntil(game::bankIsOpen, 8000);
            return;
        }
        game.depositInventory();
        game.waitUntil(() -> game.invEmptySlots() > 20, 3000);
        game.closeBank();
    }
}
