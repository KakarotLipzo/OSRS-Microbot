package net.runelite.client.plugins.microbot.quinnmain;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

/**
 * RuneLite config for the Quinn Main port. Minimal for now — just what the woodcutting vertical slice
 * needs to prove the plugin ↔ facade ↔ Rs2* chain end to end. The full Control Panel (a Swing window
 * today) is a later port target; on Microbot its natural home is a RuneLite overlay + this config
 * panel (see PORT_PLAN.md §UI).
 */
@ConfigGroup("quinnmain")
public interface QuinnMainConfig extends Config {

    @ConfigItem(keyName = "treeName", name = "Tree name", position = 0,
            description = "Name of the tree object to chop (e.g. Tree, Oak, Willow).")
    default String treeName() { return "Tree"; }

    @ConfigItem(keyName = "logId", name = "Log item id", position = 1,
            description = "Item id of the log produced (normal logs = 1511).")
    default int logId() { return 1511; }

    @ConfigItem(keyName = "bankWhenFull", name = "Bank when full", position = 2,
            description = "On: bank logs at the nearest bank when full. Off: drop them (power-chop).")
    default boolean bankWhenFull() { return false; }

    @ConfigItem(keyName = "spotX", name = "Tree spot X", position = 3,
            description = "World X of the tree area to walk to.")
    default int spotX() { return 3159; }

    @ConfigItem(keyName = "spotY", name = "Tree spot Y", position = 4,
            description = "World Y of the tree area to walk to.")
    default int spotY() { return 3406; }
}
