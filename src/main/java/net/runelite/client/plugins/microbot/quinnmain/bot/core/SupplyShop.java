package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;


/**
 * F2P NPC shops that sell skilling tools/consumables, so {@link SupplyBuy} can buy a missing material
 * from a shop (cheap, no GE buy-limit or 4-minute fill wait, and it works even while a young account is
 * still GE-<i>restricted</i>) before falling back to the Grand Exchange.
 *
 * <p>Surface, F2P shops only (Nav web-walks the surface). One catalogued item → one shop:
 * <ul>
 *   <li><b>Gerrant's Fishy Business</b> — Port Sarim: fishing nets/rods + bait.</li>
 *   <li><b>Dommik's Crafting Store</b> — Al Kharid: needle, thread, chisel, moulds. (Behind the 10gp
 *       toll gate; if it can't be reached {@link SupplyBuy} times out and uses the GE instead.)</li>
 *   <li><b>Bob's Brilliant Axes</b> — Lumbridge: bronze–steel axes and low pickaxes.</li>
 * </ul>
 * Feathers (fly-fishing bait) have no reliable F2P shop, so they're intentionally absent — the caller's
 * GE fallback handles them.
 */
public enum SupplyShop {

    //                  item id  item name             shop NPC   shop tile
    FISHING_BAIT      (313,  "Fishing bait",      "Gerrant", new Pos(3014, 3223, 0)),
    SMALL_FISHING_NET (303,  "Small fishing net", "Gerrant", new Pos(3014, 3223, 0)),
    FISHING_ROD       (307,  "Fishing rod",       "Gerrant", new Pos(3014, 3223, 0)),
    FLY_FISHING_ROD   (309,  "Fly fishing rod",   "Gerrant", new Pos(3014, 3223, 0)),
    LOBSTER_POT       (301,  "Lobster pot",       "Gerrant", new Pos(3014, 3223, 0)),
    HARPOON           (311,  "Harpoon",           "Gerrant", new Pos(3014, 3223, 0)),

    NEEDLE            (1733, "Needle",            "Dommik",  new Pos(3080, 3187, 0)),
    THREAD            (1734, "Thread",            "Dommik",  new Pos(3080, 3187, 0)),
    CHISEL            (1755, "Chisel",            "Dommik",  new Pos(3080, 3187, 0)),
    RING_MOULD        (1592, "Ring mould",        "Dommik",  new Pos(3080, 3187, 0)),

    BRONZE_AXE        (1351, "Bronze axe",        "Bob",     new Pos(3231, 3203, 0)),
    IRON_AXE          (1349, "Iron axe",          "Bob",     new Pos(3231, 3203, 0)),
    STEEL_AXE         (1353, "Steel axe",         "Bob",     new Pos(3231, 3203, 0)),
    BRONZE_PICKAXE    (1265, "Bronze pickaxe",    "Bob",     new Pos(3231, 3203, 0)),
    IRON_PICKAXE      (1267, "Iron pickaxe",      "Bob",     new Pos(3231, 3203, 0));

    public final int itemId;
    public final String itemName;
    public final String npc;
    public final Pos tile;

    SupplyShop(int itemId, String itemName, String npc, Pos tile) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.npc = npc;
        this.tile = tile;
    }

    /** The shop that sells {@code itemId}, or null if none is catalogued (caller uses the GE). */
    public static SupplyShop forItem(int itemId) {
        for (SupplyShop s : values()) if (s.itemId == itemId) return s;
        return null;
    }
}
