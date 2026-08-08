package net.runelite.client.plugins.microbot.quinnmain.game;

import java.util.List;

/**
 * <h2>The client-neutral game facade — the whole point of the port.</h2>
 *
 * Every trainer, quest, combat routine and money method in Quinn Main talks to the game through
 * <b>this interface only</b>, never through DreamBot's {@code org.dreambot.*} or Microbot's
 * {@code Rs2*} classes directly. That is what lets the same logic (coords, tables, state machines)
 * run on either client: you swap the {@link GameApi} implementation, not the logic.
 *
 * <ul>
 *   <li>{@code DreamBotGameApi} — implemented in OSRS-Main by delegating to {@code Nav}/{@code Bank}/… (retrofit).</li>
 *   <li>{@link MicrobotGameApi} — implemented here by delegating to the static {@code Rs2*} utilities.</li>
 * </ul>
 *
 * <p><b>Coordinates.</b> The facade speaks {@link Pos} (a plain x/y/plane triple), NOT DreamBot
 * {@code Tile} or RuneLite {@code WorldPoint}. Both have the same game coordinate values, so every
 * tile/area constant in the ported logic carries over unchanged — the adapter just converts
 * {@code Pos} ↔ the client's own point type at the boundary.
 *
 * <p><b>This interface grows with the port.</b> It currently covers the surface the vertical-slice
 * proof needs plus the obvious neighbours. As each subsystem is ported (see PORT_PLAN.md), add the
 * methods it needs here and implement them in every adapter. Keep it small and behavioural — model
 * <i>what the bot wants to do</i>, not the client's raw API.
 */
public interface GameApi {

    // ── Local player ─────────────────────────────────────────────────────────────────────────
    boolean isLoggedIn();
    Pos playerPosition();
    boolean isMoving();
    boolean isAnimating();
    int healthPercent();
    int runEnergy();
    /** Current (possibly boosted) level of a skill, by its canonical OSRS name e.g. "WOODCUTTING". */
    int skillLevel(String skill);
    int skillLevelReal(String skill);
    long skillXp(String skill);

    // ── Movement ─────────────────────────────────────────────────────────────────────────────
    /** Walk toward a tile (web-walks if far). Returns once the walk has been kicked off. */
    boolean walkTo(Pos target);
    /** True once within {@code radius} tiles of {@code target}. */
    boolean arrived(Pos target, int radius);
    double distanceTo(Pos target);

    // ── Inventory ────────────────────────────────────────────────────────────────────────────
    boolean invContains(int itemId);
    boolean invContains(String itemName);
    int invCount(int itemId);
    boolean invIsFull();
    int invEmptySlots();
    boolean invInteract(int itemId, String action);
    boolean invDropAll(int... itemIds);

    // ── Equipment ────────────────────────────────────────────────────────────────────────────
    boolean isWearing(int itemId);
    boolean equip(int itemId);

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

    // ── Objects (trees, rocks, banks, doors, altars…) ────────────────────────────────────────
    /** Nearest reachable game object whose name matches any of {@code names}, or null. */
    GameObj nearestObject(String... names);
    GameObj nearestObjectById(int... ids);
    boolean interactObject(GameObj obj, String action);

    // ── NPCs ─────────────────────────────────────────────────────────────────────────────────
    Npc nearestNpc(String... names);
    boolean interactNpc(Npc npc, String action);
    boolean isInteractingWithMe(Npc npc);

    // ── Ground items ─────────────────────────────────────────────────────────────────────────
    GroundItem nearestGroundItem(int maxTiles, int... itemIds);
    boolean takeGroundItem(GroundItem item);

    // ── Dialogue ─────────────────────────────────────────────────────────────────────────────
    boolean dialogueOpen();
    boolean hasDialogueOptions();
    /** The currently offered dialogue option strings, in order. */
    List<String> dialogueOptions();
    boolean selectDialogueOption(int oneBasedIndex);
    boolean continueDialogue();

    // ── Widgets / interfaces (make screens, GE, etc.) ─────────────────────────────────────────
    boolean widgetVisible(int groupId, int childId);
    String widgetText(int groupId, int childId);
    boolean interactWidget(int groupId, int childId, String action);

    // ── Grand Exchange ───────────────────────────────────────────────────────────────────────
    boolean geOpen();
    boolean openGe();
    boolean geBuy(int itemId, int quantity, int unitPrice);
    boolean geSell(int itemId, int quantity, int unitPrice);
    boolean geCollectAll();

    // ── Shops ────────────────────────────────────────────────────────────────────────────────
    boolean shopOpen();
    boolean shopBuy(String itemName, int quantity);

    // ── Timing (client-driven waits, not Thread.sleep in logic) ──────────────────────────────
    void sleep(int ms);
    /** Block up to {@code timeoutMs} until {@code cond} is true; returns whether it became true. */
    boolean waitUntil(java.util.function.BooleanSupplier cond, int timeoutMs);

    // ── Value types ──────────────────────────────────────────────────────────────────────────

    /** Client-neutral world coordinate. Same values as DreamBot Tile / RuneLite WorldPoint. */
    final class Pos {
        public final int x, y, plane;
        public Pos(int x, int y, int plane) { this.x = x; this.y = y; this.plane = plane; }
        public Pos(int x, int y) { this(x, y, 0); }
        @Override public String toString() { return "(" + x + "," + y + "," + plane + ")"; }
    }

    /** Opaque handle to a game object; the adapter knows its concrete client type. */
    interface GameObj { Pos position(); int id(); String name(); }
    /** Opaque handle to an NPC. */
    interface Npc { Pos position(); int id(); String name(); }
    /** Opaque handle to a ground item. */
    interface GroundItem { Pos position(); int id(); int quantity(); }
}
