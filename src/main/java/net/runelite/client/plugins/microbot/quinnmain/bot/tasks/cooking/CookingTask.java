package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.cooking;

import net.runelite.client.plugins.microbot.quinnmain.bot.core.Aggression;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.MakeInterface;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.Nav;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.TaskContext;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.SkillTask;
import net.runelite.client.plugins.microbot.quinnmain.game.Game;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi.GameObj;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.game.Sk;

import java.util.function.Predicate;

/**
 * Cooking trainer — a processing skill that cooks the raw fish the Fishing trainer banks. Ported to the
 * {@link GameApi} facade. Highest-effective-XP raw food it can cook, withdraw a batch → cook on the
 * range (make screen) → bank → repeat. Al Kharid range when combat-safe, else Lumbridge.
 */
public class CookingTask extends SkillTask {

    private static final BankLoc AL_KHARID_BANK = BankLoc.AL_KHARID;
    private static final Pos AL_KHARID_RANGE = new Pos(3273, 3180, 0);
    private static final BankLoc LUMBRIDGE_BANK = BankLoc.LUMBRIDGE;
    private static final Pos LUMBRIDGE_RANGE = new Pos(3211, 3215, 0);
    private static final long NO_FOOD_COOLDOWN_MS = 10 * 60 * 1000L;

    private long noFoodUntil = 0;

    private static GameApi g() { return Game.api(); }
    private static boolean safeAtAlKharid() { return Aggression.safeFrom(Aggression.AL_KHARID_WARRIOR); }
    private static BankLoc bankNow() { return safeAtAlKharid() ? AL_KHARID_BANK : LUMBRIDGE_BANK; }
    private static Pos rangeNow() { return safeAtAlKharid() ? AL_KHARID_RANGE : LUMBRIDGE_RANGE; }

    private GameObj closestObj(Predicate<GameObj> pred, int radius) {
        GameApi a = g(); if (a == null) return null;
        GameObj best = null; double bd = Double.MAX_VALUE;
        for (GameObj o : a.objectsWithin(radius)) {
            if (o == null) continue;
            try { if (!pred.test(o)) continue; } catch (Throwable t) { continue; }
            double d = o.distance();
            if (d < bd) { bd = d; best = o; }
        }
        return best;
    }

    @Override public Sk skill() { return Sk.COOKING; }
    @Override public String name() { return "Cooking"; }

    @Override
    public boolean isDoable(TaskContext ctx) {
        if (System.currentTimeMillis() < noFoodUntil) return false;
        if (ctx.bank.hasSeenBank() && !hasRawFoodAnywhere(ctx)) return false;
        return true;
    }

    private boolean hasRawFoodAnywhere(TaskContext ctx) {
        int lvl = ctx.account.level(Sk.COOKING);
        for (RawFood f : RawFood.values()) {
            if (f.cookLevel > lvl) continue;
            if (ctx.bank.has(f.rawId) || g().invContains(f.rawId)) return true;
        }
        return false;
    }

    @Override
    public int execute(TaskContext ctx) {
        GameApi a = g(); if (a == null) return 600;
        if (bestRawInInventory(ctx) != null) return cookAtRange(ctx);
        return restock(ctx);
    }

    private int cookAtRange(TaskContext ctx) {
        GameApi a = g();
        if (a.continueDialogue()) return 700;   // clear level-up / make prompts
        if (a.isAnimating()) return smallSleep();

        RawFood food = bestRawInInventory(ctx);
        if (food == null) return 600;

        if (MakeInterface.click(null) != null) {  // make screen already up → cook-all
            ctx.log("[cook] clicking make button.");
            a.waitUntil(a::isAnimating, 3000);
            return 800;
        }

        if (!a.invContains(food.rawId)) return 600;
        GameObj range = findRange();
        if (range == null) { ctx.log("[cook] no range nearby; walking to " + bankNow() + " range spot."); Nav.walkTo(rangeNow()); return 600; }
        if (range.distance() > 1) { Nav.walkTo(range.position()); return 600; }

        if (range.useItem(food.rawId)) {
            ctx.log("[cook] using " + food.rawName + " on " + range.name() + "#" + range.id());
            a.waitUntil(() -> a.isAnimating() || a.dialogueOpen() || MakeInterface.isOpen(), 4000);
        }
        return smallSleep();
    }

    private GameObj findRange() {
        return closestObj(o -> o.name() != null
                && (o.name().toLowerCase().contains("range") || o.name().toLowerCase().contains("stove")), 8);
    }

    private int restock(TaskContext ctx) {
        GameApi a = g();
        if (!a.bankIsOpen()) { if (!Nav.openBank(bankNow())) return 600; return 400; }
        RawFood food = bestRawAvailable(ctx);
        if (food == null) {
            noFoodUntil = System.currentTimeMillis() + NO_FOOD_COOLDOWN_MS;
            ctx.log("[cook] no cookable raw food in bank — pausing Cooking for ~10 min.");
            a.closeBank(); return 3000;
        }
        a.depositInventory();
        a.waitUntil(() -> a.invEmptySlots() >= 28, 2000);
        a.withdrawAll(food.rawId);
        a.waitUntil(() -> a.invContains(food.rawId), 2000);
        ctx.log("[cook] withdrew " + food.rawName + " to cook.");
        a.closeBank();
        return 800;
    }

    private RawFood bestRawInInventory(TaskContext ctx) {
        GameApi a = g();
        int lvl = ctx.account.level(Sk.COOKING);
        RawFood best = null;
        for (RawFood f : RawFood.values()) {
            if (f.cookLevel > lvl) continue;
            if (!a.invContains(f.rawId)) continue;
            if (!ctx.config.isActivityEnabled(Sk.COOKING, f.name())) continue;
            if (best == null || f.effectiveScore(lvl) > best.effectiveScore(lvl)) best = f;
        }
        return best;
    }

    private RawFood bestRawAvailable(TaskContext ctx) {
        GameApi a = g();
        int lvl = ctx.account.level(Sk.COOKING);
        RawFood best = null;
        for (RawFood f : RawFood.values()) {
            if (f.cookLevel > lvl) continue;
            if (!a.bankContains(f.rawId) && !a.invContains(f.rawId)) continue;
            if (!ctx.config.isActivityEnabled(Sk.COOKING, f.name())) continue;
            if (best == null || f.effectiveScore(lvl) > best.effectiveScore(lvl)) best = f;
        }
        return best;
    }

    private int smallSleep() { return 480 + (int) (Math.random() * 420); }
}
