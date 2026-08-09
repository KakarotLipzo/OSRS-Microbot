package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.combat;

import net.runelite.client.plugins.microbot.quinnmain.bot.core.TaskContext;
import net.runelite.client.plugins.microbot.quinnmain.game.Game;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;
import net.runelite.client.plugins.microbot.quinnmain.game.Sk;

/**
 * Prayer management for combat (F2P + members). Opt-in protection ({@code prayer.useProtection}) and
 * offensive stat prayers ({@code prayer.useOffensive}). Ported to the {@link GameApi} facade with a
 * neutral {@link PrayerType}. Only toggles prayers in {@link #MANAGED}, so it never fights manual choices.
 */
public class PrayerManager {

    private static final PrayerType[] MANAGED = {
            PrayerType.PROTECT_FROM_MELEE,
            PrayerType.CLARITY_OF_THOUGHT, PrayerType.IMPROVED_REFLEXES, PrayerType.INCREDIBLE_REFLEXES,
            PrayerType.BURST_OF_STRENGTH, PrayerType.SUPERHUMAN_STRENGTH, PrayerType.ULTIMATE_STRENGTH,
            PrayerType.THICK_SKIN, PrayerType.ROCK_SKIN, PrayerType.STEEL_SKIN,
            PrayerType.CHIVALRY, PrayerType.PIETY,
            PrayerType.SHARP_EYE, PrayerType.HAWK_EYE, PrayerType.EAGLE_EYE, PrayerType.RIGOUR,
            PrayerType.MYSTIC_WILL, PrayerType.MYSTIC_LORE, PrayerType.MYSTIC_MIGHT, PrayerType.AUGURY,
    };

    private static GameApi g() { return Game.api(); }

    public int handle(TaskContext ctx, Sk trainingSkill, MonsterType target) {
        GameApi a = g(); if (a == null) return 0;
        PrayerType wantProtect = null, wantOffensive = null;

        if (ctx.config.isPrayerEnabled() && points() > 0) {
            int lvl = ctx.account.level(Sk.PRAYER);
            boolean members = ctx.account.isMembers();
            if (ctx.config.isPrayerUseProtection() && target != null && target.dangerous && lvl >= PrayerType.PROTECT_FROM_MELEE.level) {
                wantProtect = PrayerType.PROTECT_FROM_MELEE;
            }
            if (ctx.config.isPrayerUseOffensive()) wantOffensive = bestOffensive(trainingSkill, lvl, members);
        }

        boolean acted = false;
        for (PrayerType p : MANAGED) {
            boolean want = (p == wantProtect) || (p == wantOffensive);
            boolean active = a.isPrayerActive(p.name());
            if (want && !active) { if (a.setPrayer(p.name(), true)) { ctx.log("[prayer] " + p + " on."); acted = true; } }
            else if (!want && active) { a.setPrayer(p.name(), false); acted = true; }
        }
        if (acted) { a.sleep(350); return 400; }
        return 0;
    }

    private PrayerType bestOffensive(Sk skill, int prayerLvl, boolean members) {
        if (skill == Sk.RANGED) {
            if (members && prayerLvl >= PrayerType.RIGOUR.level) return PrayerType.RIGOUR;
            return highest(prayerLvl, PrayerType.EAGLE_EYE, PrayerType.HAWK_EYE, PrayerType.SHARP_EYE);
        }
        if (skill == Sk.MAGIC) {
            if (members && prayerLvl >= PrayerType.AUGURY.level) return PrayerType.AUGURY;
            return highest(prayerLvl, PrayerType.MYSTIC_MIGHT, PrayerType.MYSTIC_LORE, PrayerType.MYSTIC_WILL);
        }
        if (members && prayerLvl >= PrayerType.PIETY.level) return PrayerType.PIETY;
        if (members && prayerLvl >= PrayerType.CHIVALRY.level) return PrayerType.CHIVALRY;
        if (skill == Sk.ATTACK) return highest(prayerLvl, PrayerType.INCREDIBLE_REFLEXES, PrayerType.IMPROVED_REFLEXES, PrayerType.CLARITY_OF_THOUGHT);
        if (skill == Sk.DEFENCE) return highest(prayerLvl, PrayerType.STEEL_SKIN, PrayerType.ROCK_SKIN, PrayerType.THICK_SKIN);
        return highest(prayerLvl, PrayerType.ULTIMATE_STRENGTH, PrayerType.SUPERHUMAN_STRENGTH, PrayerType.BURST_OF_STRENGTH);
    }

    private PrayerType highest(int prayerLvl, PrayerType... best2worst) {
        for (PrayerType p : best2worst) if (prayerLvl >= p.level) return p;
        return null;
    }

    /** Boosted Prayer level = remaining prayer points (0 when drained). */
    private int points() { GameApi a = g(); try { return a == null ? 0 : a.skillLevel("PRAYER"); } catch (Throwable e) { return 0; } }
}
