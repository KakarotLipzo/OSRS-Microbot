package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Static metadata about OSRS skills: which are members-only, and which we manage toward a target
 * level. Ported from OSRS-Main verbatim except DreamBot {@code Skill} → neutral {@link Sk}.
 */
public final class SkillCatalog {

    private SkillCatalog() {}

    /** The 8 members-only skills. On an F2P account these are never selected for training. */
    public static final Set<Sk> MEMBERS = Collections.unmodifiableSet(EnumSet.of(
            Sk.AGILITY, Sk.HERBLORE, Sk.THIEVING, Sk.FLETCHING,
            Sk.SLAYER, Sk.FARMING, Sk.CONSTRUCTION, Sk.HUNTER));

    /** Skills we track a target/weight for. Excludes SAILING. */
    public static final List<Sk> TRAINABLE = Collections.unmodifiableList(Arrays.asList(
            Sk.ATTACK, Sk.STRENGTH, Sk.DEFENCE, Sk.HITPOINTS, Sk.RANGED,
            Sk.PRAYER, Sk.MAGIC, Sk.COOKING, Sk.WOODCUTTING, Sk.FISHING,
            Sk.FIREMAKING, Sk.CRAFTING, Sk.SMITHING, Sk.MINING, Sk.RUNECRAFTING,
            Sk.AGILITY, Sk.HERBLORE, Sk.THIEVING, Sk.FLETCHING, Sk.SLAYER,
            Sk.FARMING, Sk.CONSTRUCTION, Sk.HUNTER));

    public static boolean isMembers(Sk s) { return MEMBERS.contains(s); }
}
