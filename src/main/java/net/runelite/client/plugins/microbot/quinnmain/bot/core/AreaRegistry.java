package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.fishing.FishMethod;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.fishing.FishingArea;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.mining.MiningArea;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.mining.OreType;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.woodcutting.TreeType;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.woodcutting.WoodcuttingArea;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The areas (in-game locations) where a given activity can be performed — the source of truth for the
 * control panel's <b>area picker</b>, and the same keys {@link ConfigStore#isAreaEnabled} persists.
 *
 * <p>Only exposes areas the trainer can <b>actually use</b>. Mining, Woodcutting and Fishing are wired
 * for real multi-area selection: each ore/tree/method returns its {@link MiningArea}/{@link
 * WoodcuttingArea}/{@link FishingArea} list, and the trainer walks to whichever the user leaves enabled.
 * Every other activity has one fixed location today, so it returns a single "Primary location" area —
 * honest (there's one place) rather than a picker full of choices the bot would ignore. As a trainer
 * gains real alternate locations, it's added here.
 */
public final class AreaRegistry {

    private AreaRegistry() { }

    /** One selectable area: a stable config key + a human label. */
    public static final class Area {
        public final String key;
        public final String label;
        Area(String key, String label) { this.key = key; this.label = label; }
    }

    /** The single fallback area for activities with one fixed location. */
    public static final String PRIMARY = "PRIMARY";
    private static final List<Area> SINGLE =
            Collections.singletonList(new Area(PRIMARY, "Primary location"));

    /** Areas for {@code activityKey} under {@code skill}; never empty. */
    public static List<Area> areasFor(Sk skill, String activityKey) {
        if (skill == Sk.MINING) {
            OreType ore = OreType.parse(activityKey);
            if (ore != null && ore.areas != null && ore.areas.length > 0) {
                List<Area> out = new ArrayList<>();
                for (MiningArea a : ore.areas) out.add(new Area(a.name(), a.label));
                return out;
            }
        } else if (skill == Sk.WOODCUTTING) {
            TreeType tree = TreeType.parse(activityKey);
            if (tree != null && tree.areas != null && tree.areas.length > 0) {
                List<Area> out = new ArrayList<>();
                for (WoodcuttingArea a : tree.areas) out.add(new Area(a.name(), a.label));
                return out;
            }
        } else if (skill == Sk.FISHING) {
            FishMethod m = FishMethod.parse(activityKey);
            if (m != null && m.areas != null && m.areas.length > 0) {
                List<Area> out = new ArrayList<>();
                for (FishingArea a : m.areas) out.add(new Area(a.name(), a.label));
                return out;
            }
        }
        return SINGLE;
    }

    /** True if this activity offers a genuine choice of areas (more than the single default). */
    public static boolean hasRealAreas(Sk skill, String activityKey) {
        return areasFor(skill, activityKey).size() > 1;
    }
}
