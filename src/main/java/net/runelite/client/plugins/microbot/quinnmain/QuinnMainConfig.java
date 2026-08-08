package net.runelite.client.plugins.microbot.quinnmain;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

/**
 * RuneLite config for the Quinn Main port. Still minimal — enough to drive the two proven trainers
 * (Woodcutting, Mining) through the shared {@link net.runelite.client.plugins.microbot.quinnmain.slice.GatherLoop}.
 * The full Control Panel (a Swing window in OSRS-Main) is a later port target; on Microbot its natural
 * home is a RuneLite overlay + this config panel (see PORT_PLAN.md §UI).
 */
@ConfigGroup("quinnmain")
public interface QuinnMainConfig extends Config {

    /** Which trainer the loop runs. Grows into the weighted GoalEngine once ported. */
    enum Task { WOODCUTTING, MINING }

    @ConfigItem(keyName = "task", name = "Task", position = 0,
            description = "Which trainer to run.")
    default Task task() { return Task.WOODCUTTING; }

    @ConfigItem(keyName = "bankWhenFull", name = "Bank when full", position = 1,
            description = "On: bank the product at the nearest bank when full. Off: drop it (power-train).")
    default boolean bankWhenFull() { return false; }

    // ── Woodcutting ──────────────────────────────────────────────────────────────────────────
    @ConfigSection(name = "Woodcutting", description = "Woodcutting settings", position = 2)
    String WC = "woodcutting";

    @ConfigItem(keyName = "treeName", name = "Tree name", position = 0, section = WC,
            description = "Name of the tree object (e.g. Tree, Oak, Willow).")
    default String treeName() { return "Tree"; }

    @ConfigItem(keyName = "logId", name = "Log item id", position = 1, section = WC,
            description = "Item id of the log produced (normal logs = 1511).")
    default int logId() { return 1511; }

    @ConfigItem(keyName = "spotX", name = "Tree spot X", position = 2, section = WC,
            description = "World X of the tree area.")
    default int spotX() { return 3159; }

    @ConfigItem(keyName = "spotY", name = "Tree spot Y", position = 3, section = WC,
            description = "World Y of the tree area.")
    default int spotY() { return 3406; }

    // ── Mining ───────────────────────────────────────────────────────────────────────────────
    @ConfigSection(name = "Mining", description = "Mining settings", position = 3)
    String MINE = "mining";

    @ConfigItem(keyName = "rockName", name = "Rock name", position = 0, section = MINE,
            description = "Name of the rock object (e.g. Copper rocks, Tin rocks, Iron rocks).")
    default String rockName() { return "Copper rocks"; }

    @ConfigItem(keyName = "oreId", name = "Ore item id", position = 1, section = MINE,
            description = "Item id of the ore produced (copper ore = 436).")
    default int oreId() { return 436; }

    @ConfigItem(keyName = "mineX", name = "Mine spot X", position = 2, section = MINE,
            description = "World X of the mining area.")
    default int mineX() { return 3180; }

    @ConfigItem(keyName = "mineY", name = "Mine spot Y", position = 3, section = MINE,
            description = "World Y of the mining area.")
    default int mineY() { return 3372; }
}
