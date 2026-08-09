package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.smithing;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Items we can hammer out of a bar at an anvil (the second half of Smithing, after smelting).
 *
 * <p><b>No product item IDs.</b> {@link com.quinn.osrs.main.core.MakeInterface} picks the button by
 * <b>name</b> ("Bronze platebody"), because every button in that interface carries item id -1. So a
 * product only needs its name, the bar it eats, how many, and the level — which removes ~130
 * hand-transcribed item IDs that nothing would ever have read and that would fail silently if wrong.
 *
 * <p><b>Generated, not transcribed.</b> OSRS smithing levels are a fixed grid: every tier offers the
 * same 17 shapes at the same offsets from the tier's base level (dagger at +0, mace at +2, platebody
 * at +18 …). So the table is the cross product of {@link Tier} × {@link Shape} rather than 102 hand
 * written rows — one typo in a shared offset is visible and fixable, where one typo in row 71 is not.
 * Bronze uses base 0 with a level floor of 1, which is what makes bronze dagger <i>and</i> axe both
 * level 1 while every richer tier separates them.
 *
 * <p><b>What gets picked:</b> Smithing XP is per bar and set by the bar's tier, so the shape doesn't
 * change XP rate — but it does change how many interface trips a batch takes. We therefore prefer the
 * highest bar tier available, then the shape eating the most bars (platebody 5 → platelegs 3 →
 * dagger 1), so an inventory clears in the fewest actions.
 *
 * <p>Tiers above steel need coal (or are members-gated for smelting), so they simply never get picked
 * while no such bars reach the bank — they cost nothing to define and mean the anvil keeps working if
 * bars are ever bought or mined.
 */
public final class AnvilProduct {

    /** A bar tier: the bar it consumes and the Smithing level its cheapest shape starts at. */
    private enum Tier {
        BRONZE  ("Bronze",   2349, 0),   // base 0 + floor(1): bronze dagger AND axe are both level 1
        IRON    ("Iron",     2351, 15),
        STEEL   ("Steel",    2353, 30),
        MITHRIL ("Mithril",  2359, 50),
        ADAMANT ("Adamant",  2361, 70),
        RUNE    ("Rune",     2363, 85);

        final String label; final int barId; final int base;
        Tier(String label, int barId, int base) { this.label = label; this.barId = barId; this.base = base; }
    }

    /** A shape: its level offset within every tier, and how many bars it eats. */
    private enum Shape {
        DAGGER     ("dagger",     0, 1),
        AXE        ("axe",        1, 1),
        MACE       ("mace",       2, 1),
        MED_HELM   ("med helm",   3, 1),
        SWORD      ("sword",      4, 1),
        SCIMITAR   ("scimitar",   5, 2),
        LONGSWORD  ("longsword",  6, 2),
        FULL_HELM  ("full helm",  7, 2),
        SQ_SHIELD  ("sq shield",  8, 2),
        WARHAMMER  ("warhammer",  9, 3),
        BATTLEAXE  ("battleaxe", 10, 3),
        CHAINBODY  ("chainbody", 11, 3),
        KITESHIELD ("kiteshield",12, 3),
        CLAWS      ("claws",     13, 2),
        TWO_H      ("2h sword",  14, 3),
        PLATELEGS  ("platelegs", 16, 3),
        PLATESKIRT ("plateskirt",16, 3),
        PLATEBODY  ("platebody", 18, 5);

        final String label; final int offset; final int bars;
        Shape(String label, int offset, int bars) { this.label = label; this.offset = offset; this.bars = bars; }
    }

    /** Product name exactly as the make interface labels it, e.g. "Bronze platebody". */
    public final String productName;
    public final int barItemId;
    public final int barsPerItem;
    public final int smithLevel;
    public final int tier;        // 1 = bronze … 6 = rune
    /** Shape enum name, e.g. "PLATEBODY" — the stable key the control panel's picker stores. */
    public final String shapeKey;
    /** Shape label, e.g. "platebody". */
    public final String shapeLabel;

    private AnvilProduct(String productName, int barItemId, int barsPerItem, int smithLevel, int tier,
                         String shapeKey, String shapeLabel) {
        this.productName = productName;
        this.barItemId = barItemId;
        this.barsPerItem = barsPerItem;
        this.smithLevel = smithLevel;
        this.tier = tier;
        this.shapeKey = shapeKey;
        this.shapeLabel = shapeLabel;
    }

    /** Picker value meaning "let the bot choose" (the highest tier, most-bars shape). */
    public static final String AUTO = "AUTO";

    /** The 17 shape keys, in level order — the control panel's "Anvil item" dropdown. */
    public static String[] shapeKeys() {
        List<String> out = new ArrayList<>();
        out.add(AUTO);
        for (Shape s : Shape.values()) {
            if (s == Shape.PLATESKIRT) continue;   // duplicate of platelegs, see build()
            out.add(s.name());
        }
        return out.toArray(new String[0]);
    }

    /** "PLATEBODY" → "platebody" for display; {@link #AUTO} passes through. */
    public static String shapeLabelOf(String key) {
        if (key == null || AUTO.equals(key)) return AUTO;
        for (Shape s : Shape.values()) if (s.name().equals(key)) return s.label;
        return key;
    }

    private static final AnvilProduct[] ALL = build();

    private static AnvilProduct[] build() {
        List<AnvilProduct> out = new ArrayList<>();
        for (Tier t : Tier.values()) {
            for (Shape s : Shape.values()) {
                // Plateskirt is the same level/bars as platelegs; keeping only one avoids a coin-flip
                // between two identical options when ranking.
                if (s == Shape.PLATESKIRT) continue;
                // Clamped at both ends. Bronze's base of 0 floors to 1 (dagger and axe share level 1);
                // rune's top compresses against the 99 cap, which is why rune 2h sword, platelegs and
                // platebody are all level 99 rather than 99/101/103.
                int level = Math.min(99, Math.max(1, t.base + s.offset));
                out.add(new AnvilProduct(t.label + " " + s.label, t.barId, s.bars, level,
                        t.ordinal() + 1, s.name(), s.label));
            }
        }
        return out.toArray(new AnvilProduct[0]);
    }

    /** Every product, in tier order. Named {@code values()} so call sites read like the old enum. */
    public static AnvilProduct[] values() { return ALL; }

    /** Higher = preferred: best bar tier first, then the shape eating the most bars per action. */
    public int rank() { return tier * 100 + barsPerItem; }

    @Override public String toString() { return productName; }
}
