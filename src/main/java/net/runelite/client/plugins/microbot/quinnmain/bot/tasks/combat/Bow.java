package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.combat;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

/**
 * F2P shortbows worst → best (declaration order = effectiveness), with the item ID, the Ranged level
 * to use it, and a fallback GE price. Shortbows are used over longbows for the faster attack speed
 * (better XP/hr). The bow's tier also caps the arrow tier (see {@link Arrow}); conveniently the level
 * thresholds line up (yew bow ↔ rune arrows at 40, etc.).
 */
public enum Bow {
    SHORTBOW ("Shortbow",        841, 1,  50),
    OAK      ("Oak shortbow",    843, 5,  100),
    WILLOW   ("Willow shortbow", 849, 20, 300),
    MAPLE    ("Maple shortbow",  851, 30, 600),
    YEW      ("Yew shortbow",    855, 40, 900);

    public final String itemName;
    public final int itemId;
    public final int rangedLevel;
    public final int fallbackPrice;

    Bow(String itemName, int itemId, int rangedLevel, int fallbackPrice) {
        this.itemName = itemName;
        this.itemId = itemId;
        this.rangedLevel = rangedLevel;
        this.fallbackPrice = fallbackPrice;
    }
}
