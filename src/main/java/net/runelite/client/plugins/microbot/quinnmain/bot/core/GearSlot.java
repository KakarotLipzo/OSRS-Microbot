package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

/**
 * The eleven OSRS worn-equipment slots, in the order the design's slot diagram lays them out
 * (§2.4 item 3). Each slot carries a short uppercase {@code label} used for the empty-cell caption
 * and its {@code row}/{@code col} position in the 3-wide diagram grid:
 *
 * <pre>
 *   [  – , HEAD ,  –  ]
 *   [ CAPE, NECK, AMMO]
 *   [ WEAPON, BODY, SHIELD]
 *   [  – , LEGS ,  –  ]
 *   [ HANDS, FEET, RING]
 * </pre>
 *
 * <p>Per-skill loadouts are stored in {@link ConfigStore} keyed {@code gear.<SKILL>.<SLOT>=itemId};
 * {@link GearCatalogue} maps each tray item to the one slot it occupies.
 */
public enum GearSlot {
    HEAD  ("HEAD",   0, 1),
    CAPE  ("CAPE",   1, 0),
    NECK  ("NECK",   1, 1),
    AMMO  ("AMMO",   1, 2),
    WEAPON("WEAPON", 2, 0),
    BODY  ("BODY",   2, 1),
    SHIELD("SHIELD", 2, 2),
    LEGS  ("LEGS",   3, 1),
    HANDS ("HANDS",  4, 0),
    FEET  ("FEET",   4, 1),
    RING  ("RING",   4, 2);

    public final String label;
    public final int row;
    public final int col;

    GearSlot(String label, int row, int col) {
        this.label = label;
        this.row = row;
        this.col = col;
    }

    /** Safe parse of a stored slot name; null if unknown (so a stale config key is ignored). */
    public static GearSlot parse(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        try { return GearSlot.valueOf(name.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
