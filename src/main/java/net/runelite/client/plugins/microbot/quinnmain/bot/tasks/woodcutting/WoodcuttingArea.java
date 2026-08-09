package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.woodcutting;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

import net.runelite.client.plugins.microbot.quinnmain.bot.core.Aggression;


/**
 * A real in-game place where a tree type can be chopped — a chop anchor plus the bank the trainer uses
 * from there, and the worst aggressive-mob level nearby (so a low-combat account avoids it). This is the
 * Woodcutting counterpart to {@link com.quinn.osrs.main.tasks.mining.MiningArea}: it makes the control
 * panel's <b>area picker</b> honest — the user's choices actually change where {@link WoodcuttingTask}
 * walks to chop and to bank.
 *
 * <p>Anchors were read off explv's map tiles against a game-coordinate grid:
 * <ul>
 *   <li><b>Draynor Village</b> — trees (normal/oak) and willows sit right beside the bank; the fastest
 *       cycle and the default for every tree it offers. Tagged with the Draynor dark-wizard aggro that
 *       the fishing/willow spots already carry, so a low-combat account falls back to a safe area.</li>
 *   <li><b>Varrock West</b> — the classic normal/oak tree line SW of the bank, but it's ~39 tiles from
 *       that bank (a long cycle). Kept as the <b>safe</b> normal/oak option for low-combat accounts that
 *       can't yet use Draynor.</li>
 *   <li><b>Edgeville</b> — yews just south of the bank.</li>
 *   <li><b>Seers' Village</b> — members maples near the bank (best-effort anchor; members-gated).</li>
 * </ul>
 */
public enum WoodcuttingArea {

    VARROCK_WEST ("Varrock West",    new Pos(3160, 3411, 0), BankLoc.VARROCK_WEST, 0),
    DRAYNOR      ("Draynor Village", new Pos(3089, 3235, 0), BankLoc.DRAYNOR,      Aggression.DRAYNOR_DARK_WIZARD),
    EDGEVILLE    ("Edgeville",       new Pos(3087, 3481, 0), BankLoc.EDGEVILLE,    0),
    SEERS        ("Seers' Village",  new Pos(2728, 3496, 0), BankLoc.SEERS,        0);

    public final String label;
    public final Pos anchor;
    public final BankLoc bank;
    /** Combat level of the worst aggressive mob at this spot (0 = none). */
    public final int aggroLevel;

    WoodcuttingArea(String label, Pos anchor, BankLoc bank, int aggroLevel) {
        this.label = label;
        this.anchor = anchor;
        this.bank = bank;
        this.aggroLevel = aggroLevel;
    }

    /** False while this spot's aggressive mobs would still attack us (too low combat) — skip it for now. */
    public boolean usableNow() { return Aggression.safeFrom(aggroLevel); }
}
