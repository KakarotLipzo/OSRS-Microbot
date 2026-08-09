package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.cooking.RawFood;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.fishing.FishMethod;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.mining.OreType;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.smithing.BarType;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.woodcutting.TreeType;


import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the item(s) a skill's activity produces, so a plan "get N items" step can be counted against
 * what we hold. Only the gathering / production skills yield a countable item — combat, firemaking (burns
 * logs), agility, etc. produce nothing, so {@link #hasOutput} is false and those skills don't offer an
 * "Amount" plan condition. Derived from the same trainer enums as {@link ActivityRegistry}.
 */
public final class SkillOutput {

    private SkillOutput() { }

    private static final int[] NONE = new int[0];

    /** True if this skill produces a countable item (so an "Amount" plan target makes sense). */
    public static boolean hasOutput(Sk s) {
        return s == Sk.MINING || s == Sk.WOODCUTTING || s == Sk.FISHING
                || s == Sk.COOKING || s == Sk.SMITHING;
    }

    /**
     * The item ids a (skill, activity) yields. A null/blank activity means "any" — all of the skill's
     * outputs (so an Any-activity step counts every ore / log / fish / … the skill can produce).
     */
    public static int[] itemIds(Sk s, String activity) {
        boolean any = activity == null || activity.trim().isEmpty();
        List<Integer> out = new ArrayList<>();
        switch (s) {
            case MINING:
                for (OreType o : OreType.values()) if (any || o.name().equalsIgnoreCase(activity)) out.add(o.oreId);
                break;
            case WOODCUTTING:
                for (TreeType t : TreeType.values()) if (any || t.name().equalsIgnoreCase(activity)) out.add(t.logId);
                break;
            case FISHING:
                for (FishMethod f : FishMethod.values()) if (any || f.name().equalsIgnoreCase(activity)) for (int id : f.fishIds()) out.add(id);
                break;
            case COOKING:
                for (RawFood f : RawFood.values()) if (any || f.name().equalsIgnoreCase(activity)) out.add(f.cookedId);
                break;
            case SMITHING:
                for (BarType b : BarType.values()) if (any || b.name().equalsIgnoreCase(activity)) out.add(b.barId);
                break;
            default:
                return NONE;
        }
        int[] arr = new int[out.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
        return arr;
    }
}
