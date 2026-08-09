package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Game;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;

/**
 * Shared movement helper. Ported to the facade: walking + banking now go through {@link GameApi}
 * (which on Microbot uses Rs2Walker's pathfinding minimap walking). Call once per loop until
 * {@link #arrived} is true.
 */
public final class Nav {

    private Nav() {}

    private static GameApi g() { return Game.api(); }

    /** Issue one move toward {@code dest} and return immediately (the adapter walks continuously). */
    public static void walkTo(Pos dest) {
        GameApi a = g();
        if (a == null || dest == null) return;
        try { a.walkTo(dest); } catch (Throwable ignored) { }
    }

    public static boolean isMoving() {
        GameApi a = g();
        return a != null && a.isMoving();
    }

    public static boolean arrived(Pos dest, int within) {
        GameApi a = g();
        return a != null && dest != null && a.arrived(dest, within);
    }

    /**
     * Walk to a bank and open it. Returns true once the bank interface is open; false while travelling.
     * With a specific {@link BankLoc}, walks to its centre first; null = nearest bank.
     */
    public static boolean openBank(BankLoc loc) {
        GameApi a = g();
        if (a == null) return false;
        if (a.bankIsOpen()) return true;
        if (loc != null && loc.center() != null && !a.arrived(loc.center(), 6)) {
            a.walkTo(loc.center());
            return false;
        }
        a.openNearestBank();
        a.waitUntil(a::bankIsOpen, 4000);
        return a.bankIsOpen();
    }
}
