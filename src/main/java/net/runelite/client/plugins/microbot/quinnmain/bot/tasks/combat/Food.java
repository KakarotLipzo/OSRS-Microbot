package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.combat;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

/**
 * Cooked F2P foods the melee trainer eats, with the (cooked) item ID, how much it heals, and a
 * static GE fallback price (used when the live price API is down). Ordered ascending by heal so the
 * trainer can pick "the biggest heal that doesn't massively over-heal the current max HP" when
 * buying, and "the best food I actually have" when eating.
 *
 * <p>These are exactly the fish the Cooking trainer produces (shrimp → swordfish), so combat eats
 * what cooking banks; when the bank is empty it GE-buys an HP-appropriate one instead.
 */
public enum Food {
    SHRIMPS  ("Shrimps",   315, 3,  20),
    SARDINE  ("Sardine",   325, 4,  25),
    HERRING  ("Herring",   347, 5,  30),
    MACKEREL ("Mackerel",  355, 6,  35),
    TROUT    ("Trout",     333, 7,  40),
    COD      ("Cod",       339, 7,  45),
    PIKE     ("Pike",      343, 8,  60),
    SALMON   ("Salmon",    329, 9,  80),
    TUNA     ("Tuna",      361, 10, 90),
    LOBSTER  ("Lobster",   379, 12, 150),
    BASS     ("Bass",      365, 13, 220),
    SWORDFISH("Swordfish", 373, 14, 300);

    public final String name;
    public final int id;
    public final int heal;
    public final int gePrice;

    Food(String name, int id, int heal, int gePrice) {
        this.name = name;
        this.id = id;
        this.heal = heal;
        this.gePrice = gePrice;
    }
}
