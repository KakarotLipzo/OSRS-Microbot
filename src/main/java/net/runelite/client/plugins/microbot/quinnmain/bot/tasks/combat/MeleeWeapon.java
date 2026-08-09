package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.combat;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

/**
 * F2P melee weapons grouped by {@link WeaponType}, worst → best within each type (declaration order
 * = effectiveness). Each carries the item ID, the Attack level to wield it, and a fallback GE price
 * (used only when the live price API is down). The gear manager equips the best weapon of the
 * configured type that the Attack level allows, sourcing it own → bank → GE.
 *
 * <ul>
 *   <li><b>STAB</b> — the metal sword line (strong stab lunge).</li>
 *   <li><b>SLASH</b> — the scimitar line (fast, best all-round F2P training DPS).</li>
 *   <li><b>CRUSH</b> — the mace line (one-handed crush, keeps the shield slot).</li>
 * </ul>
 */
public enum MeleeWeapon {
    // STAB — swords
    BRONZE_SWORD  (WeaponType.STAB,  "Bronze sword",   1277, 1,   200),
    IRON_SWORD    (WeaponType.STAB,  "Iron sword",     1279, 1,   200),
    STEEL_SWORD   (WeaponType.STAB,  "Steel sword",    1281, 5,   500),
    BLACK_SWORD   (WeaponType.STAB,  "Black sword",    1283, 10,  1500),
    MITHRIL_SWORD (WeaponType.STAB,  "Mithril sword",  1285, 20,  1000),
    ADAMANT_SWORD (WeaponType.STAB,  "Adamant sword",  1287, 30,  2500),
    RUNE_SWORD    (WeaponType.STAB,  "Rune sword",     1289, 40,  12000),

    // STAB — daggers (Tutorial Island hands out a bronze dagger, so this is the free starter weapon
    // every fresh account already owns; listed after swords so a STAB build still prefers the sword).
    BRONZE_DAGGER (WeaponType.STAB,  "Bronze dagger",  1205, 1,   100),
    IRON_DAGGER   (WeaponType.STAB,  "Iron dagger",    1203, 1,   100),
    STEEL_DAGGER  (WeaponType.STAB,  "Steel dagger",   1207, 5,   300),
    BLACK_DAGGER  (WeaponType.STAB,  "Black dagger",   1211, 10,  1000),
    MITHRIL_DAGGER(WeaponType.STAB,  "Mithril dagger", 1209, 20,  600),
    ADAMANT_DAGGER(WeaponType.STAB,  "Adamant dagger", 1213, 30,  1500),
    RUNE_DAGGER   (WeaponType.STAB,  "Rune dagger",    1215, 40,  9000),

    // SLASH — scimitars
    BRONZE_SCIM   (WeaponType.SLASH, "Bronze scimitar",  1321, 1,  200),
    IRON_SCIM     (WeaponType.SLASH, "Iron scimitar",    1323, 1,  200),
    STEEL_SCIM    (WeaponType.SLASH, "Steel scimitar",   1325, 5,  500),
    BLACK_SCIM    (WeaponType.SLASH, "Black scimitar",   1327, 10, 1500),
    MITHRIL_SCIM  (WeaponType.SLASH, "Mithril scimitar", 1329, 20, 1000),
    ADAMANT_SCIM  (WeaponType.SLASH, "Adamant scimitar", 1331, 30, 2500),
    RUNE_SCIM     (WeaponType.SLASH, "Rune scimitar",    1333, 40, 15000),

    // CRUSH — maces
    BRONZE_MACE   (WeaponType.CRUSH, "Bronze mace",   1422, 1,  200),
    IRON_MACE     (WeaponType.CRUSH, "Iron mace",     1420, 1,  200),
    STEEL_MACE    (WeaponType.CRUSH, "Steel mace",    1424, 5,  500),
    BLACK_MACE    (WeaponType.CRUSH, "Black mace",    1426, 10, 1500),
    MITHRIL_MACE  (WeaponType.CRUSH, "Mithril mace",  1428, 20, 1000),
    ADAMANT_MACE  (WeaponType.CRUSH, "Adamant mace",  1430, 30, 2500),
    RUNE_MACE     (WeaponType.CRUSH, "Rune mace",     1432, 40, 12000);

    public final WeaponType type;
    public final String itemName;
    public final int itemId;
    public final int attackLevel;
    public final int fallbackPrice;

    MeleeWeapon(WeaponType type, String itemName, int itemId, int attackLevel, int fallbackPrice) {
        this.type = type;
        this.itemName = itemName;
        this.itemId = itemId;
        this.attackLevel = attackLevel;
        this.fallbackPrice = fallbackPrice;
    }

    public boolean wieldableAt(int attackLvl) { return attackLvl >= this.attackLevel; }
}
