package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.smithing;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

import net.runelite.client.plugins.microbot.quinnmain.bot.core.Aggression;


/**
 * A place to hammer bars into items: an anvil anchor plus the bank paired with it. Modelled on
 * {@link com.quinn.osrs.main.tasks.mining.MiningArea} so pinning another verified anvil-by-a-bank is
 * a one-line row.
 *
 * <p><b>This table is only the guaranteed travel fallback, not a whitelist.</b> {@link SmithingTask}
 * smiths at <i>any</i> anvil it already finds itself standing next to — wherever the mine/smelt
 * pipeline left it — and banks at the nearest bank. A listed site is only walked to when no anvil is
 * already in reach. So Smithing is not limited to the anvils here.
 *
 * <p>Only <b>Varrock West</b> is listed: at 10 tiles it's the closest F2P anvil-to-bank in the game
 * and the one verified live (50 bronze warhammers). Al Kharid and Lumbridge deliberately aren't here
 * — Al Kharid has no anvil by its bank, and Lumbridge's is ~80 tiles away. To pin another (Falador,
 * central Varrock's Horvik's, Edgeville …) read its anvil tile off the live
 * {@code [smith] smithing … at anvil #id @ tile} diagnostic and add a row — the opportunistic
 * "use whatever anvil is in reach" path already covers those in the meantime.
 */
public enum AnvilSite {

    //             label                 anvil anchor              bank                       aggroLevel (0 = safe)
    VARROCK_WEST  ("Varrock West anvil", new Pos(3188, 3425, 0), BankLoc.VARROCK_WEST, 0);

    public final String label;
    public final Pos anchor;
    public final BankLoc bank;
    /** Combat level of the worst aggressive mob at this anvil (0 = none). */
    public final int aggroLevel;

    AnvilSite(String label, Pos anchor, BankLoc bank, int aggroLevel) {
        this.label = label;
        this.anchor = anchor;
        this.bank = bank;
        this.aggroLevel = aggroLevel;
    }

    /** Safe for the local player right now — no aggressive mob here would attack at our combat level. */
    public boolean usableNow() { return Aggression.safeFrom(aggroLevel); }
}
