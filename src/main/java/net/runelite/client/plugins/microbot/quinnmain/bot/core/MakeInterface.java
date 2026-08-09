package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Game;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;

/**
 * The client's shared "make" popup (cook/smelt/smith/craft). In OSRS-Main this was 200+ lines of
 * DreamBot widget-group-270 scraping (item-id -1 traps, varying action verbs, hidden quantity
 * buttons). On the facade that all collapses to three behavioural verbs on {@link GameApi}, so this
 * is now a thin neutral wrapper — the client-specific widget handling lives in the adapter.
 *
 * <p>Kept the same static API the callers (Gather, cooking/smithing trainers) already use.
 */
public final class MakeInterface {

    public static final int GROUP = 270;   // reference only

    private MakeInterface() { }

    private static GameApi g() { return Game.api(); }

    public static boolean isOpen() { GameApi a = g(); return a != null && a.makeScreenOpen(); }
    public static boolean isOpen(String productName) { GameApi a = g(); return a != null && a.makeScreenHas(productName); }

    /** Select All and click the product; returns a non-null token on success (callers only null-check). */
    public static String click(String productName) {
        GameApi a = g();
        return (a != null && a.clickMake(productName, 28)) ? "make" : null;
    }

    /** Quantity is handled inside {@link #click}; kept for source compatibility. */
    public static boolean selectQuantity(int desired) { return true; }

    public static String describe() { return "(make screen — facade)"; }
    public static String describeVisible(int limit) { return "(n/a on this client)"; }
}
