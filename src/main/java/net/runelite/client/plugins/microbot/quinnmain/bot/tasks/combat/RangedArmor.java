package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.combat;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

import net.runelite.client.plugins.microbot.quinnmain.bot.core.GearSlot;


/**
 * F2P ranged armour by slot, worst → best within each slot (declaration order = effectiveness). Each
 * carries its worn {@link GearSlot}, item id, the skill + level needed to wear it, and a fallback GE
 * price. {@link RangedGear} keeps the best wearable piece per slot equipped, sourced own → bank → GE —
 * the ranged analogue of {@link ArmorPiece} (which is melee/Defence-gated).
 *
 * <p><b>F2P only.</b> The ladder tops out at studded leather + coif; green dragonhide (body/chaps/
 * vambraces, 40 Ranged) is <i>members</i>, so it's deliberately absent. Most pieces gate on Ranged
 * level; Hardleather body is the one Defence-gated piece (10 Defence). The bow (WEAPON) and arrows
 * (AMMO) are handled directly by {@link RangedGear}, so they're not repeated here.
 *
 * <p>Source: OSRS Wiki "Free-to-play Ranged training — Best-in-slot gear".
 */
public enum RangedArmor {
    // slot,          name,               id,    req skill,     lvl  price
    LEATHER_COWL  (GearSlot.HEAD,  "Leather cowl",      1167,  Sk.RANGED,  1,  20),
    COIF          (GearSlot.HEAD,  "Coif",              12507, Sk.RANGED,  20, 250),

    LEATHER_BODY  (GearSlot.BODY,  "Leather body",      1129,  Sk.RANGED,  1,  30),
    HARDLEATHER   (GearSlot.BODY,  "Hardleather body",  1131,  Sk.DEFENCE, 10, 60),
    STUDDED_BODY  (GearSlot.BODY,  "Studded body",      1133,  Sk.RANGED,  20, 250),

    LEATHER_CHAPS (GearSlot.LEGS,  "Leather chaps",     1095,  Sk.RANGED,  1,  25),
    STUDDED_CHAPS (GearSlot.LEGS,  "Studded chaps",     1097,  Sk.RANGED,  20, 250),

    LEATHER_VAMB  (GearSlot.HANDS, "Leather vambraces", 1063,  Sk.RANGED,  1,  20),
    LEATHER_BOOTS (GearSlot.FEET,  "Leather boots",     1061,  Sk.RANGED,  1,  20),
    BLACK_CAPE    (GearSlot.CAPE,  "Black cape",        1019,  Sk.RANGED,  1,  100),
    AMULET_POWER  (GearSlot.NECK,  "Amulet of power",   1731,  Sk.RANGED,  1,  350);

    public final GearSlot slot;
    public final String itemName;
    public final int itemId;
    public final Sk reqSkill;
    public final int reqLevel;
    public final int fallbackPrice;

    RangedArmor(GearSlot slot, String itemName, int itemId, Sk reqSkill, int reqLevel, int fallbackPrice) {
        this.slot = slot;
        this.itemName = itemName;
        this.itemId = itemId;
        this.reqSkill = reqSkill;
        this.reqLevel = reqLevel;
        this.fallbackPrice = fallbackPrice;
    }

    /** Wearable given the account's Ranged and Defence levels (the piece gates on one of them). */
    public boolean wearableAt(int rangedLvl, int defenceLvl) {
        int have = (reqSkill == Sk.DEFENCE) ? defenceLvl : rangedLvl;
        return have >= reqLevel;
    }
}
