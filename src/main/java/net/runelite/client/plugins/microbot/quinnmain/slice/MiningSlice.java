package net.runelite.client.plugins.microbot.quinnmain.slice;

import net.runelite.client.plugins.microbot.quinnmain.QuinnMainConfig;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi.Pos;

/**
 * Thin Mining trainer — same {@link GatherLoop} as Woodcutting, different spec (rock names, "Mine",
 * ore id). Proves the shared engine generalises past one skill with zero new facade surface.
 */
public final class MiningSlice implements GatherLoop.Spec {

    private final QuinnMainConfig cfg;
    private final GatherLoop loop;

    public MiningSlice(GameApi game, QuinnMainConfig cfg) {
        this.cfg = cfg;
        this.loop = new GatherLoop(game, this);
    }

    public void tick() { loop.tick(); }

    @Override public Pos spot() { return new Pos(cfg.mineX(), cfg.mineY(), 0); }
    // Rock objects are named by their ore ("Copper rocks", "Tin rocks", "Iron rocks"); the config
    // holds the display name so one slice covers every F2P rock.
    @Override public String[] objectNames() { return new String[]{ cfg.rockName() }; }
    @Override public String action() { return "Mine"; }
    @Override public int productId() { return cfg.oreId(); }
    @Override public boolean bankWhenFull() { return cfg.bankWhenFull(); }
}
