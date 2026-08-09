package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.combat;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

/**
 * F2P metal arrows worst → best (declaration order = effectiveness), with the item ID, the Ranged
 * level to fire them, and a fallback GE price (per arrow). The trainer buys/uses the best arrow the
 * Ranged level <b>and</b> the equipped {@link Bow}'s tier allow, in bulk from the GE.
 */
public enum Arrow {
    BRONZE  ("Bronze arrow",  882, 1,  5),
    IRON    ("Iron arrow",    884, 1,  8),
    STEEL   ("Steel arrow",   886, 5,  15),
    MITHRIL ("Mithril arrow", 888, 20, 30),
    ADAMANT ("Adamant arrow", 890, 30, 80),
    RUNE    ("Rune arrow",    892, 40, 150);

    public final String itemName;
    public final int itemId;
    public final int rangedLevel;
    public final int fallbackPrice;

    Arrow(String itemName, int itemId, int rangedLevel, int fallbackPrice) {
        this.itemName = itemName;
        this.itemId = itemId;
        this.rangedLevel = rangedLevel;
        this.fallbackPrice = fallbackPrice;
    }
}
