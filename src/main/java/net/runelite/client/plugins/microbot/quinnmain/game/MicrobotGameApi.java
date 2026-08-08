package net.runelite.client.plugins.microbot.quinnmain.game;

import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
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

    private static Skill skill(String name) { return Skill.valueOf(name.trim().toUpperCase()); }

    // ── local player ─────────────────────────────────────────────────────────────────────────
    @Override public boolean isLoggedIn() { return Microbot.isLoggedIn(); }
    @Override public Pos playerPosition() { return pos(Rs2Player.getWorldLocation()); }
    @Override public boolean isMoving() { return Rs2Player.isMoving(); }
    @Override public boolean isAnimating() { return Rs2Player.isAnimating(); }
    @Override public int healthPercent() { return Rs2Player.getHealthPercentage(); }
    @Override public int runEnergy() { return (int) Microbot.getClient().getEnergy() / 100; }
    @Override public int skillLevel(String s) { return Microbot.getClient().getBoostedSkillLevel(skill(s)); }
    @Override public int skillLevelReal(String s) { return Microbot.getClient().getRealSkillLevel(skill(s)); }
    @Override public long skillXp(String s) { return Microbot.getClient().getSkillExperience(skill(s)); }

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
    @Override public boolean invDropAll(int... ids) { boolean any = false; for (int id : ids) any |= Rs2Inventory.dropAll(id); return any; }

    // ── equipment ────────────────────────────────────────────────────────────────────────────
    @Override public boolean isWearing(int id) { return Rs2Equipment.isWearing(id); }
    @Override public boolean equip(int id) { return Rs2Inventory.interact(id, "Wield") || Rs2Inventory.interact(id, "Wear"); }

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

    // ── objects ──────────────────────────────────────────────────────────────────────────────
    @Override public GameObj nearestObject(String... names) {
        for (String name : names) {
            GameObject o = Rs2GameObject.findObject(name, true);   // TODO verify: name lookup helper
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
    @Override public boolean isInteractingWithMe(Npc npc) {
        return npc instanceof NpcHandle && ((NpcHandle) npc).n.getInteracting() == Microbot.getClient().getLocalPlayer();
    }

    // ── ground items ─────────────────────────────────────────────────────────────────────────
    @Override public GroundItem nearestGroundItem(int maxTiles, int... itemIds) {
        throw new UnsupportedOperationException("TODO port: looting subsystem (Rs2GroundItem.getNearest / loot).");
    }
    @Override public boolean takeGroundItem(GroundItem item) {
        throw new UnsupportedOperationException("TODO port: looting subsystem.");
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
        throw new UnsupportedOperationException("TODO port: MakeInterface — Rs2Widget click by group/child/action.");
    }

    // ── grand exchange ───────────────────────────────────────────────────────────────────────
    @Override public boolean geOpen() { return Rs2GrandExchange.isOpen(); }
    @Override public boolean openGe() { return Rs2GrandExchange.walkToGrandExchange(); }
    @Override public boolean geBuy(int itemId, int quantity, int unitPrice) {
        throw new UnsupportedOperationException("TODO port: money engine — Rs2GrandExchange.buyItem (verify id vs name signature).");
    }
    @Override public boolean geSell(int itemId, int quantity, int unitPrice) {
        throw new UnsupportedOperationException("TODO port: money engine — Rs2GrandExchange.sellItem.");
    }
    @Override public boolean geCollectAll() { return Rs2GrandExchange.collectAllToBank(); }

    // ── shops ────────────────────────────────────────────────────────────────────────────────
    @Override public boolean shopOpen() {
        throw new UnsupportedOperationException("TODO port: SupplyBuy — Rs2Shop.hasShopOpen.");
    }
    @Override public boolean shopBuy(String itemName, int quantity) {
        throw new UnsupportedOperationException("TODO port: SupplyBuy — Rs2Shop.buyItem.");
    }

    // ── timing ───────────────────────────────────────────────────────────────────────────────
    @Override public void sleep(int ms) { net.runelite.client.plugins.microbot.util.Global.sleep(ms); }
    @Override public boolean waitUntil(java.util.function.BooleanSupplier cond, int timeoutMs) {
        return sleepUntil(cond::getAsBoolean, timeoutMs);
    }

    // ── handles ──────────────────────────────────────────────────────────────────────────────
    private static final class ObjHandle implements GameObj {
        final GameObject o;
        ObjHandle(GameObject o) { this.o = o; }
        @Override public Pos position() { return pos(o.getWorldLocation()); }
        @Override public int id() { return o.getId(); }
        @Override public String name() {
            var c = Rs2GameObject.getObjectComposition(o.getId());
            return c == null ? null : c.getName();
        }
    }
    private static final class NpcHandle implements Npc {
        final NPC n;
        NpcHandle(NPC n) { this.n = n; }
        @Override public Pos position() { return pos(n.getWorldLocation()); }
        @Override public int id() { return n.getId(); }
        @Override public String name() { return n.getName(); }
    }
}
