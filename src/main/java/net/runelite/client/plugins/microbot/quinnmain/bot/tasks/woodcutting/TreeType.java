package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.woodcutting;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

/**
 * Choppable tree types with the in-game areas each can be cut at (first = default), plus an XP-rate
 * rank used for auto-progression. Higher {@code xpRank} = better XP/hr.
 *
 * <p>F2P max-XP ladder falls out of the ranks: NORMAL (1) → OAK (15) → WILLOW (30+). Willows are
 * the fastest F2P Woodcutting XP all the way to 99, so they out-rank YEW (a value tree) and stay
 * selected. Members-only trees are gated and reserved for a future value/optimised mode.
 *
 * <p>Each tree lists its {@link WoodcuttingArea}s; the control-panel picker gates which are used, and
 * {@link WoodcuttingTask} chops/banks at the first area the user leaves enabled (preferring one that's
 * combat-safe). Normal/oak default to Draynor (trees beside the bank) with Varrock West as the safe
 * low-combat fallback; willow is Draynor-only, so a low-combat account skips it and drops to oak.
 */
public enum TreeType {
    //         objName        word       wcReq  members  xpRank  logId  areas (first = default; extras opt-in via the picker)
    NORMAL ("Tree",          "tree",     1,     false,   30,     1511,  WoodcuttingArea.DRAYNOR, WoodcuttingArea.VARROCK_WEST),
    OAK    ("Oak",           "oak",      15,    false,   60,     1521,  WoodcuttingArea.DRAYNOR, WoodcuttingArea.VARROCK_WEST),
    WILLOW ("Willow",        "willow",   30,    false,   100,    1519,  WoodcuttingArea.DRAYNOR),
    YEW    ("Yew",           "yew",      60,    false,   20,     1515,  WoodcuttingArea.EDGEVILLE),
    MAPLE  ("Maple tree",    "maple",    45,    true,    25,     1517,  WoodcuttingArea.SEERS);

    public final String objectName;
    public final String word;
    public final int wcLevel;
    public final boolean members;
    public final int xpRank;
    /** The log item this tree yields (for plan "get N items" targets). */
    public final int logId;
    /** Places this tree can be chopped (first = default). The area picker gates which of these are used. */
    public final WoodcuttingArea[] areas;

    TreeType(String objectName, String word, int wcLevel, boolean members, int xpRank,
             int logId, WoodcuttingArea... areas) {
        this.objectName = objectName;
        this.word = word;
        this.wcLevel = wcLevel;
        this.members = members;
        this.xpRank = xpRank;
        this.logId = logId;
        this.areas = areas;
    }

    /** Does a live game-object name match this tree type? NORMAL must match exactly so it never
     *  swallows "Oak tree"/"Willow tree" scenery; the rest match by keyword. */
    public boolean matches(String name) {
        if (name == null) return false;
        if (this == NORMAL) return name.equals("Tree");
        return name.toLowerCase().contains(word);
    }

    /** Parse a config value to a specific tree, or null for "AUTO"/blank/unknown (= best-XP auto-select). */
    public static TreeType parse(String s) {
        if (s == null) return null;
        try { return valueOf(s.trim().toUpperCase()); } catch (IllegalArgumentException e) { return null; }
    }
}
