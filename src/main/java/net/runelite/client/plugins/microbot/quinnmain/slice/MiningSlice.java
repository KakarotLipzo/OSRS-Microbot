package net.runelite.client.plugins.microbot.quinnmain.slice;

import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi.Pos;

/**
 * Thin Mining trainer — same {@link GatherLoop} as Woodcutting, different spec (rock names, "Mine",
 * ore id). Client-neutral: takes plain data. Proves the shared engine generalises with zero new facade.
 */
public final class MiningSlice implements GatherLoop.Spec {

    private final Pos spot;
    private final String rockName;
    private final int oreId;
    private final boolean bankWhenFull;
    private final GatherLoop loop;

    public MiningSlice(GameApi game, int spotX, int spotY, String rockName, int oreId, boolean bankWhenFull) {
        this.spot = new Pos(spotX, spotY, 0);
        this.rockName = rockName;
        this.oreId = oreId;
        this.bankWhenFull = bankWhenFull;
        this.loop = new GatherLoop(game, this);
    }

    public void tick() { loop.tick(); }

    @Override public Pos spot() { return spot; }
    @Override public String[] objectNames() { return new String[]{ rockName }; }
    @Override public String action() { return "Mine"; }
    @Override public int productId() { return oreId; }
    @Override public boolean bankWhenFull() { return bankWhenFull; }
}
