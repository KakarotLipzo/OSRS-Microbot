package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Pos;

/**
 * Neutral bank-location enum, replacing DreamBot's {@code BankLocation}. Each carries a centre tile so
 * the adapter can walk to it; on Microbot the adapter may instead defer to {@code Rs2Bank}'s nearest-bank
 * logic. Coords are best-known F2P bank centres — refine against live logs during validation.
 */
public enum BankLoc {
    AL_KHARID      (new Pos(3269, 3167, 0)),
    DRAYNOR        (new Pos(3092, 3243, 0)),
    EDGEVILLE      (new Pos(3094, 3491, 0)),
    FALADOR_EAST   (new Pos(3013, 3356, 0)),
    FALADOR_WEST   (new Pos(2946, 3368, 0)),
    GRAND_EXCHANGE (new Pos(3165, 3487, 0)),
    LUMBRIDGE      (new Pos(3208, 3220, 2)),
    SEERS          (new Pos(2725, 3491, 0)),
    VARROCK_EAST   (new Pos(3253, 3420, 0)),
    VARROCK_WEST   (new Pos(3185, 3436, 0));

    public final Pos center;
    BankLoc(Pos center) { this.center = center; }
    public Pos center() { return center; }
}
