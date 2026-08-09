package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Game;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi.GameObj;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi.GroundItem;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi.Npc;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

import java.util.function.Predicate;

/**
 * Self-gather fallbacks (mine ore, pick crops, kill for a drop, shop-buy) plus processing helpers
 * (smelt/cook/mill). Ported from OSRS-Main to the {@link GameApi} facade: the DreamBot object/NPC/
 * ground-item/shop statics become facade finders, and the (client-neutral) predicate filtering stays
 * in Java here.
 *
 * <p><b>smelt / cook / mill</b> drive the shared "make" popup, which needs {@code MakeInterface} — a
 * later wave. Until that lands they throw {@link UnsupportedOperationException} (honest — no silent
 * no-op). Every other method returns {@code 0} once we hold {@code qty}, else {@code >0} (a delay).
 */
public final class Gather {

    private Gather() { }

    private static GameApi g() { return Game.api(); }

    // ── finders (facade list → nearest matching) ──────────────────────────────────────────────
    private static GameObj closestObject(Predicate<GameObj> pred, int radius) {
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
    private static Npc closestNpc(Predicate<Npc> pred, int radius) {
        GameApi a = g(); if (a == null) return null;
        Npc best = null; double bd = Double.MAX_VALUE;
        for (Npc n : a.npcsWithin(radius)) {
            if (n == null) continue;
            try { if (!pred.test(n)) continue; } catch (Throwable t) { continue; }
            double d = n.distance();
            if (d < bd) { bd = d; best = n; }
        }
        return best;
    }

    // ── mine ──────────────────────────────────────────────────────────────────────────────────
    public static int mineOre(int oreId, int[] rockIds, Pos site, int qty) {
        GameApi a = g(); if (a == null) return 600;
        try {
            if (a.invCount(oreId) >= qty) return 0;
            if (!hasPickaxe()) { Log.log("[gather] no pickaxe to mine ore #" + oreId + " — can't self-gather it."); return 0; }
            if (a.isAnimating()) return 500;

            GameObj rock = closestObject(o -> inSet(rockIds, o.id()) && o.hasAction("Mine")
                    && o.position() != null && o.position().distance(site) <= 15, 16);
            if (rock == null) {
                if (atSite(site)) logNearbyRocks(oreId, rockIds);
                Nav.walkTo(site);
                return 700;
            }
            if (rock.distance() > 4) { Nav.walkTo(rock.position()); return 600; }
            final int before = a.invCount(oreId);
            final GameObj r = rock;
            if (rock.interact("Mine")) {
                Log.log("[gather] mining ore #" + oreId + " (" + before + "/" + qty + ") @ " + rock.position() + ".");
                a.waitUntil(() -> a.invCount(oreId) > before || !r.exists(), 9000);
            }
            return 600;
        } catch (Throwable t) { Log.log("[gather] mineOre #" + oreId + " failed (" + t + ")."); return 600; }
    }

    private static long lastRockDump = 0;
    private static void logNearbyRocks(int oreId, int[] rockIds) {
        long t = System.currentTimeMillis();
        if (t - lastRockDump < 5000) return;
        lastRockDump = t;
        StringBuilder sb = new StringBuilder();
        java.util.Set<String> seen = new java.util.HashSet<>();
        GameApi a = g();
        if (a != null) for (GameObj o : a.objectsWithin(16)) {
            if (o == null || !o.hasAction("Mine") || o.name() == null) continue;
            String key = o.name() + "#" + o.id();
            if (seen.add(key)) sb.append(key).append('@').append(o.position()).append("  ");
            if (seen.size() >= 15) break;
        }
        Log.log("[gather] no rock matching ore #" + oreId + " ids=" + java.util.Arrays.toString(rockIds)
                + " — nearby minable rocks: " + (sb.length() == 0 ? "none" : sb));
    }

    // ── kill for a drop ─────────────────────────────────────────────────────────────────────────
    public static int killFor(int dropId, String dropName, String npcName, Pos at, int qty) {
        GameApi a = g(); if (a == null) return 700;
        try {
            if (a.invCount(dropId) >= qty) return 0;
            for (GroundItem gi : a.groundItemsWithin(10)) {   // loot the drop if it's on the floor
                if (gi != null && dropName.equalsIgnoreCase(gi.name())) {
                    if (gi.take()) { Log.log("[gather] taking " + dropName + "."); a.waitUntil(() -> a.invCount(dropId) > 0, 3000); }
                    return 700;
                }
            }
            if (a.isInCombat() || a.isAnimating()) return 700;
            Npc npc = closestNpc(n -> npcName.equalsIgnoreCase(n.name()) && n.hasAction("Attack"), 12);
            if (npc == null || npc.distance() > 12) { Nav.walkTo(at); return 800; }
            if (npc.interact("Attack")) { Log.log("[gather] attacking " + npcName + " for " + dropName + "."); a.waitUntil(a::isInCombat, 3000); }
            return 900;
        } catch (Throwable t) { Log.log("[gather] killFor " + dropName + " failed (" + t + ")."); return 700; }
    }

    // ── pick crops ─────────────────────────────────────────────────────────────────────────────
    public static int pickField(int itemId, String itemName, Pos at, int qty) {
        return pickFrom(itemId, itemName, itemName, "Pick", at, qty);
    }

    public static int pickFrom(int itemId, String itemName, String objNameContains, String action, Pos at, int qty) {
        GameApi a = g(); if (a == null) return 600;
        try {
            if (a.invCount(itemId) >= qty) return 0;
            if (a.isAnimating()) return 500;
            final String want = objNameContains.toLowerCase();
            Predicate<GameObj> match = o -> o.name() != null && o.name().toLowerCase().contains(want)
                    && o.hasAction(action) && o.position() != null && o.position().distance(at) <= 30;
            GameObj plant = closestObject(o -> match.test(o) && !depleted(o.position()), 30);
            if (plant == null) {
                boolean anyExist = closestObject(match, 30) != null;
                if (anyExist) return 2500;                     // all known plants depleted → wait for respawn
                if (atSite(at)) logNearbyObjects(itemName, objNameContains, action);
                Nav.walkTo(at);
                return 700;
            }
            if (plant.distance() > 4) { Nav.walkTo(plant.position()); return 600; }
            final int before = a.invCount(itemId);
            final Pos plantTile = plant.position();
            if (plant.interact(action)) {
                Log.log("[gather] " + action + " " + itemName + " (" + before + "/" + qty + ").");
                boolean got = a.waitUntil(() -> a.invCount(itemId) > before, 3000);
                if (!got) { markDepleted(plantTile); Log.log("[gather] " + itemName + " plant @" + plantTile + " empty — trying another."); }
            }
            return 600;
        } catch (Throwable t) { Log.log("[gather] pickFrom " + itemName + " failed (" + t + ")."); return 600; }
    }

    private static final java.util.Map<String, Long> depletedUntil = new java.util.HashMap<>();
    private static final long RESPAWN_MS = 125_000;
    private static String tileKey(Pos t) { return t == null ? "?" : t.getX() + "," + t.getY() + "," + t.getZ(); }
    private static boolean depleted(Pos t) { Long u = depletedUntil.get(tileKey(t)); return u != null && System.currentTimeMillis() < u; }
    private static void markDepleted(Pos t) { if (t != null) depletedUntil.put(tileKey(t), System.currentTimeMillis() + RESPAWN_MS); }

    private static long lastObjDump = 0;
    private static void logNearbyObjects(String itemName, String objNameContains, String action) {
        long t = System.currentTimeMillis();
        if (t - lastObjDump < 5000) return;
        lastObjDump = t;
        StringBuilder sb = new StringBuilder();
        java.util.Set<String> seen = new java.util.HashSet<>();
        GameApi a = g();
        if (a != null) for (GameObj o : a.objectsWithin(20)) {
            if (o == null || o.name() == null || o.name().equals("null")) continue;
            String key = o.name() + "#" + o.id();
            if (seen.add(key)) sb.append(key).append('@').append(o.position()).append("  ");
            if (seen.size() >= 30) break;
        }
        Log.log("[gather] no '" + objNameContains + "' object with action '" + action + "' for " + itemName
                + " near anchor — nearby objects: " + (sb.length() == 0 ? "none" : sb));
    }

    // ── shop buy ───────────────────────────────────────────────────────────────────────────────
    public static int shopBuy(int itemId, String itemName, String shopNpcName, Pos at, int qty) {
        GameApi a = g(); if (a == null) return 700;
        try {
            if (a.invCount(itemId) >= qty) { if (a.shopIsOpen()) a.closeShop(); return 0; }
            if (!a.shopIsOpen()) {
                Npc npc = closestNpc(n -> shopNpcName.equalsIgnoreCase(n.name())
                        && (n.hasAction("Trade") || n.hasAction("Trade-with")), 8);
                if (npc == null || npc.distance() > 6) { Nav.walkTo(at); return 700; }
                if (a.openShop(shopNpcName)) a.waitUntil(a::shopIsOpen, 3000);
                return 700;
            }
            int need = qty - a.invCount(itemId);
            boolean ok = a.shopPurchase(itemId, need);
            if (!ok) ok = a.shopPurchase(itemName, need);
            Log.log("[gather] buying " + need + "x " + itemName + " from " + shopNpcName + " ok=" + ok);
            final int q = qty;
            a.waitUntil(() -> a.invCount(itemId) >= q, 2500);
            if (a.invCount(itemId) < qty) {
                dumpShop(itemName, itemId, shopNpcName);
                if (!ok) { a.closeShop(); return 0; }
            }
            return 600;
        } catch (Throwable t) { Log.log("[gather] shopBuy " + itemName + " failed (" + t + ")."); return 700; }
    }

    private static long lastShopDump = 0;
    private static void dumpShop(String itemName, int itemId, String shopNpcName) {
        long t = System.currentTimeMillis();
        if (t - lastShopDump < 5000) return;
        lastShopDump = t;
        GameApi a = g();
        java.util.List<String> stock = a == null ? java.util.Collections.emptyList() : a.shopStock();
        Log.log("[gather] " + shopNpcName + " shop open but couldn't buy " + itemName + " (#" + itemId
                + ") — stock: " + (stock.isEmpty() ? "EMPTY/none" : String.join("  ", stock)));
    }

    // ── processing (smelt/cook via the make screen; mill = quest-specific, still TODO) ────────────
    public static int smelt(int barId, String barName, int oreIdToUse, Pos furnaceSite, int qty) {
        GameApi a = g(); if (a == null) return 700;
        try {
            if (a.invCount(barId) >= qty) return 0;
            if (MakeInterface.isOpen(barName)) return runMake(barName, oreIdToUse);
            if (a.isAnimating()) return 600;
            GameObj furnace = closestObject(o -> o.name() != null && o.name().toLowerCase().contains("furnace"), 8);
            if (furnace == null || furnace.distance() > 5) {
                if (furnace == null && atSite(furnaceSite)) logNearbyObjects(barName, "furnace", "Smelt");
                Nav.walkTo(furnace != null ? furnace.position() : furnaceSite);
                return 700;
            }
            boolean opened = furnace.useItem(oreIdToUse) || furnace.interact("Smelt");
            if (opened) {
                a.waitUntil(() -> MakeInterface.isOpen() || a.isAnimating(), 3000);
                if (MakeInterface.isOpen(barName)) return runMake(barName, oreIdToUse);
            }
            return 700;
        } catch (Throwable t) { Log.log("[gather] smelt " + barName + " failed (" + t + ")."); return 700; }
    }

    public static int cook(int cookedId, String cookedName, int rawId, Pos cookSpot, int qty) {
        GameApi a = g(); if (a == null) return 700;
        try {
            if (a.invCount(cookedId) >= qty) return 0;
            if (MakeInterface.isOpen(cookedName) || MakeInterface.isOpen()) return runMake(cookedName, rawId);
            if (a.isAnimating()) return 600;
            if (!a.invContains(rawId)) return 0;   // nothing to cook — caller supplies raw food
            GameObj cooker = closestObject(o -> o.name() != null
                    && (o.name().toLowerCase().contains("range") || o.name().equalsIgnoreCase("Fire")
                        || o.name().toLowerCase().contains("stove") || o.name().toLowerCase().contains("cooking")), 8);
            if (cooker == null || cooker.distance() > 5) {
                if (cooker == null && atSite(cookSpot)) logNearbyObjects(cookedName, "range", "Cook");
                Nav.walkTo(cooker != null ? cooker.position() : cookSpot);
                return 700;
            }
            boolean opened = cooker.useItem(rawId) || cooker.interact("Cook");
            if (opened) {
                a.waitUntil(() -> MakeInterface.isOpen() || a.isAnimating(), 3000);
                if (MakeInterface.isOpen()) return runMake(cookedName, rawId);
            }
            return 700;
        } catch (Throwable t) { Log.log("[gather] cook " + cookedName + " failed (" + t + ")."); return 700; }
    }

    /** Select All + click the product in the make screen, then grind the batch until the input is used up. */
    private static int runMake(String productName, int watchInputId) {
        GameApi a = g(); if (a == null) return 900;
        String act = MakeInterface.click(productName);
        if (act == null) { Log.log("[gather] make button not found for " + productName + "; " + MakeInterface.describe()); return 900; }
        Log.log("[gather] making " + productName + " (" + act + ").");
        a.waitUntil(a::isAnimating, 3000);
        a.waitUntil(() -> a.invCount(watchInputId) == 0 || (!a.isAnimating() && !MakeInterface.isOpen()), 120000);
        return 800;
    }

    public static int mill(int potOfFlourId, int grainId, int emptyPotId, Pos millGround, int qty) {
        throw new UnsupportedOperationException("TODO port: windmill mill flow (multi-floor climb + hopper/bin useItem).");
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────
    private static boolean atSite(Pos site) {
        GameApi a = g();
        return a != null && site != null && a.playerPosition() != null && a.playerPosition().distance(site) <= 6;
    }

    public static boolean hasPickaxe() {
        GameApi a = g(); if (a == null) return false;
        try {
            for (String n : a.inventoryItemNames()) if (n != null && n.toLowerCase().contains("pickaxe")) return true;
            for (String n : a.equipmentItemNames()) if (n != null && n.toLowerCase().contains("pickaxe")) return true;
        } catch (Throwable ignore) { }
        return false;
    }

    private static boolean inSet(int[] ids, int id) {
        for (int i : ids) if (i == id) return true;
        return false;
    }
}
