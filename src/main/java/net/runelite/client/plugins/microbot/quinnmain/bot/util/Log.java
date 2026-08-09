package net.runelite.client.plugins.microbot.quinnmain.bot.util;

/**
 * Neutral logger. Replaces DreamBot's {@code org.dreambot.api.utilities.Logger} across the ported
 * logic so no logic file imports a client type. The adapter can redirect this to Microbot's
 * {@code Microbot.log} at the boundary; by default it prints to stdout (fine for local compile + dev).
 */
public final class Log {

    private Log() {}

    /** Optional sink — the plugin sets this to route through {@code Microbot.log}. */
    public interface Sink { void log(String msg); }

    private static volatile Sink sink;

    public static void setSink(Sink s) { sink = s; }

    public static void log(String msg) {
        Sink s = sink;
        if (s != null) { try { s.log(msg); return; } catch (Throwable ignored) { } }
        System.out.println(msg);
    }

    public static void log(String msg, Throwable t) {
        log(msg + " — " + t);
    }

    // Aliases so ported code that used DreamBot Logger.info/warn/error keeps working.
    public static void info(String msg)  { log(msg); }
    public static void warn(String msg)  { log(msg); }
    public static void error(String msg) { log(msg); }
    public static void error(String msg, Throwable t) { log(msg, t); }
}
