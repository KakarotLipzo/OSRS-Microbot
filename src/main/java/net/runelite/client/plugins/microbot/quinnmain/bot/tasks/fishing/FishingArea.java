package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.fishing;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

import net.runelite.client.plugins.microbot.quinnmain.bot.core.Aggression;


/**
 * A real in-game place where a fishing method can be done — a shore anchor plus the bank the trainer
 * uses from there. This is what makes the control panel's <b>area picker</b> honest for Fishing, the
 * same way {@link com.quinn.osrs.main.tasks.mining.MiningArea} does for Mining: the user's area choices
 * actually change where {@link FishingTask} walks to fish and to bank.
 *
 * <p>Each {@link FishMethod} lists the areas it can be done at (first = default). The trainer fishes at
 * the first <b>enabled</b> area that is also <b>combat-safe right now</b> ({@link #usableNow()}), which
 * is what replaces the old single "primary vs safe" spot pair — a low-combat account naturally falls
 * through an aggressive area to the next safe one, and simply skips a method if none of its enabled
 * areas is safe yet.
 *
 * <p><b>F2P net (shrimp/anchovies) spots:</b>
 * <ul>
 *   <li><b>Draynor</b> — spot beside the bank, the efficient default; lvl-7 dark wizards among the
 *       willows (safe at combat ≥ 16). <i>Anchor confirmed live.</i></li>
 *   <li><b>Lumbridge Swamp</b> — safe (no aggressive mobs), bank at Lumbridge. <i>Anchor confirmed live.</i></li>
 * </ul>
 * <b>F2P lure (fly — trout/salmon) spots:</b>
 * <ul>
 *   <li><b>Barbarian Village</b> — the efficient default; lvl-10 aggressive barbarians throughout
 *       (safe at combat ≥ 22), bank at Edgeville. <i>Anchor confirmed live.</i></li>
 *   <li><b>Lumbridge</b> — River Lum by the castle, safe, bank at Lumbridge; gives a low-combat account
 *       a fly-fishing option it never had before. <i>Anchor is Quinn's explv read (3238,3253); the
 *       alternate (3240,3200) is the other candidate — the live {@code [fish] loaded fishing spots}
 *       diagnostic confirms which is right on first use. It's opt-in, so the verified Barbarian default
 *       is unaffected.</i></li>
 * </ul>
 *
 * <p>(Al Kharid net was intentionally left out per Quinn — the scorpion-ringed spot isn't worth it.)
 */
public enum FishingArea {

    //                    label                    anchor                     bank                       aggroLevel (worst aggressive mob; 0 = safe)
    DRAYNOR            ("Draynor Village",       new Pos(3086, 3230, 0), BankLoc.DRAYNOR,   Aggression.DRAYNOR_DARK_WIZARD),
    LUMBRIDGE_SWAMP    ("Lumbridge Swamp",       new Pos(3242, 3152, 0), BankLoc.LUMBRIDGE, 0),
    BARBARIAN_VILLAGE  ("Barbarian Village",     new Pos(3105, 3432, 0), BankLoc.EDGEVILLE, Aggression.BARBARIAN),
    LUMBRIDGE          ("Lumbridge (River Lum)", new Pos(3238, 3253, 0), BankLoc.LUMBRIDGE, 0),
    // Karamja lobster/tuna/swordfish dock — reached by the Port Sarim boat (gated by fishing.karamja). No
    // F2P bank on Karamja, so pair with power-drop; DRAYNOR is the nearest bank if banking is on.
    MUSA_POINT         ("Musa Point (Karamja)",  new Pos(2924, 3178, 0), BankLoc.DRAYNOR,  0);

    public final String label;
    public final Pos anchor;
    public final BankLoc bank;
    /** Combat level of the worst aggressive mob at this spot (0 = none). */
    public final int aggroLevel;

    FishingArea(String label, Pos anchor, BankLoc bank, int aggroLevel) {
        this.label = label;
        this.anchor = anchor;
        this.bank = bank;
        this.aggroLevel = aggroLevel;
    }

    /** Safe for the local player right now — no aggressive mob here that would attack at our combat level. */
    public boolean usableNow() { return Aggression.safeFrom(aggroLevel); }
}
