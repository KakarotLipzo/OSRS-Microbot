package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.fishing;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;


/**
 * A fishing method: the tool it needs, the fishing-spot action it uses, an optional consumable bait,
 * the level to start, an XP-rate rank for auto-progression, the spot NPC id, and the list of
 * {@link FishingArea}s it can be done at (first = default).
 *
 * <p>F2P max-XP ladder: SHRIMP (small net, lvl 1) → FLY (fly fishing rod + feathers, lvl 20). Fly
 * fishing trout/salmon is the fastest F2P XP to 99. Members methods slot in later, gated.
 *
 * <p><b>Location is per-area now, not per-method.</b> Each method lists its areas; the trainer walks
 * to the first <b>enabled</b> area that is combat-<b>safe right now</b> (the area picker gates which),
 * which replaces the old single primary/safe spot pair — a low-combat account falls through an
 * aggressive area (Draynor / Barbarian) to the next safe one, and a method with no currently-safe
 * enabled area is simply skipped.
 *
 * <p>Fishing spots are NPCs named "…Fishing spot"; the {@code action} (Net / Lure / Bait / Cage /
 * Harpoon) disambiguates which spot/method to use. The spot NPC id is a property of the fishing
 * <i>type</i> (net vs lure), so it's the same across that method's areas. Tools/bait match by item id.
 */
public enum FishMethod {
    //        tool               toolId action  lvl  members baitId  xpRank  spotNpcId  areas (first = default; extras opt-in via the picker)
    SHRIMP ("Small fishing net", 303,  "Net",   1,   false,  0,      30,     1525,      new FishingArea[]{
                FishingArea.DRAYNOR, FishingArea.LUMBRIDGE_SWAMP}),
    FLY    ("Fly fishing rod",   309,  "Lure",  20,  false,  314,    100,    1526,      new FishingArea[]{
                FishingArea.BARBARIAN_VILLAGE, FishingArea.LUMBRIDGE}),
    // Karamja tier (F2P): Musa Point only, reached via the Port Sarim boat — needsBoat. No F2P bank on
    // Karamja, so these pair best with fishing.powerDrop. Same spot (NPC 1522) offers Cage + Harpoon.
    LOBSTER("Lobster pot",       301,  "Cage",  40,  false,  0,      150,    1522,      true, new FishingArea[]{
                FishingArea.MUSA_POINT}),
    TUNA_SWORD("Harpoon",        311,  "Harpoon", 50, false, 0,      200,    1522,      true, new FishingArea[]{
                FishingArea.MUSA_POINT});

    public final String toolName;
    public final int toolId;
    public final String action;
    public final int fishLevel;
    public final boolean members;
    /** Consumable item id, or 0 if the method needs none (feathers 314 for fly, bait 313 for rod). */
    public final int baitId;
    public final int xpRank;
    /** Fishing-spot NPC id (spots under-report actions, so match by id first). 0 = match by name only. */
    public final int spotNpcId;
    /** Places this method can be fished (first = default). The area picker gates which are used. */
    public final FishingArea[] areas;
    /** True if the spot is only reachable by boat (Karamja/Musa Point) — gated by {@code fishing.karamja}. */
    public final boolean needsBoat;

    /** Mainland method (no boat needed). */
    FishMethod(String toolName, int toolId, String action, int fishLevel, boolean members,
               int baitId, int xpRank, int spotNpcId, FishingArea[] areas) {
        this(toolName, toolId, action, fishLevel, members, baitId, xpRank, spotNpcId, false, areas);
    }

    FishMethod(String toolName, int toolId, String action, int fishLevel, boolean members,
               int baitId, int xpRank, int spotNpcId, boolean needsBoat, FishingArea[] areas) {
        this.toolName = toolName;
        this.toolId = toolId;
        this.action = action;
        this.fishLevel = fishLevel;
        this.members = members;
        this.baitId = baitId;
        this.xpRank = xpRank;
        this.spotNpcId = spotNpcId;
        this.needsBoat = needsBoat;
        this.areas = areas;
    }

    public boolean needsBait() { return baitId > 0; }

    /** The default spot anchor (first area) — kept for callers that don't area-select. */
    public Pos defaultAnchor() { return areas[0].anchor; }

    /** Raw fish this method yields (for plan "get N items" targets). */
    public int[] fishIds() {
        switch (this) {
            case SHRIMP:    return new int[]{317, 321}; // raw shrimps + anchovies (the net catches both)
            case FLY:       return new int[]{335, 331}; // raw trout + raw salmon
            case LOBSTER:   return new int[]{377};      // raw lobster
            case TUNA_SWORD:return new int[]{359, 371}; // raw tuna + raw swordfish (harpoon catches both)
            default:        return new int[0];
        }
    }

    /** Parse a config value to a specific method, or null for "AUTO"/blank/unknown. */
    public static FishMethod parse(String s) {
        if (s == null) return null;
        try { return valueOf(s.trim().toUpperCase()); } catch (IllegalArgumentException e) { return null; }
    }
}
