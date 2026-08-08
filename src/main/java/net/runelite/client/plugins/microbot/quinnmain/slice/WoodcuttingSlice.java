package net.runelite.client.plugins.microbot.quinnmain.slice;

import net.runelite.client.plugins.microbot.quinnmain.QuinnMainConfig;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi.Pos;

/**
 * Thin Woodcutting trainer — a {@link GatherLoop.Spec} over the shared engine. No client-specific code;
 * this is the shape every ported trainer takes.
 */
public final class WoodcuttingSlice implements GatherLoop.Spec {

    private final QuinnMainConfig cfg;
    private final GatherLoop loop;

    public WoodcuttingSlice(GameApi game, QuinnMainConfig cfg) {
        this.cfg = cfg;
        this.loop = new GatherLoop(game, this);
    }

    public void tick() { loop.tick(); }

    @Override public Pos spot() { return new Pos(cfg.spotX(), cfg.spotY(), 0); }
    @Override public String[] objectNames() { return new String[]{ cfg.treeName() }; }
    @Override public String action() { return "Chop down"; }
    @Override public int productId() { return cfg.logId(); }
    @Override public boolean bankWhenFull() { return cfg.bankWhenFull(); }
}
