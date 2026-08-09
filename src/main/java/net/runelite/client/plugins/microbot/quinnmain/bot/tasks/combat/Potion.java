package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.combat;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;


/**
 * Combat-relevant potions, F2P and members. Each lists its four dose item IDs (4→1), which skills it
 * boosts (or PRAYER for restores), whether it's members-only, its category, and a fallback GE price
 * (for the 4-dose) used when the live price API is down.
 *
 * <ul>
 *   <li><b>COMBAT</b> — stat boosts. F2P: attack/strength/defence/combat. Members: the super versions
 *       + super combat (boosts all three at once).</li>
 *   <li><b>PRAYER_RESTORE</b> — restore prayer points mid-fight. Members only (F2P restores at an
 *       altar, which we don't do), so on F2P these simply never appear/are used.</li>
 * </ul>
 */
public enum Potion {
    // COMBAT — F2P
    ATTACK       ("Attack potion",   Category.COMBAT, false, new int[]{2428, 121, 123, 125},   30,    Sk.ATTACK),
    STRENGTH     ("Strength potion", Category.COMBAT, false, new int[]{113, 115, 117, 119},    30,    Sk.STRENGTH),
    DEFENCE      ("Defence potion",  Category.COMBAT, false, new int[]{2432, 133, 135, 137},   30,    Sk.DEFENCE),
    COMBAT       ("Combat potion",   Category.COMBAT, false, new int[]{9739, 9741, 9743, 9745},200,   Sk.ATTACK, Sk.STRENGTH),
    // COMBAT — members (super)
    SUPER_ATTACK ("Super attack",    Category.COMBAT, true,  new int[]{2436, 145, 147, 149},   150,   Sk.ATTACK),
    SUPER_STRENGTH("Super strength", Category.COMBAT, true,  new int[]{2440, 157, 159, 161},   200,   Sk.STRENGTH),
    SUPER_DEFENCE("Super defence",   Category.COMBAT, true,  new int[]{2442, 163, 165, 167},   200,   Sk.DEFENCE),
    SUPER_COMBAT ("Super combat potion", Category.COMBAT, true, new int[]{12695, 12697, 12699, 12701}, 10000, Sk.ATTACK, Sk.STRENGTH, Sk.DEFENCE),
    // PRAYER_RESTORE — members
    PRAYER_POTION("Prayer potion",   Category.PRAYER_RESTORE, true, new int[]{2434, 139, 141, 143}, 1000,  Sk.PRAYER),
    SUPER_RESTORE("Super restore",   Category.PRAYER_RESTORE, true, new int[]{3024, 3026, 3028, 3030}, 10000, Sk.PRAYER);

    public enum Category { COMBAT, PRAYER_RESTORE }

    public final String name;
    public final Category category;
    public final boolean members;
    /** Dose item IDs, index 0 = (4) … index 3 = (1). */
    public final int[] doseIds;
    public final int fallbackPrice; // for the 4-dose
    public final Sk[] boosts;

    Potion(String name, Category category, boolean members, int[] doseIds, int fallbackPrice, Sk... boosts) {
        this.name = name;
        this.category = category;
        this.members = members;
        this.doseIds = doseIds;
        this.fallbackPrice = fallbackPrice;
        this.boosts = boosts;
    }

    public boolean boosts(Sk s) {
        for (Sk b : boosts) if (b == s) return true;
        return false;
    }
}
