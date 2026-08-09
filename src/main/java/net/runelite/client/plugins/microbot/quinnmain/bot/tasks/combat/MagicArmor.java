package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.combat;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

import net.runelite.client.plugins.microbot.quinnmain.bot.core.GearSlot;


/**
 * F2P magic equipment by slot (magic-attack bonus gear). {@link MagicGear} keeps the best wearable
 * piece per slot equipped, sourced own → bank → GE — the magic analogue of {@link RangedArmor}. The
 * staff (WEAPON) and secondary runes (AMMO/supplies) are handled directly by {@link MagicGear}, so
 * they're not repeated here.
 *
 * <p><b>F2P only.</b> None of these carry a level requirement in free-to-play. Legs are intentionally
 * omitted — F2P has no iconic magic-bonus leg piece, so that slot is left for a user loadout rather
 * than filling it with non-magic armour. Members robes (e.g. Zamorak/god robes beyond the F2P set,
 * mystic) are excluded.
 *
 * <p>Source: OSRS Wiki "Free-to-play Magic training — Equipment choice".
 */
public enum MagicArmor {
    // slot,          name,               id,    req skill,   lvl  price
    WIZARD_HAT    (GearSlot.HEAD,  "Wizard hat",        579,  Sk.MAGIC, 1, 30),
    WIZARD_ROBE   (GearSlot.BODY,  "Wizard robe",       577,  Sk.MAGIC, 1, 40),
    AMULET_MAGIC  (GearSlot.NECK,  "Amulet of magic",   1727, Sk.MAGIC, 1, 300),
    BLACK_CAPE    (GearSlot.CAPE,  "Black cape",        1019, Sk.MAGIC, 1, 100),
    LEATHER_VAMB  (GearSlot.HANDS, "Leather vambraces", 1063, Sk.MAGIC, 1, 20),
    LEATHER_BOOTS (GearSlot.FEET,  "Leather boots",     1061, Sk.MAGIC, 1, 20);

    public final GearSlot slot;
    public final String itemName;
    public final int itemId;
    public final Sk reqSkill;
    public final int reqLevel;
    public final int fallbackPrice;

    MagicArmor(GearSlot slot, String itemName, int itemId, Sk reqSkill, int reqLevel, int fallbackPrice) {
        this.slot = slot;
        this.itemName = itemName;
        this.itemId = itemId;
        this.reqSkill = reqSkill;
        this.reqLevel = reqLevel;
        this.fallbackPrice = fallbackPrice;
    }

    /** Wearable given the account's Magic and Defence levels (F2P magic gear has no requirement). */
    public boolean wearableAt(int magicLvl, int defenceLvl) {
        int have = (reqSkill == Sk.DEFENCE) ? defenceLvl : magicLvl;
        return have >= reqLevel;
    }
}
