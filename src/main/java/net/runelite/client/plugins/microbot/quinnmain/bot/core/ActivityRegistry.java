package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.combat.MonsterType;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.cooking.RawFood;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.crafting.CraftMethod;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.firemaking.LogType;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.fishing.FishMethod;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.mining.OreType;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.smithing.BarType;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.woodcutting.TreeType;


import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The single source of truth for "what can this bot actually do for skill X" — the list the control
 * panel's activity chips are built from, and the same list the trainers filter against.
 *
 * <p>Deliberately derived from the <b>real</b> trainer enums ({@link OreType}, {@link TreeType}, …)
 * rather than a hand-written menu. The control-panel design handoff shipped placeholder activity
 * lists (Sand crabs, Nightmare zone, Coal, Mithril, …) that this bot has no modules for; showing
 * those would be a control that silently does nothing on an unattended run. A skill with no trainer
 * yields an empty list, and the panel renders an honest empty state for it.
 *
 * <p>Keys are the enum constant names ({@code "COPPER"}, {@code "GOLD_JEWELRY"}) — stable across
 * renames of the display label, and what {@link ConfigStore#isActivityEnabled} persists.
 */
public final class ActivityRegistry {

    private ActivityRegistry() { }

    /** One selectable activity: a stable config key plus a human label for the chip. */
    public static final class Activity {
        public final String key;
        public final String label;
        /** Level required, or 0 if it isn't level-gated. Shown as a hint, never used to hide a chip. */
        public final int level;

        Activity(String key, String label, int level) {
            this.key = key; this.label = label; this.level = level;
        }
        @Override public String toString() { return label; }
    }

    private static final Map<Sk, List<Activity>> BY_SKILL = new EnumMap<>(Sk.class);

    static {
        List<Activity> woodcutting = new ArrayList<>();
        for (TreeType t : TreeType.values()) woodcutting.add(new Activity(t.name(), title(t.name()), t.wcLevel));
        BY_SKILL.put(Sk.WOODCUTTING, woodcutting);

        List<Activity> fishing = new ArrayList<>();
        for (FishMethod f : FishMethod.values()) fishing.add(new Activity(f.name(), title(f.name()), f.fishLevel));
        BY_SKILL.put(Sk.FISHING, fishing);

        List<Activity> cooking = new ArrayList<>();
        for (RawFood f : RawFood.values()) cooking.add(new Activity(f.name(), f.rawName, f.cookLevel));
        BY_SKILL.put(Sk.COOKING, cooking);

        List<Activity> mining = new ArrayList<>();
        for (OreType o : OreType.values()) mining.add(new Activity(o.name(), o.oreName, o.miningLevel));
        BY_SKILL.put(Sk.MINING, mining);

        List<Activity> smithing = new ArrayList<>();
        for (BarType b : BarType.values()) smithing.add(new Activity(b.name(), b.barName, b.smithLevel));
        BY_SKILL.put(Sk.SMITHING, smithing);

        List<Activity> firemaking = new ArrayList<>();
        for (LogType l : LogType.values()) firemaking.add(new Activity(l.name(), l.logName, l.fmLevel));
        BY_SKILL.put(Sk.FIREMAKING, firemaking);

        List<Activity> crafting = new ArrayList<>();
        for (CraftMethod m : CraftMethod.values()) crafting.add(new Activity(m.name(), title(m.name()), 0));
        BY_SKILL.put(Sk.CRAFTING, crafting);

        // The combat skills all pick from the same monster table via the shared engine.
        List<Activity> monsters = new ArrayList<>();
        for (MonsterType m : MonsterType.values()) monsters.add(new Activity(m.name(), title(m.name()), m.minCombat));
        for (Sk s : new Sk[]{Sk.ATTACK, Sk.STRENGTH, Sk.DEFENCE, Sk.RANGED, Sk.MAGIC}) {
            BY_SKILL.put(s, monsters);
        }
    }

    /** Activities for {@code s}, or an empty list when no trainer exists for it yet. */
    public static List<Activity> forSkill(Sk s) {
        List<Activity> l = BY_SKILL.get(s);
        return l == null ? Collections.emptyList() : Collections.unmodifiableList(l);
    }

    public static boolean hasActivities(Sk s) { return !forSkill(s).isEmpty(); }

    /** "GOLD_JEWELRY" → "Gold jewelry", "AL_KHARID_WARRIOR" → "Al kharid warrior". */
    private static String title(String enumName) {
        String s = enumName.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
