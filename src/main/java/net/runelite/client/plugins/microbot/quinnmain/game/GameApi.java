package net.runelite.client.plugins.microbot.quinnmain.game;

import java.util.List;

/**
 * <h2>The client-neutral game facade — the whole point of the port.</h2>
 *
 * Every trainer, quest, combat routine and money method in Quinn Main talks to the game through
 * <b>this interface only</b>, never through DreamBot's {@code org.dreambot.*} or Microbot's
 * {@code Rs2*} classes directly. Swap the {@link GameApi} implementation, not the logic.
 *
 * <p><b>Coordinates.</b> The facade speaks {@link Pos} (a plain x/y/plane triple), not DreamBot
 * {@code Tile} or RuneLite {@code WorldPoint} — same values, so tile constants carry over unchanged.
 *
 * <p><b>Finder style.</b> Object/NPC/ground-item finders return <i>lists</i> of rich handles; the
 * neutral logic does its own filtering (by id/name/action/distance) in plain Java. This keeps the
 * facade small and moves the (client-neutral) predicate logic into the ported code where it already lives.
 */
public interface GameApi {

    // ── Local player ─────────────────────────────────────────────────────────────────────────
    boolean isLoggedIn();
    Pos playerPosition();
    boolean isMoving();
    boolean isAnimating();
    boolean isInCombat();
    int healthPercent();
    int runEnergy();
    int combatLevel();
    /** Current (possibly boosted) level of a skill, by its canonical OSRS name e.g. "WOODCUTTING". */
    int skillLevel(String skill);
    int skillLevelReal(String skill);
    long skillXp(String skill);
    /** XP remaining to the next level for a skill (0 at 99 / unknown). */
    int xpToLevel(String skill);
    /** All skills' XP, indexed by {@link Sk#ordinal()} (SAILING → 0). For the XP tracker. */
    long[] allSkillXp();
    String username();
    boolean isMembers();
    int totalLevel();
    int questPoints();
    /** VarClientInt read (e.g. 526 = Time Played minutes). */
    int varcInt(int id);

    // ── Movement ─────────────────────────────────────────────────────────────────────────────
    boolean walkTo(Pos target);
    boolean arrived(Pos target, int radius);
    double distanceTo(Pos target);

    // ── Inventory ────────────────────────────────────────────────────────────────────────────
    boolean invContains(int itemId);
    boolean invContains(String itemName);
    int invCount(int itemId);
    boolean invIsFull();
    int invEmptySlots();
    boolean invInteract(int itemId, String action);
    /** Use one carried item on another (e.g. tinderbox on logs). */
    boolean useItemOnItem(int itemId1, int itemId2);
    boolean invDropAll(int... itemIds);
    boolean invDropAllExcept(int... keepItemIds);
    /** Names of all carried items (for name-substring checks like "any pickaxe"). */
    List<String> inventoryItemNames();
    /** Item ids of all carried items (for junk detection). */
    List<Integer> inventoryItemIds();

    // ── Equipment ────────────────────────────────────────────────────────────────────────────
    boolean isWearing(int itemId);
    boolean equip(int itemId);
    List<String> equipmentItemNames();

    // ── Banking ──────────────────────────────────────────────────────────────────────────────
    boolean bankIsOpen();
    boolean openNearestBank();
    boolean closeBank();
    int bankCount(int itemId);
    boolean bankContains(int itemId);
    boolean withdraw(int itemId, int amount);
    boolean withdrawAll(int itemId);
    boolean deposit(int itemId, int amount);
    boolean depositAllExcept(int... keepItemIds);
    boolean depositInventory();
    java.util.Map<Integer, Integer> bankSnapshot();

    // ── Objects ──────────────────────────────────────────────────────────────────────────────
    /** All game objects within {@code tiles} of the player (for logic-side filtering). */
    List<GameObj> objectsWithin(int tiles);
    /** Convenience: nearest object matching any of {@code names}, or null. */
    GameObj nearestObject(String... names);
    GameObj nearestObjectById(int... ids);
    boolean interactObject(GameObj obj, String action);

    // ── NPCs ─────────────────────────────────────────────────────────────────────────────────
    List<Npc> npcsWithin(int tiles);
    Npc nearestNpc(String... names);
    boolean interactNpc(Npc npc, String action);

    // ── Ground items ─────────────────────────────────────────────────────────────────────────
    List<GroundItem> groundItemsWithin(int tiles);

    // ── Dialogue ─────────────────────────────────────────────────────────────────────────────
    boolean dialogueOpen();
    boolean hasDialogueOptions();
    List<String> dialogueOptions();
    boolean selectDialogueOption(int oneBasedIndex);
    boolean continueDialogue();

    // ── Widgets / interfaces (make screens, GE, etc.) ─────────────────────────────────────────
    boolean widgetVisible(int groupId, int childId);
    String widgetText(int groupId, int childId);
    boolean interactWidget(int groupId, int childId, String action);
    /** The shared "make" popup (cook/smelt/smith/craft) is up. */
    boolean makeScreenOpen();
    /** A product button matching {@code productName} is present in the make popup. */
    boolean makeScreenHas(String productName);
    /** Select the quantity and click the product in the make popup. */
    boolean clickMake(String productName, int quantity);

    // ── Grand Exchange ───────────────────────────────────────────────────────────────────────
    boolean geOpen();
    boolean openGe();
    boolean geBuy(int itemId, int quantity, int unitPrice);
    boolean geSell(int itemId, int quantity, int unitPrice);
    boolean geCollectAll();
    boolean geCollectToBank();
    boolean geClose();
    boolean geReadyToCollect();
    int geUsedSlots();
    List<GeOffer> geOffers();
    boolean geCancel(int slot);

    // ── Shops ────────────────────────────────────────────────────────────────────────────────
    boolean shopIsOpen();
    boolean openShop(String npcName);
    boolean closeShop();
    boolean shopPurchase(int itemId, int qty);
    boolean shopPurchase(String itemName, int qty);
    /** Open shop's stock as "name#id x amount" strings (for the live-debug dump). */
    List<String> shopStock();

    // ── Session (breaks) ─────────────────────────────────────────────────────────────────────
    boolean logout();
    boolean login();

    // ── Timing ───────────────────────────────────────────────────────────────────────────────
    void sleep(int ms);
    boolean waitUntil(java.util.function.BooleanSupplier cond, int timeoutMs);

    // ── Value types ──────────────────────────────────────────────────────────────────────────
    // Pos is a top-level class in this package (game/Pos.java).

    /** Rich handle to a game object. */
    interface GameObj {
        int id(); String name(); Pos position(); double distance();
        boolean hasAction(String action);
        boolean exists();
        boolean interact(String action);
        /** Use a carried inventory item on this object (item→object). */
        boolean useItem(int itemId);
    }
    /** Rich handle to an NPC. */
    interface Npc {
        int id(); String name(); Pos position(); double distance();
        boolean hasAction(String action);
        boolean interact(String action);
        boolean interactingWithMe();
        /** Use a carried inventory item on this NPC (item→NPC). */
        boolean useItem(int itemId);
    }
    /** Rich handle to a ground item. */
    interface GroundItem {
        int id(); String name(); Pos position(); double distance(); int quantity();
        boolean take();
    }
    /** A Grand Exchange offer slot in use. */
    interface GeOffer {
        int itemId(); int slot();
        boolean buy(); boolean sell();
        boolean readyToCollect();
        int transferredAmount(); long transferredValue();
    }
}
