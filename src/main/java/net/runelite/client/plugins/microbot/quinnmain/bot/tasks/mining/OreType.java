package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.mining;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;


/**
 * Minable ores with the rock <b>object IDs</b> that yield them (in the game, all ore rocks are
 * named "Rocks" — only the ID distinguishes copper from iron), the ore item ID, the Mining level,
 * an XP-rate rank for auto-progression, and a fixed mine + bank.
 *
 * <p>F2P max-XP ladder: COPPER/TIN (lvl 1) → IRON (lvl 15). Iron is the fastest F2P Mining XP to 99.
 *
 * <p>Rock IDs (Varrock East SE mine): COPPER {10943, 11161}, TIN {11360} and IRON {11364, 11365}
 * are <b>confirmed live</b>; the remaining IDs in each set are best-effort spares for other mines.
 * If a forced ore finds nothing, the live `[mine] nearby minable rocks` diagnostic prints the real
 * name#id of every minable rock within 15 tiles — pin it here. The mine has all three ores and is
 * adjacent to Varrock East bank.
 */
public enum OreType {
    //        oreItem  oreId  lvl  members xpRank  rockIds                                areas (first = default; extras opt-in via the picker)
    COPPER ("Copper ore", 436, 1,  false,  31,   new int[]{10943, 11161, 11362, 11363},  MiningArea.VARROCK_EAST, MiningArea.AL_KHARID,
                                                                                          MiningArea.EAST_LUMBRIDGE_SWAMP, MiningArea.RIMMINGTON, MiningArea.DWARVEN_MINE),
    TIN    ("Tin ore",    438, 1,  false,  30,   new int[]{11360, 11361, 11597, 11598},  MiningArea.VARROCK_EAST, MiningArea.AL_KHARID,
                                                                                          MiningArea.EAST_LUMBRIDGE_SWAMP, MiningArea.SW_VARROCK, MiningArea.RIMMINGTON, MiningArea.DWARVEN_MINE),
    IRON   ("Iron ore",   440, 15, false,  100,  new int[]{11364, 11365},                MiningArea.VARROCK_EAST, MiningArea.AL_KHARID,
                                                                                          MiningArea.SW_VARROCK, MiningArea.RIMMINGTON, MiningArea.DWARVEN_MINE);

    public final String oreName;
    public final int oreId;
    public final int miningLevel;
    public final boolean members;
    public final int xpRank;
    public final int[] rockIds;
    /** Places this ore can be mined (first = default). The area picker gates which of these are used. */
    public final MiningArea[] areas;

    OreType(String oreName, int oreId, int miningLevel, boolean members, int xpRank,
            int[] rockIds, MiningArea... areas) {
        this.oreName = oreName;
        this.oreId = oreId;
        this.miningLevel = miningLevel;
        this.members = members;
        this.xpRank = xpRank;
        this.rockIds = rockIds;
        this.areas = areas;
    }

    /** The default mine anchor (first area) — kept for callers that don't area-select. */
    public Pos defaultAnchor() { return areas[0].anchor; }

    public boolean isRock(int objectId) {
        for (int id : rockIds) if (id == objectId) return true;
        return false;
    }

    /** Parse a config value to a specific ore, or null for "AUTO"/blank/unknown (= best-XP auto-select). */
    public static OreType parse(String s) {
        if (s == null) return null;
        try { return valueOf(s.trim().toUpperCase()); } catch (IllegalArgumentException e) { return null; }
    }
}
