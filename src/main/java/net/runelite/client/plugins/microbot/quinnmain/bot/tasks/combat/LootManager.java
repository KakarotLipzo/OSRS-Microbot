package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.combat;

import net.runelite.client.plugins.microbot.quinnmain.bot.core.Nav;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.PriceLookup;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.TaskContext;
import net.runelite.client.plugins.microbot.quinnmain.game.Game;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi.GroundItem;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi.Npc;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;

import java.util.Map;

/**
 * Combat loot handling with a wealth gate + kill-site ownership (only our own drops). Ported to the
 * {@link GameApi} facade. Buries bones for passive Prayer, always takes coins/stackables, applies the
 * value threshold to non-stackables, and banks when the inventory fills (keeping food).
 *
 * <p>NB: {@code groundItemsWithin} in the adapter is a fork-verify TODO (Rs2GroundItem model) — the loot
 * LOGIC here is complete; once that adapter method returns real items, looting is live.
 */
public class LootManager {

    private static final int COINS_ID = 995;
    private static final int LOOT_RANGE = 8;
    private static final long VALUE_TTL_MS = 5 * 60 * 1000L;

    private long lastValueCalc = 0;
    private long cachedBankValue = -1;

    private static final int KILL_RADIUS = 2, BONES_RADIUS = 1, MAX_SITES = 12;
    private static final long KILL_TTL_MS = 90_000L;

    private final java.util.Deque<long[]> killSites = new java.util.ArrayDeque<>();
    private Pos fightingTile;
    private long fightingSeenAt = 0;
    private final java.util.Set<Integer> seenOwnership = new java.util.HashSet<>();

    private static GameApi g() { return Game.api(); }

    public void trackFight() {
        try {
            GameApi a = g(); if (a == null) return;
            long now = System.currentTimeMillis();
            Npc c = a.interactingNpc();
            if (c != null && c.position() != null) {
                fightingTile = c.position();
                fightingSeenAt = now;
                if (c.healthPercent() == 0) recordKill(fightingTile, now);
            } else if (fightingTile != null && now - fightingSeenAt < 4000) {
                recordKill(fightingTile, now);
                fightingTile = null;
            }
        } catch (Throwable ignored) { }
    }

    private void recordKill(Pos t, long now) {
        if (t == null) return;
        long[] last = killSites.peekLast();
        if (last != null && last[0] == t.getX() && last[1] == t.getY() && last[2] == t.getZ()) { last[3] = now; return; }
        killSites.addLast(new long[]{t.getX(), t.getY(), t.getZ(), now});
        if (killSites.size() > MAX_SITES) killSites.removeFirst();
    }

    private void noteOwnership(TaskContext ctx, GroundItem g) {
        try { int own = g.ownership(); if (seenOwnership.add(own)) ctx.log("[loot] ownership=" + own + " on an item we killed for (" + g.name() + ")"); }
        catch (Throwable ignored) { }
    }

    private boolean withinKill(Pos t, int radius) {
        if (t == null) return false;
        long now = System.currentTimeMillis();
        killSites.removeIf(s -> now - s[3] > KILL_TTL_MS);
        for (long[] s : killSites) {
            if (t.getZ() != s[2]) continue;
            if (Math.abs(t.getX() - s[0]) <= radius && Math.abs(t.getY() - s[1]) <= radius) return true;
        }
        return false;
    }
    private boolean nearOurKill(Pos t) { return withinKill(t, KILL_RADIUS); }
    private boolean bonesAreOurs(Pos t) { return withinKill(t, BONES_RADIUS); }

    public int handle(TaskContext ctx) {
        GameApi a = g(); if (a == null || a.playerPosition() == null) return 0;
        int buried = buryBones(ctx);
        if (buried > 0) return buried;
        if (!ctx.config.isLootEnabled()) return 0;
        if (a.invEmptySlots() == 0) return bankLoot(ctx);
        if (a.isInCombat()) return 0;

        GroundItem gi = closestLoot(ctx);
        if (gi == null) return 0;
        if (gi.distance() > 5) { Nav.walkTo(gi.position()); return 600; }
        String name = gi.name();
        if (gi.take()) {
            noteOwnership(ctx, gi);
            ctx.log("[loot] taking " + name + ".");
            a.waitUntil(() -> !a.isMoving(), 2500);
        }
        return smallSleep();
    }

    private GroundItem closestLoot(TaskContext ctx) {
        GameApi a = g();
        GroundItem best = null; double bd = Double.MAX_VALUE;
        for (GroundItem gi : a.groundItemsWithin(LOOT_RANGE)) {
            if (gi == null || gi.position() == null) continue;
            if (gi.distance() > LOOT_RANGE || !shouldLoot(ctx, gi)) continue;
            double d = gi.distance();
            if (d < bd) { bd = d; best = gi; }
        }
        return best;
    }

    private boolean shouldLoot(TaskContext ctx, GroundItem g) {
        if (!nearOurKill(g.position())) return false;
        int id = g.id();
        if (ctx.config.isPlanActive()) {
            int cap = ctx.config.getLootCap(id);
            if (cap > 0 && ownedCount(ctx, id) >= cap) return false;
        }
        if (isBones(g.name())) return bonesAreOurs(g.position());
        if (id == COINS_ID) return true;
        if (g.stackable()) return true;
        if (ctx.config.isLootAllWhenPoor() && bankValue(ctx) < ctx.config.getLootWealthGate()) return true;
        return itemValue(id) >= ctx.config.getLootValuableMinValue();
    }

    private boolean isBones(String name) { return name != null && name.toLowerCase().contains("bones"); }

    private int ownedCount(TaskContext ctx, int id) {
        int carried = 0;
        try { carried = g().invCount(id); } catch (Throwable ignored) { }
        int banked = 0;
        try { if (ctx.bank != null) banked = ctx.bank.count(id); } catch (Throwable ignored) { }
        return carried + banked;
    }

    private int itemValue(int id) { if (id == COINS_ID) return 1; int v = PriceLookup.high(id); return Math.max(v, 0); }

    private int buryBones(TaskContext ctx) {
        GameApi a = g();
        for (int id : a.inventoryItemIdsMatching("bones")) {
            final int before = a.invCount(id), fid = id;
            if (a.invInteract(id, "Bury")) {
                ctx.log("[loot] burying bones #" + id + ".");
                a.waitUntil(() -> a.invCount(fid) < before, 1500);
                return 500;
            }
        }
        return 0;
    }

    private int bankLoot(TaskContext ctx) {
        GameApi a = g();
        if (!a.bankIsOpen()) { if (!Nav.openBank(null)) return 600; return 400; }
        final int before = a.invEmptySlots();
        a.depositAllExcept(foodIds());
        a.waitUntil(() -> a.invEmptySlots() > before, 2000);
        ctx.log("[loot] inventory full — banked loot (kept food).");
        a.closeBank();
        return 700;
    }

    private int[] foodIds() {
        Food[] f = Food.values();
        int[] ids = new int[f.length];
        for (int i = 0; i < f.length; i++) ids[i] = f[i].id;
        return ids;
    }

    private long bankValue(TaskContext ctx) {
        long now = System.currentTimeMillis();
        if (cachedBankValue >= 0 && now - lastValueCalc < VALUE_TTL_MS) return cachedBankValue;
        lastValueCalc = now;
        long total = 0;
        try {
            Map<Integer, Integer> contents = ctx.bank.contentsCopy();
            for (Map.Entry<Integer, Integer> e : contents.entrySet()) {
                int id = e.getKey(), qty = e.getValue();
                int unit = id == COINS_ID ? 1 : Math.max(0, PriceLookup.high(id));
                total += (long) unit * qty;
            }
        } catch (Throwable ignored) { return cachedBankValue >= 0 ? cachedBankValue : 0; }
        cachedBankValue = total;
        return total;
    }

    private int smallSleep() { return 400 + (int) (Math.random() * 350); }
}
