package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.combat;

import net.runelite.client.plugins.microbot.quinnmain.bot.core.Nav;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.TaskContext;
import net.runelite.client.plugins.microbot.quinnmain.game.Game;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi.GameObj;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Gets from the surface down into a {@link Dungeon} for underground combat spots. Ported to the
 * {@link GameApi} facade. Fail-safe: can't get inside within {@link #GIVE_UP_MS} → {@link #GIVE_UP} so
 * the combat engine benches the spot and drops a tier (never strands the bot underground).
 */
public final class DungeonNav {

    public static final int GIVE_UP = -1;
    private static final long GIVE_UP_MS = 90_000L;
    private static final String[] DESCEND_ACTIONS = {"Climb-down", "Climb down", "Climb-into", "Enter", "Go-down", "Open"};

    private final Map<Dungeon, Long> tryingSince = new EnumMap<>(Dungeon.class);

    private static GameApi g() { return Game.api(); }

    private GameObj closestObj(Predicate<GameObj> pred, int radius) {
        GameApi a = g(); if (a == null) return null;
        GameObj best = null; double bd = Double.MAX_VALUE;
        for (GameObj o : a.objectsWithin(radius)) {
            if (o == null) continue;
            try { if (!pred.test(o)) continue; } catch (Throwable t) { continue; }
            double d = o.distance();
            if (d < bd) { bd = d; best = o; }
        }
        return best;
    }

    public int reach(TaskContext ctx, Dungeon d) {
        if (d == null) return 0;
        GameApi a = g(); if (a == null) return 600;
        Pos me = a.playerPosition();
        if (me == null) return 600;

        if (d.inside(me)) { tryingSince.remove(d); return 0; }

        long now = System.currentTimeMillis();
        long since = tryingSince.getOrDefault(d, 0L);
        if (since == 0L) { tryingSince.put(d, now); since = now; }
        if (now - since > GIVE_UP_MS) {
            ctx.log("[dungeon] couldn't get into " + d.label + " within " + (GIVE_UP_MS / 1000) + "s — benching this spot and dropping a tier.");
            tryingSince.remove(d);
            return GIVE_UP;
        }

        if (me.distance(d.surfaceEntrance) > 4) { Nav.walkTo(d.surfaceEntrance); return 700; }

        GameObj ent = closestObj(o -> o.name() != null && o.position() != null
                && o.position().distance(d.surfaceEntrance) <= 6 && d.matchesEntrance(o.name())
                && descendAction(o) != null, 8);
        if (ent == null) { Nav.walkTo(d.surfaceEntrance); return 700; }
        String act = descendAction(ent);
        if (act != null && ent.interact(act)) {
            ctx.log("[dungeon] " + act + " " + ent.name() + " — entering " + d.label + ".");
            a.waitUntil(() -> { Pos p = a.playerPosition(); return p != null && d.inside(p); }, 6000);
        }
        return 800;
    }

    private static String descendAction(GameObj o) {
        try { for (String a : DESCEND_ACTIONS) if (o.hasAction(a)) return a; } catch (Throwable ignored) { }
        return null;
    }
}
