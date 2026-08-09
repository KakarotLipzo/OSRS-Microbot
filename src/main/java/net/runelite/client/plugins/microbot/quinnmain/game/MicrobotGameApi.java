package net.runelite.client.plugins.microbot.quinnmain.game;

import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.ArrayList;
import java.util.List;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Microbot implementation of {@link GameApi}, delegating to the static {@code Rs2*} utilities.
 *
 * <p><b>⚠ COMPILE-VERIFY IN A MICROBOT FORK.</b> This maps the facade onto Microbot's API using its
 * documented conventions, but the exact method signatures drift between Microbot revisions (e.g.
 * {@code Rs2Bank.withdrawX} vs {@code withdrawItem}, {@code Rs2Player.getWorldLocation} vs
 * {@code getWorldPoint}). It has NOT been compiled here — OSRS-Micro has no Microbot toolchain. When
 * you drop this into your fork under {@code plugins/microbot/quinnmain/} and build, the compiler will
 * flag the handful that differ; fix them against your fork's actual signatures. Methods proven by the
 * vertical slice are implemented; everything the slice doesn't exercise yet throws
 * {@link UnsupportedOperationException} <i>on purpose</i> — nothing silently pretends to work
 * (Quinn's honesty rule). Implement each as its subsystem is ported (see PORT_PLAN.md).
 */
public final class MicrobotGameApi implements GameApi {

    // ── coordinate conversion ────────────────────────────────────────────────────────────────
    private static WorldPoint wp(Pos p) { return new WorldPoint(p.x, p.y, p.plane); }
    private static Pos pos(WorldPoint w) { return w == null ? null : new Pos(w.getX(), w.getY(), w.getPlane()); }

    /** Map a neutral skill name to RuneLite's enum. RUNECRAFTING→RUNECRAFT; SAILING has no RL skill → null. */
    private static Skill rl(String name) {
        if (name == null) return null;
        String n = name.trim().toUpperCase();
        if (n.equals("RUNECRAFTING")) return Skill.RUNECRAFT;
        if (n.equals("SAILING")) return null;
        try { return Skill.valueOf(n); } catch (Exception e) { return null; }
    }
    private static Skill skill(String name) { return rl(name); }

    // ── local player ─────────────────────────────────────────────────────────────────────────
    @Override public boolean isLoggedIn() { return Microbot.isLoggedIn(); }
    @Override public Pos playerPosition() { return pos(Rs2Player.getWorldLocation()); }
    @Override public boolean isMoving() { return Rs2Player.isMoving(); }
    @Override public boolean isAnimating() { return Rs2Player.isAnimating(); }
    @Override public boolean isInCombat() { try { return Rs2Player.isInteracting() || Rs2Combat.inCombat(); } catch (Throwable t) { return false; } } // TODO verify
    @Override public int healthPercent() { return Rs2Player.getHealthPercentage(); }
    @Override public int runEnergy() { return (int) Microbot.getClient().getEnergy() / 100; }
    @Override public int combatLevel() {
        try { return Microbot.getClient().getLocalPlayer().getCombatLevel(); } catch (Throwable t) { return 3; }
    }
    @Override public int skillLevel(String s) { Skill k = rl(s); return k == null ? 1 : Microbot.getClient().getBoostedSkillLevel(k); }
    @Override public int skillLevelReal(String s) { Skill k = rl(s); return k == null ? 1 : Microbot.getClient().getRealSkillLevel(k); }
    @Override public long skillXp(String s) { Skill k = rl(s); return k == null ? 0 : Microbot.getClient().getSkillExperience(k); }
    @Override public int xpToLevel(String s) {
        Skill k = rl(s); if (k == null) return 0;
        int lvl = Microbot.getClient().getRealSkillLevel(k);
        if (lvl >= 99) return 0;
        return Math.max(0, net.runelite.api.Experience.getXpForLevel(lvl + 1) - Microbot.getClient().getSkillExperience(k));
    }
    @Override public long[] allSkillXp() {
        Sk[] vals = Sk.values();
        long[] out = new long[vals.length];
        for (Sk sk : vals) { Skill k = rl(sk.name()); out[sk.ordinal()] = k == null ? 0 : Microbot.getClient().getSkillExperience(k); }
        return out;
    }
    @Override public String username() {
        try { return Microbot.getClient().getLocalPlayer().getName(); } catch (Throwable t) { return null; }
    }
    @Override public boolean isMembers() {
        // TODO verify: RuneLite reports the WORLD's type, not account membership. Proxy via members world.
        try { return Microbot.getClient().getWorldType() != null
                && Microbot.getClient().getWorldType().contains(net.runelite.api.WorldType.MEMBERS); }
        catch (Throwable t) { return false; }
    }
    @Override public int totalLevel() { try { return Microbot.getClient().getTotalLevel(); } catch (Throwable t) { return 0; } }
    @Override public int questPoints() { try { return Microbot.getClient().getVarpValue(101); } catch (Throwable t) { return 0; } } // TODO verify QP varp id
    @Override public int varcInt(int id) { try { return Microbot.getClient().getVarcIntValue(id); } catch (Throwable t) { return 0; } }

    // ── movement ─────────────────────────────────────────────────────────────────────────────
    @Override public boolean walkTo(Pos target) { return Rs2Walker.walkTo(wp(target)); }
    @Override public boolean arrived(Pos target, int radius) {
        WorldPoint me = Rs2Player.getWorldLocation();
        return me != null && me.distanceTo(wp(target)) <= radius;
    }
    @Override public double distanceTo(Pos target) {
        WorldPoint me = Rs2Player.getWorldLocation();
        return me == null ? Double.MAX_VALUE : me.distanceTo(wp(target));
    }

    // ── inventory ────────────────────────────────────────────────────────────────────────────
    @Override public boolean invContains(int id) { return Rs2Inventory.hasItem(id); }
    @Override public boolean invContains(String name) { return Rs2Inventory.hasItem(name); }
    @Override public int invCount(int id) { return Rs2Inventory.count(id); }
    @Override public boolean invIsFull() { return Rs2Inventory.isFull(); }
    @Override public int invEmptySlots() { return Rs2Inventory.getEmptySlots(); }
    @Override public boolean invInteract(int id, String action) { return Rs2Inventory.interact(id, action); }
    @Override public boolean useItemOnItem(int a, int b) { try { return Rs2Inventory.combine(a, b); } catch (Throwable t) { return false; } } // TODO verify combine/useItemOnItem
    @Override public boolean invDropAll(int... ids) { boolean any = false; for (int id : ids) any |= Rs2Inventory.dropAll(id); return any; }
    @Override public boolean invDropAllExcept(int... keep) {
        try { Rs2Inventory.dropAllExcept(keep); return true; } catch (Throwable t) { return false; } // TODO verify signature
    }
    @Override public List<String> inventoryItemNames() {
        List<String> out = new ArrayList<>();
        try { for (var it : Rs2Inventory.all()) if (it != null && it.getName() != null) out.add(it.getName()); } catch (Throwable ignored) { }
        return out;
    }
    @Override public List<Integer> inventoryItemIds() {
        List<Integer> out = new ArrayList<>();
        try { for (var it : Rs2Inventory.all()) if (it != null) out.add(it.getId()); } catch (Throwable ignored) { }
        return out;
    }

    // ── equipment ────────────────────────────────────────────────────────────────────────────
    @Override public boolean isWearing(int id) { return Rs2Equipment.isWearing(id); }
    @Override public boolean equip(int id) { return Rs2Inventory.interact(id, "Wield") || Rs2Inventory.interact(id, "Wear"); }
    @Override public List<String> equipmentItemNames() {
        List<String> out = new ArrayList<>();
        try { for (var it : Rs2Equipment.items()) if (it != null && it.getName() != null) out.add(it.getName()); } catch (Throwable ignored) { } // TODO verify Rs2Equipment.items()
        return out;
    }

    // ── banking ──────────────────────────────────────────────────────────────────────────────
    @Override public boolean bankIsOpen() { return Rs2Bank.isOpen(); }
    @Override public boolean openNearestBank() { return Rs2Bank.walkToBankAndUseBank(); }
    @Override public boolean closeBank() { return Rs2Bank.closeBank(); }
    @Override public int bankCount(int id) { return Rs2Bank.count(id); }
    @Override public boolean bankContains(int id) { return Rs2Bank.hasItem(id); }
    @Override public boolean withdraw(int id, int amount) { return Rs2Bank.withdrawX(id, amount); }
    @Override public boolean withdrawAll(int id) { return Rs2Bank.withdrawAll(id); }
    @Override public boolean deposit(int id, int amount) { return Rs2Bank.depositX(id, amount); }
    @Override public boolean depositAllExcept(int... keep) {
        Integer[] boxed = new Integer[keep.length];
        for (int i = 0; i < keep.length; i++) boxed[i] = keep[i];
        return Rs2Bank.depositAllExcept(boxed);
    }
    @Override public boolean depositInventory() { return Rs2Bank.depositAll(); }
    @Override public java.util.Map<Integer, Integer> bankSnapshot() {
        java.util.Map<Integer, Integer> m = new java.util.HashMap<>();
        try {
            for (var it : Rs2Bank.bankItems()) {   // TODO verify method name (bankItems/getBankItems)
                if (it == null) continue;
                m.merge(it.getId(), Math.max(1, it.getQuantity()), Integer::sum);
            }
        } catch (Throwable ignored) { }
        return m;
    }

    // ── objects ──────────────────────────────────────────────────────────────────────────────
    @Override public List<GameObj> objectsWithin(int tiles) {
        List<GameObj> out = new ArrayList<>();
        try { for (GameObject o : Rs2GameObject.getGameObjects()) if (o != null) out.add(new ObjHandle(o)); } // TODO verify getGameObjects()
        catch (Throwable ignored) { }
        return out;
    }
    @Override public GameObj nearestObject(String... names) {
        for (String name : names) {
            GameObject o = Rs2GameObject.findObject(name, true);   // TODO verify name lookup helper
            if (o != null) return new ObjHandle(o);
        }
        return null;
    }
    @Override public GameObj nearestObjectById(int... ids) {
        for (int id : ids) {
            GameObject o = Rs2GameObject.findObjectById(id);
            if (o != null) return new ObjHandle(o);
        }
        return null;
    }
    @Override public boolean interactObject(GameObj obj, String action) {
        return obj instanceof ObjHandle && Rs2GameObject.interact(((ObjHandle) obj).o, action);
    }

    // ── NPCs ─────────────────────────────────────────────────────────────────────────────────
    @Override public List<Npc> npcsWithin(int tiles) {
        List<Npc> out = new ArrayList<>();
        try { for (NPC n : Rs2Npc.getNpcs()) if (n != null) out.add(new NpcHandle(n)); } // TODO verify getNpcs()
        catch (Throwable ignored) { }
        return out;
    }
    @Override public Npc nearestNpc(String... names) {
        for (String name : names) {
            NPC n = Rs2Npc.getNpc(name);
            if (n != null) return new NpcHandle(n);
        }
        return null;
    }
    @Override public boolean interactNpc(Npc npc, String action) {
        return npc instanceof NpcHandle && Rs2Npc.interact(((NpcHandle) npc).n, action);
    }

    // ── ground items ─────────────────────────────────────────────────────────────────────────
    @Override public List<GroundItem> groundItemsWithin(int tiles) {
        // TODO port: looting subsystem — map Rs2GroundItem's model to GroundItem handles. Empty for now
        // (only the quest killFor() consumes this; quests are a later wave). Rs2GroundItem import kept for then.
        return new ArrayList<>();
    }

    // ── dialogue ─────────────────────────────────────────────────────────────────────────────
    @Override public boolean dialogueOpen() { return Rs2Dialogue.isInDialogue(); }
    @Override public boolean hasDialogueOptions() { return Rs2Dialogue.hasSelectAnOption(); }
    @Override public List<String> dialogueOptions() {
        throw new UnsupportedOperationException("TODO port: quests — expose Rs2Dialogue option strings for the keyword matcher.");
    }
    @Override public boolean selectDialogueOption(int oneBasedIndex) {
        throw new UnsupportedOperationException("TODO port: quests — Rs2Dialogue.clickOption by index.");
    }
    @Override public boolean continueDialogue() { return Rs2Dialogue.clickContinue(); }

    // ── widgets ──────────────────────────────────────────────────────────────────────────────
    @Override public boolean widgetVisible(int group, int child) { return Rs2Widget.isWidgetVisible(group, child); }
    @Override public String widgetText(int group, int child) {
        var w = Rs2Widget.getWidget(group, child);
        return w == null ? null : w.getText();
    }
    @Override public boolean interactWidget(int group, int child, String action) {
        try { return Rs2Widget.clickWidget(Rs2Widget.getWidget(group, child)); } catch (Throwable t) { return false; } // TODO verify click-by-widget
    }
    @Override public boolean makeScreenOpen() {
        try { return Rs2Widget.isWidgetVisible(270, 14); } catch (Throwable t) { return false; } // TODO verify make-screen group/child
    }
    @Override public boolean makeScreenHas(String productName) {
        try { return Rs2Widget.hasWidget(productName); } catch (Throwable t) { return false; } // TODO verify
    }
    @Override public boolean clickMake(String productName, int quantity) {
        // TODO verify against the fork's make-screen helper: select All quantity, then click the product.
        try { return Rs2Widget.clickWidget(productName); } catch (Throwable t) { return false; }
    }

    // ── grand exchange ───────────────────────────────────────────────────────────────────────
    @Override public boolean geOpen() { return Rs2GrandExchange.isOpen(); }
    @Override public boolean openGe() { return Rs2GrandExchange.walkToGrandExchange(); }
    @Override public boolean geBuy(int itemId, int quantity, int unitPrice) {
        try { return Rs2GrandExchange.buyItem(String.valueOf(itemId), quantity, unitPrice); } catch (Throwable t) { return false; } // TODO verify id-vs-name signature
    }
    @Override public boolean geSell(int itemId, int quantity, int unitPrice) {
        try { return Rs2GrandExchange.sellItem(itemId, quantity, unitPrice); } catch (Throwable t) { return false; } // TODO verify
    }
    @Override public boolean geCollectAll() { try { return Rs2GrandExchange.collectAllToBank(); } catch (Throwable t) { return false; } }
    @Override public boolean geCollectToBank() { try { return Rs2GrandExchange.collectAllToBank(); } catch (Throwable t) { return false; } }
    @Override public int geUsedSlots() {
        try {
            int n = 0;
            for (net.runelite.api.GrandExchangeOffer o : Microbot.getClient().getGrandExchangeOffers())
                if (o != null && o.getState() != net.runelite.api.GrandExchangeOfferState.EMPTY) n++;
            return n;
        } catch (Throwable t) { return 0; }
    }
    @Override public List<GeOffer> geOffers() {
        List<GeOffer> out = new ArrayList<>();
        try {
            net.runelite.api.GrandExchangeOffer[] arr = Microbot.getClient().getGrandExchangeOffers();
            for (int slot = 0; slot < arr.length; slot++) {
                net.runelite.api.GrandExchangeOffer o = arr[slot];
                if (o != null && o.getState() != net.runelite.api.GrandExchangeOfferState.EMPTY) out.add(new GeOfferHandle(slot, o));
            }
        } catch (Throwable ignored) { }
        return out;
    }
    @Override public boolean geCancel(int slot) { try { return Rs2GrandExchange.abortOffer(slot); } catch (Throwable t) { return false; } } // TODO verify abort signature
    @Override public boolean geClose() { try { return Rs2GrandExchange.closeExchange(); } catch (Throwable t) { return false; } } // TODO verify name
    @Override public boolean geReadyToCollect() { try { return Rs2GrandExchange.hasBoughtOffer() || Rs2GrandExchange.hasSoldOffer(); } catch (Throwable t) { return false; } } // TODO verify

    // ── shops ────────────────────────────────────────────────────────────────────────────────
    @Override public boolean shopIsOpen() { try { return Rs2Shop.isOpen(); } catch (Throwable t) { return false; } } // TODO verify
    @Override public boolean openShop(String npcName) { try { return Rs2Shop.openShop(npcName); } catch (Throwable t) { return false; } } // TODO verify
    @Override public boolean closeShop() { try { return Rs2Shop.closeShop(); } catch (Throwable t) { return false; } } // TODO verify
    @Override public boolean shopPurchase(int itemId, int qty) { try { return Rs2Shop.buyItem(String.valueOf(itemId), String.valueOf(qty)); } catch (Throwable t) { return false; } } // TODO verify id-vs-name signature
    @Override public boolean shopPurchase(String itemName, int qty) { try { return Rs2Shop.buyItem(itemName, String.valueOf(qty)); } catch (Throwable t) { return false; } } // TODO verify
    @Override public List<String> shopStock() {
        List<String> out = new ArrayList<>();
        try { for (var it : Rs2Shop.getShopItems()) if (it != null && it.getName() != null) out.add(it.getName() + "#" + it.getId()); } // TODO verify getShopItems()
        catch (Throwable ignored) { }
        return out;
    }

    // ── prayer ───────────────────────────────────────────────────────────────────────────────
    private static net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum rsPrayer(String name) {
        try { return net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum.valueOf(name); } catch (Throwable t) { return null; } // TODO verify enum names match
    }
    @Override public boolean isPrayerActive(String prayer) {
        try { var p = rsPrayer(prayer); return p != null && net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer.isPrayerActive(p); } catch (Throwable t) { return false; }
    }
    @Override public boolean setPrayer(String prayer, boolean on) {
        try { var p = rsPrayer(prayer); if (p == null) return false; net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer.toggle(p, on); return true; } catch (Throwable t) { return false; } // TODO verify toggle signature
    }

    // ── session ──────────────────────────────────────────────────────────────────────────────
    @Override public boolean logout() { try { return Rs2Player.logout(); } catch (Throwable t) { return false; } } // TODO verify (Rs2Player.logout / Logout util)
    @Override public boolean login() {
        // TODO verify: Microbot manages login via the Login util / profile; wire to the fork's mechanism.
        try { new net.runelite.client.plugins.microbot.util.security.Login(); return true; } catch (Throwable t) { return false; }
    }

    // ── timing ───────────────────────────────────────────────────────────────────────────────
    @Override public void sleep(int ms) { net.runelite.client.plugins.microbot.util.Global.sleep(ms); }
    @Override public boolean waitUntil(java.util.function.BooleanSupplier cond, int timeoutMs) {
        return sleepUntil(cond::getAsBoolean, timeoutMs);
    }

    // ── handles ──────────────────────────────────────────────────────────────────────────────
    private static double distTo(WorldPoint w) {
        WorldPoint me = Rs2Player.getWorldLocation();
        return (me == null || w == null) ? Double.MAX_VALUE : me.distanceTo(w);
    }

    private static final class ObjHandle implements GameObj {
        final GameObject o;
        ObjHandle(GameObject o) { this.o = o; }
        @Override public int id() { return o.getId(); }
        @Override public String name() {
            var c = Rs2GameObject.getObjectComposition(o.getId());
            return c == null ? null : c.getName();
        }
        @Override public Pos position() { return pos(o.getWorldLocation()); }
        @Override public double distance() { return distTo(o.getWorldLocation()); }
        @Override public boolean hasAction(String action) {
            try {
                var c = Rs2GameObject.getObjectComposition(o.getId());
                if (c == null || c.getActions() == null) return false;
                for (String a : c.getActions()) if (a != null && a.equalsIgnoreCase(action)) return true;
            } catch (Throwable ignored) { }
            return false;
        }
        @Override public boolean exists() { try { return Rs2GameObject.exists(o); } catch (Throwable t) { return true; } } // TODO verify
        @Override public boolean interact(String action) { return Rs2GameObject.interact(o, action); }
        @Override public boolean useItem(int itemId) { try { return Rs2Inventory.useItemOnObject(itemId, o.getId()); } catch (Throwable t) { return false; } } // TODO verify
    }
    private static final class NpcHandle implements Npc {
        final NPC n;
        NpcHandle(NPC n) { this.n = n; }
        @Override public int id() { return n.getId(); }
        @Override public String name() { return n.getName(); }
        @Override public Pos position() { return pos(n.getWorldLocation()); }
        @Override public double distance() { return distTo(n.getWorldLocation()); }
        @Override public boolean hasAction(String action) {
            try {
                if (n.getComposition() == null || n.getComposition().getActions() == null) return false;
                for (String a : n.getComposition().getActions()) if (a != null && a.equalsIgnoreCase(action)) return true;
            } catch (Throwable ignored) { }
            return false;
        }
        @Override public boolean interact(String action) { return Rs2Npc.interact(n, action); }
        @Override public boolean interactingWithMe() {
            try { return n.getInteracting() == Microbot.getClient().getLocalPlayer(); } catch (Throwable t) { return false; }
        }
        @Override public boolean useItem(int itemId) { try { return Rs2Inventory.useItemOnNpc(itemId, n); } catch (Throwable t) { return false; } } // TODO verify
    }
    private static final class GeOfferHandle implements GeOffer {
        final int slot; final net.runelite.api.GrandExchangeOffer o;
        GeOfferHandle(int slot, net.runelite.api.GrandExchangeOffer o) { this.slot = slot; this.o = o; }
        @Override public int itemId() { return o.getItemId(); }
        @Override public int slot() { return slot; }
        @Override public boolean buy() {
            net.runelite.api.GrandExchangeOfferState s = o.getState();
            return s == net.runelite.api.GrandExchangeOfferState.BUYING
                    || s == net.runelite.api.GrandExchangeOfferState.BOUGHT
                    || s == net.runelite.api.GrandExchangeOfferState.CANCELLED_BUY;
        }
        @Override public boolean sell() {
            net.runelite.api.GrandExchangeOfferState s = o.getState();
            return s == net.runelite.api.GrandExchangeOfferState.SELLING
                    || s == net.runelite.api.GrandExchangeOfferState.SOLD
                    || s == net.runelite.api.GrandExchangeOfferState.CANCELLED_SELL;
        }
        @Override public boolean readyToCollect() {
            net.runelite.api.GrandExchangeOfferState s = o.getState();
            return s == net.runelite.api.GrandExchangeOfferState.BOUGHT
                    || s == net.runelite.api.GrandExchangeOfferState.SOLD
                    || s == net.runelite.api.GrandExchangeOfferState.CANCELLED_BUY
                    || s == net.runelite.api.GrandExchangeOfferState.CANCELLED_SELL;
        }
        @Override public int transferredAmount() { return o.getQuantitySold(); }
        @Override public long transferredValue() { return o.getSpent(); }
    }
}
