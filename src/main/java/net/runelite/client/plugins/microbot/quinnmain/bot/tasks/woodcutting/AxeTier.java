package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.woodcutting;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

/**
 * The standard hatchets, worst → best (enum ordinal = effectiveness). Each carries the
 * Woodcutting level needed to <b>use</b> it, the Attack level needed to <b>wield</b> it, whether
 * it is members-only, and a <b>fallback</b> price used only when the live GE price API is down.
 *
 * <p>You can chop with an axe in the inventory; wielding it (Attack req) just frees a slot.
 * Live buy offers come from {@link com.quinn.osrs.main.core.PriceLookup}; {@code fallbackPrice}
 * is a static safety net keyed to rough real values.
 */
public enum AxeTier {
    BRONZE ("Bronze axe",  1351,  1,  1, false,   200),
    IRON   ("Iron axe",    1349,  1,  1, false,   200),
    STEEL  ("Steel axe",   1353,  6,  5, false,   500),
    BLACK  ("Black axe",   1361,  6, 10, true,   1500),
    MITHRIL("Mithril axe", 1355, 21, 20, false,  1000),
    ADAMANT("Adamant axe", 1357, 31, 30, false,  1500),
    RUNE   ("Rune axe",    1359, 41, 40, false,  9000),
    DRAGON ("Dragon axe",  6739, 61, 60, true,  90000);

    public final String itemName;
    public final int itemId;
    public final int wcLevel;
    public final int attackLevel;
    public final boolean members;
    /** Fallback gp price used only if the live price API is unavailable. */
    public final int fallbackPrice;

    AxeTier(String itemName, int itemId, int wcLevel, int attackLevel, boolean members, int fallbackPrice) {
        this.itemName = itemName;
        this.itemId = itemId;
        this.wcLevel = wcLevel;
        this.attackLevel = attackLevel;
        this.members = members;
        this.fallbackPrice = fallbackPrice;
    }

    /** True if the given real Woodcutting level + membership allow chopping with this axe. */
    public boolean usableAt(int wcLvl, boolean membersAccount) {
        return wcLvl >= this.wcLevel && (membersAccount || !this.members);
    }

    /** True if the given Attack level allows wielding this axe (to free the inventory slot). */
    public boolean wieldableAt(int attackLvl) {
        return attackLvl >= this.attackLevel;
    }

    public static AxeTier byName(String name) {
        if (name == null) return null;
        for (AxeTier a : values()) {
            if (a.itemName.equalsIgnoreCase(name)) return a;
        }
        return null;
    }
}
