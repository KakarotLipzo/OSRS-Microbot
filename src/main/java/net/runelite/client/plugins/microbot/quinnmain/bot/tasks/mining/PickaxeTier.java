package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.mining;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

/**
 * The standard pickaxes, worst → best (enum ordinal = effectiveness). Each carries the Mining level
 * needed to <b>use</b> it, the Attack level needed to <b>wield</b> it, whether it is members-only,
 * and a fallback price used only if the live GE price API is down.
 *
 * <p>You can mine with a pickaxe in the inventory; wielding it (Attack req) just frees a slot.
 * Mirrors {@link com.quinn.osrs.main.tasks.woodcutting.AxeTier} but keyed to the Mining level.
 */
public enum PickaxeTier {
    BRONZE ("Bronze pickaxe",  1265,  1,  1, false,    200),
    IRON   ("Iron pickaxe",    1267,  1,  1, false,    200),
    STEEL  ("Steel pickaxe",   1269,  6,  5, false,    500),
    BLACK  ("Black pickaxe",  12297, 11, 10, true,    2000),
    MITHRIL("Mithril pickaxe", 1273, 21, 20, false,   1000),
    ADAMANT("Adamant pickaxe", 1271, 31, 30, false,   2000),
    RUNE   ("Rune pickaxe",    1275, 41, 40, false,   25000),
    DRAGON ("Dragon pickaxe", 11920, 61, 60, true,  2000000);

    public final String itemName;
    public final int itemId;
    public final int miningLevel;
    public final int attackLevel;
    public final boolean members;
    public final int fallbackPrice;

    PickaxeTier(String itemName, int itemId, int miningLevel, int attackLevel, boolean members, int fallbackPrice) {
        this.itemName = itemName;
        this.itemId = itemId;
        this.miningLevel = miningLevel;
        this.attackLevel = attackLevel;
        this.members = members;
        this.fallbackPrice = fallbackPrice;
    }

    public boolean usableAt(int miningLvl, boolean membersAccount) {
        return miningLvl >= this.miningLevel && (membersAccount || !this.members);
    }

    public boolean wieldableAt(int attackLvl) {
        return attackLvl >= this.attackLevel;
    }
}
