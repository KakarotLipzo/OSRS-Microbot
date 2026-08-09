package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * The 30-item gear tray shown in the Library skill-detail equipment loadout (design §2.4 item 3).
 * Pure data: each entry is an item id, the one {@link GearSlot} it equips into, and a display name.
 * Sprites are drawn from the running client via {@code new Item(id).getImage()} (same path the
 * consumables tiles use), so nothing is bundled — offline the tile falls back to a {@code #id} stub.
 *
 * <p>The set mirrors the handoff's {@code assets/gear/} bundle: a spread of skilling gear
 * (prospector, graceful, rune pickaxe/axe), melee (rune armour, scimitar, whip, defender, gloves),
 * ranged (crossbow, arrows, accumulator) and general jewellery (glory, fury, wealth, rings) so any
 * skill's loadout has sensible picks for every slot.
 */
public final class GearCatalogue {

    /** One tray item. */
    public static final class Gear {
        public final int id;
        public final GearSlot slot;
        public final String name;
        Gear(int id, GearSlot slot, String name) { this.id = id; this.slot = slot; this.name = name; }
    }

    private static final Gear[] ITEMS = {
        // Weapons
        new Gear(1333,  GearSlot.WEAPON, "Rune scimitar"),
        new Gear(4151,  GearSlot.WEAPON, "Abyssal whip"),
        new Gear(9185,  GearSlot.WEAPON, "Rune crossbow"),
        new Gear(1275,  GearSlot.WEAPON, "Rune pickaxe"),
        new Gear(6739,  GearSlot.WEAPON, "Dragon axe"),
        // Head
        new Gear(1163,  GearSlot.HEAD,   "Rune full helm"),
        new Gear(12013, GearSlot.HEAD,   "Prospector helmet"),
        new Gear(11850, GearSlot.HEAD,   "Graceful hood"),
        // Body
        new Gear(1127,  GearSlot.BODY,   "Rune platebody"),
        new Gear(11848, GearSlot.BODY,   "Graceful top"),
        new Gear(8839,  GearSlot.BODY,   "Void knight top"),
        // Legs
        new Gear(1079,  GearSlot.LEGS,   "Rune platelegs"),
        new Gear(11847, GearSlot.LEGS,   "Graceful legs"),
        // Shield
        new Gear(1201,  GearSlot.SHIELD, "Rune kiteshield"),
        new Gear(12954, GearSlot.SHIELD, "Dragon defender"),
        // Cape
        new Gear(6570,  GearSlot.CAPE,   "Fire cape"),
        new Gear(10499, GearSlot.CAPE,   "Ava's accumulator"),
        new Gear(11849, GearSlot.CAPE,   "Graceful cape"),
        // Neck
        new Gear(6585,  GearSlot.NECK,   "Amulet of fury"),
        new Gear(1712,  GearSlot.NECK,   "Amulet of glory"),
        new Gear(1731,  GearSlot.NECK,   "Amulet of power"),
        // Ammo
        new Gear(892,   GearSlot.AMMO,   "Rune arrow"),
        new Gear(9236,  GearSlot.AMMO,   "Adamant bolts"),
        // Hands
        new Gear(7462,  GearSlot.HANDS,  "Barrows gloves"),
        new Gear(1059,  GearSlot.HANDS,  "Leather gloves"),
        // Feet
        new Gear(11840, GearSlot.FEET,   "Dragon boots"),
        new Gear(11851, GearSlot.FEET,   "Graceful boots"),
        new Gear(3105,  GearSlot.FEET,   "Climbing boots"),
        // Ring
        new Gear(6737,  GearSlot.RING,   "Berserker ring"),
        new Gear(2572,  GearSlot.RING,   "Ring of wealth"),
    };

    public static Gear[] all() { return ITEMS; }

    /** The tray items that occupy {@code slot}, in catalogue order. */
    public static List<Gear> forSlot(GearSlot slot) {
        List<Gear> out = new ArrayList<>();
        for (Gear g : ITEMS) if (g.slot == slot) out.add(g);
        return out;
    }

    /** Catalogue lookup by item id, or null if the id isn't in the tray. */
    public static Gear byId(int id) {
        for (Gear g : ITEMS) if (g.id == id) return g;
        return null;
    }

    private GearCatalogue() { }
}
