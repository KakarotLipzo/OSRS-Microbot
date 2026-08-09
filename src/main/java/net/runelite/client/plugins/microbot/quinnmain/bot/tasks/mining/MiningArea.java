package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.mining;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

import net.runelite.client.plugins.microbot.quinnmain.bot.core.Aggression;


/**
 * A real in-game place where an ore can be mined — a mine anchor plus the bank the trainer uses from
 * there. This is what makes the control panel's <b>area picker</b> honest: the user's area choices
 * actually change where {@link MiningTask} walks to mine and to bank.
 *
 * <p>F2P mines with a bank the {@code Nav} web-walker can reach:
 * <ul>
 *   <li><b>Varrock East</b> — the SE mine, rocks adjacent to the bank. The default, fastest cycle.</li>
 *   <li><b>Al Kharid</b> — copper/tin/iron in the desert mine; a longer bank run and wandering
 *       scorpions, but a genuine alternative when Varrock East is crowded.</li>
 *   <li><b>East Lumbridge Swamp</b> — copper/tin only; banks at Al Kharid (a long run).</li>
 *   <li><b>South-west Varrock</b> — tin/iron (no copper); banks at Varrock West, close by.</li>
 *   <li><b>Rimmington</b> — copper/tin/iron; banks at Falador West (a long run).</li>
 *   <li><b>Dwarven Mine</b> — copper/tin/iron underground; banks at Falador East up the ladder. The
 *       web-walker handles the ladder link, but it's the only non-surface mine — expect long bank runs.
 *       Anchor sits by the west mining spot where copper/tin/iron rocks cluster together.</li>
 * </ul>
 * Added on request (2026-07-26): the last four are deliberately slower/less efficient alternatives, so
 * they sit AFTER Varrock East + Al Kharid in each ore's area list and are only used when those are
 * excluded in the picker. Anchors were read off explv's map tiles against a game-coordinate grid (each
 * sits central to that mine's rock cluster / on the mining-site icon); the live {@code [mine] nearby
 * minable rocks} diagnostic still confirms the exact rock object IDs on first arrival.
 */
public enum MiningArea {

    //                    label                    anchor                    bank                       aggroLevel (worst aggressive mob nearby; 0 = safe)
    VARROCK_EAST         ("Varrock East",          new Pos(3285, 3365, 0), BankLoc.VARROCK_EAST,  0),
    AL_KHARID            ("Al Kharid mine",        new Pos(3299, 3311, 0), BankLoc.AL_KHARID,     3),   // desert scorpions
    EAST_LUMBRIDGE_SWAMP ("East Lumbridge Swamp",  new Pos(3224, 3148, 0), BankLoc.AL_KHARID,     0),
    SW_VARROCK           ("South-west Varrock",    new Pos(3176, 3371, 0), BankLoc.VARROCK_WEST,  0),
    RIMMINGTON           ("Rimmington mine",       new Pos(2977, 3242, 0), BankLoc.FALADOR_WEST,  0),
    DWARVEN_MINE         ("Dwarven Mine",          new Pos(3031, 9824, 0), BankLoc.FALADOR_EAST,  14);  // scorpions underground

    public final String label;
    public final Pos anchor;
    public final BankLoc bank;
    /** Combat level of the worst aggressive mob at this mine (0 = none). */
    public final int aggroLevel;

    MiningArea(String label, Pos anchor, BankLoc bank, int aggroLevel) {
        this.label = label;
        this.anchor = anchor;
        this.bank = bank;
        this.aggroLevel = aggroLevel;
    }

    /** Safe for the local player right now — no aggressive mob here that would attack at our combat level. */
    public boolean usableNow() { return Aggression.safeFrom(aggroLevel); }
}
