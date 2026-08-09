package net.runelite.client.plugins.microbot.quinnmain.slice;

import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;

/**
 * Thin Woodcutting trainer — a {@link GatherLoop.Spec} over the shared engine. <b>Client-neutral:</b>
 * takes plain data (the RuneLite plugin reads config and constructs it), never a client config type.
 * This is the shape every ported trainer takes.
 */
public final class WoodcuttingSlice implements GatherLoop.Spec {

    private final Pos spot;
    private final String treeName;
    private final int logId;
    private final boolean bankWhenFull;
    private final GatherLoop loop;

    public WoodcuttingSlice(GameApi game, int spotX, int spotY, String treeName, int logId, boolean bankWhenFull) {
        this.spot = new Pos(spotX, spotY, 0);
        this.treeName = treeName;
        this.logId = logId;
        this.bankWhenFull = bankWhenFull;
        this.loop = new GatherLoop(game, this);
    }

    public void tick() { loop.tick(); }

    @Override public Pos spot() { return spot; }
    @Override public String[] objectNames() { return new String[]{ treeName }; }
    @Override public String action() { return "Chop down"; }
    @Override public int productId() { return logId; }
    @Override public boolean bankWhenFull() { return bankWhenFull; }
}
