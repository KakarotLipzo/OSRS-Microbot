package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.firemaking;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;


/**
 * A place to burn logs: a bank plus an open lane anchor beside it. The trainer lights a log at the
 * anchor and the game auto-walks the character along the lane burning the batch; {@code hopOffFire}
 * steps to any free tile when the line is blocked, so the anchor only needs open ground around it, not
 * a perfectly straight run. Selected by {@code firemaking.area} (default VARROCK_WEST).
 *
 * <p>Spots (OSRS Wiki "Free-to-play Firemaking training"):
 * <ul>
 *   <li><b>GRAND_EXCHANGE</b> — the cleanest: the bank is one tile from a big open courtyard.</li>
 *   <li><b>VARROCK_WEST</b> — the long east–west road right by the west bank (the original default).</li>
 *   <li><b>FALADOR</b> — the east bank / road through Falador; viable, listed for lower levels, but no
 *       long straight lane (the fire-hop handles the tighter space). Anchor is approximate.</li>
 * </ul>
 */
public enum FiremakingArea {
    VARROCK_WEST   ("Varrock West road", BankLoc.VARROCK_WEST,   new Pos(3212, 3428, 0)),
    GRAND_EXCHANGE ("Grand Exchange",    BankLoc.GRAND_EXCHANGE, new Pos(3162, 3483, 0)),
    FALADOR        ("Falador East",      BankLoc.FALADOR_EAST,   new Pos(3019, 3360, 0));

    public final String label;
    public final BankLoc bank;
    public final Pos anchor;

    FiremakingArea(String label, BankLoc bank, Pos anchor) {
        this.label = label;
        this.bank = bank;
        this.anchor = anchor;
    }

    /** Parse a config value to an area, defaulting to VARROCK_WEST for blank/unknown. */
    public static FiremakingArea parse(String s) {
        if (s != null) {
            try { return valueOf(s.trim().toUpperCase()); } catch (IllegalArgumentException ignored) { }
        }
        return VARROCK_WEST;
    }
}
