package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.mining;

import net.runelite.client.plugins.microbot.quinnmain.bot.core.Nav;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.PriceLookup;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.SupplyBuy;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.TaskContext;
import net.runelite.client.plugins.microbot.quinnmain.bot.tasks.SkillTask;
import net.runelite.client.plugins.microbot.quinnmain.game.Game;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi.GameObj;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.game.Sk;

import java.util.function.Predicate;

/**
 * Mining trainer — ore rocks matched by object ID (all rocks are named "Rocks"), auto-progress to the
 * best-XP ore the level allows, best usable pickaxe (wield/carry/bank-upgrade/GE-buy), bank-or-drop when
 * full. Ported from OSRS-Main to the {@link GameApi} facade.
 */
public class MiningTask extends SkillTask {

    private static final Pos GE_TILE = new Pos(3164, 3486, 0);
    private static final int AREA_RADIUS = 12;
    private long lastRockScan = 0;
    private Pos targetRockTile;
    private OreType lowOreChoice;

    private static GameApi g() { return Game.api(); }

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

    @Override public Sk skill() { return Sk.MINING; }
    @Override public String name() { return "Mining"; }

    @Override
    public boolean isDoable(TaskContext ctx) {
        int target = Math.max(ctx.config.getSkillQuantity(Sk.MINING), ctx.config.getMiningQuantity());
        if (target > 0) {
            OreType ore = chooseOre(ctx);
            if (ore != null && ctx.bank.count(ore.oreId) + inv(ore.oreId) >= target) return false;
        }
        return true;
    }

    @Override
    public int execute(TaskContext ctx) {
        GameApi a = g(); if (a == null) return 600;

        OreType ore = chooseOre(ctx);
        if (ore == null) { ctx.log("[mine] no ore available for this account/level."); return 2000; }

        if (a.invIsFull()) return ctx.config.isMiningPowerDrop() ? dropOre(ctx, ore) : doBank(ctx, ore);

        int gearStep = ensurePickaxe(ctx);
        if (gearStep > 0) return gearStep;

        if (hasJunk(ctx, ore)) { ctx.log("[mine] depositing non-mining items before mining."); return doBank(ctx, ore); }

        return mine(ctx, ore);
    }

    private static int inv(int id) { GameApi a = g(); return a == null ? 0 : a.invCount(id); }

    private boolean hasJunk(TaskContext ctx, OreType ore) {
        PickaxeTier held = bestHeldUsable(ctx);
        for (int id : g().inventoryItemIds()) {
            if (held != null && id == held.itemId) continue;
            if (id == ore.oreId) continue;
            if (isAnyPickaxe(id)) continue;
            return true;
        }
        return false;
    }

    private boolean isAnyPickaxe(int itemId) {
        for (PickaxeTier p : PickaxeTier.values()) if (p.itemId == itemId) return true;
        return false;
    }

    // ── ore selection ─────────────────────────────────────────────────────────────────────────
    private OreType chooseOre(TaskContext ctx) {
        int lvl = ctx.account.level(Sk.MINING);
        boolean members = ctx.account.isMembers();
        OreType forced = OreType.parse(ctx.config.getMiningOre());
        if (forced != null && forced.miningLevel <= lvl && (!forced.members || members)) return forced;
        OreType best = null;
        for (OreType o : OreType.values()) {
            if (o.miningLevel > lvl) continue;
            if (o.members && !members) continue;
            if (!ctx.config.isActivityEnabled(Sk.MINING, o.name())) continue;
            if (enabledSafeAreaOrNull(ctx, o) == null) continue;
            if (best == null || o.xpRank > best.xpRank) best = o;
        }
        if (best == OreType.COPPER || best == OreType.TIN) {
            OreType pick = pickLowTierOre(ctx, lvl, members);
            if (pick != null) return pick;
        }
        return best;
    }

    private OreType pickLowTierOre(TaskContext ctx, int lvl, boolean members) {
        boolean copper = oreViable(ctx, OreType.COPPER, lvl, members);
        boolean tin = oreViable(ctx, OreType.TIN, lvl, members);
        if (copper && tin) {
            if (lowOreChoice != OreType.COPPER && lowOreChoice != OreType.TIN)
                lowOreChoice = Math.random() < 0.5 ? OreType.COPPER : OreType.TIN;
            return lowOreChoice;
        }
        if (copper) return OreType.COPPER;
        if (tin) return OreType.TIN;
        return null;
    }

    private boolean oreViable(TaskContext ctx, OreType o, int lvl, boolean members) {
        return o.miningLevel <= lvl && (!o.members || members)
                && ctx.config.isActivityEnabled(Sk.MINING, o.name())
                && enabledSafeAreaOrNull(ctx, o) != null;
    }

    private MiningArea activeArea(TaskContext ctx, OreType ore) {
        MiningArea a = enabledSafeAreaOrNull(ctx, ore);
        if (a != null) return a;
        a = enabledAreaOrNull(ctx, ore);
        return a != null ? a : ore.areas[0];
    }
    private MiningArea enabledAreaOrNull(TaskContext ctx, OreType ore) {
        for (MiningArea a : ore.areas) if (ctx.config.isAreaEnabled(Sk.MINING, ore.name(), a.name())) return a;
        return null;
    }
    private MiningArea enabledSafeAreaOrNull(TaskContext ctx, OreType ore) {
        for (MiningArea a : ore.areas)
            if (a.usableNow() && ctx.config.isAreaEnabled(Sk.MINING, ore.name(), a.name())) return a;
        return null;
    }

    // ── mining ────────────────────────────────────────────────────────────────────────────────
    private int mine(TaskContext ctx, OreType ore) {
        GameApi a = g();
        if (a.isAnimating()) return smallSleep();
        MiningArea area = activeArea(ctx, ore);
        Pos anchor = area.anchor;

        GameObj rock = committedRock(ore);
        if (rock == null) { rock = findRockNear(ore, anchor, AREA_RADIUS); targetRockTile = rock != null ? rock.position() : null; }

        if (rock == null) {
            if (distanceTo(anchor) > AREA_RADIUS) {
                ctx.log("[mine] walking to the " + ore.oreName + " mine at " + area.label + ".");
                Nav.walkTo(anchor); logNearbyRocks(ctx); return 700;
            }
            logNearbyRocks(ctx); return 900;
        }
        if (rock.distance() > 6) { Nav.walkTo(rock.position()); return 700; }
        if (rock.interact("Mine")) {
            ctx.log("[mine] mining " + ore.oreName + " rock#" + rock.id() + " @" + rock.position());
            a.waitUntil(() -> a.isAnimating() || a.invIsFull(), 4000);
        }
        return smallSleep();
    }

    private GameObj committedRock(OreType ore) {
        if (targetRockTile == null) return null;
        GameObj r = closestObj(o -> o.position() != null && o.position().equals(targetRockTile)
                && o.hasAction("Mine") && ore.isRock(o.id()), AREA_RADIUS + 2);
        if (r == null) targetRockTile = null;
        return r;
    }
    private GameObj findRockNear(OreType ore, Pos anchor, int radius) {
        return closestObj(o -> o.hasAction("Mine") && ore.isRock(o.id())
                && o.position() != null && o.position().distance(anchor) <= radius, radius + 2);
    }
    private double distanceTo(Pos t) {
        GameApi a = g();
        Pos me = a == null ? null : a.playerPosition();
        return (me == null || t == null) ? 999 : me.distance(t);
    }

    private void logNearbyRocks(TaskContext ctx) {
        long t = System.currentTimeMillis();
        if (t - lastRockScan < 15000) return;
        lastRockScan = t;
        StringBuilder sb = new StringBuilder();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (GameObj o : g().objectsWithin(15)) {
            if (o == null || o.name() == null || !o.name().toLowerCase().contains("rock") || !o.hasAction("Mine")) continue;
            if (!seen.add(o.id())) continue;
            sb.append(o.name()).append('#').append(o.id()).append('(').append((int) o.distance()).append("), ");
            if (seen.size() >= 15) break;
        }
        ctx.log("[mine] nearby minable rocks: " + (sb.length() == 0 ? "none" : sb));
    }

    // ── full inventory ──────────────────────────────────────────────────────────────────────────
    private int dropOre(TaskContext ctx, OreType ore) {
        GameApi a = g();
        PickaxeTier held = bestHeldUsable(ctx);
        if (held != null && a.invContains(held.itemId)) a.invDropAllExcept(held.itemId);
        else a.invDropAll(ore.oreId);
        ctx.log("[mine] power-dropping ore (max XP).");
        a.waitUntil(() -> !a.invIsFull(), 3000);
        return 700;
    }

    private int doBank(TaskContext ctx, OreType ore) {
        GameApi a = g();
        if (!a.bankIsOpen()) { if (!Nav.openBank(null)) return 700; return 400; }
        PickaxeTier held = bestHeldUsable(ctx);
        if (held != null && a.invContains(held.itemId)) a.depositAllExcept(held.itemId);
        else a.depositInventory();
        a.waitUntil(() -> !a.invIsFull(), 3000);
        lowOreChoice = null;

        PickaxeTier bankBetter = bestUsableInBank(ctx);
        if (bankBetter != null && (held == null || bankBetter.ordinal() > held.ordinal())) {
            if (a.withdraw(bankBetter.itemId, 1)) {
                a.waitUntil(() -> a.invContains(bankBetter.itemId), 2000);
                ctx.log("[mine] upgraded pickaxe from bank: " + bankBetter.itemName);
            }
        }
        a.closeBank();
        return 800;
    }

    // ── pickaxe management ───────────────────────────────────────────────────────────────────────
    private int ensurePickaxe(TaskContext ctx) {
        GameApi a = g();
        int attack = ctx.account.level(Sk.ATTACK);
        PickaxeTier held = bestHeldUsable(ctx);

        if (held != null) {
            if (held.wieldableAt(attack) && !a.isWearing(held.itemId) && a.invContains(held.itemId)) {
                if (a.invInteract(held.itemId, "Wield") || a.invInteract(held.itemId, "Wear") || a.invInteract(held.itemId, "Equip")) {
                    ctx.log("[mine] wielding " + held.itemName + ".");
                    a.sleep(650);
                    return 700;
                }
            }
            if (ctx.config.isGeBuyAxes()) {
                PickaxeTier better = bestAffordableUpgrade(ctx, held);
                if (better != null && !owns(better)) return geBuyAndEquip(ctx, better);
            }
            return 0;
        }

        if (a.bankIsOpen()) {
            if (a.invEmptySlots() < 28) { a.depositInventory(); a.waitUntil(() -> a.invEmptySlots() >= 28, 2000); }
            PickaxeTier bankPick = bestUsableInBank(ctx);
            if (bankPick != null) {
                if (a.withdraw(bankPick.itemId, 1)) {
                    ctx.log("[mine] withdrew " + bankPick.itemName + " from bank.");
                    a.waitUntil(() -> a.invContains(bankPick.itemId), 2000);
                }
                return 800;
            }
            if (ctx.config.isGeBuyAxes()) {
                PickaxeTier buy = bestAffordable(ctx);
                if (buy != null) { a.closeBank(); return geBuyAndEquip(ctx, buy); }
            }
            ctx.log("[mine] no usable pickaxe in bank and none affordable — cannot train right now.");
            return 5000;
        }

        ctx.log("[mine] no pickaxe carried; opening bank to fetch one.");
        a.openNearestBank();
        a.waitUntil(a::bankIsOpen, 8000);
        return 1200;
    }

    private int geBuyAndEquip(TaskContext ctx, PickaxeTier tier) {
        GameApi a = g();
        int shop = SupplyBuy.tryStore(ctx, tier.itemId, 1);
        if (shop != SupplyBuy.NO_STORE) return shop;

        int offer = buyOffer(tier), cost = offer;
        double dist = distanceTo(GE_TILE);
        if (dist > 8) {
            if (a.geOpen()) a.geClose();
            ctx.log("[mine] travelling to GE to buy " + tier.itemName + " (dist " + (int) dist + ").");
            Nav.walkTo(GE_TILE); return 900;
        }
        if (!a.invContains(tier.itemId) && a.invCount(995) < cost) {
            if (a.geOpen()) a.geClose();
            if (!a.bankIsOpen()) { if (!Nav.openBank(null)) return 600; return 400; }
            ctx.bank.maybeSnapshot();
            int need = cost - a.invCount(995);
            if (a.bankCount(995) < need) {
                ctx.log("[mine] can't afford " + tier.itemName + " (offer " + offer + "gp); skipping.");
                a.closeBank(); return 1500;
            }
            a.withdraw(995, need);
            a.waitUntil(() -> a.invCount(995) >= cost, 1500);
            a.closeBank(); return 500;
        }
        if (!a.geOpen()) { a.openGe(); a.waitUntil(a::geOpen, 3000); return 1000; }
        if (!a.invContains(tier.itemId)) {
            boolean ok = a.geBuy(tier.itemId, 1, offer);
            ctx.log("[mine] GE buy " + tier.itemName + " @" + offer + "gp ok=" + ok);
            a.sleep(3000);
            a.geCollectAll();
            a.waitUntil(() -> a.invContains(tier.itemId), 4000);
        }
        a.geClose();
        return 1000;
    }

    private int buyOffer(PickaxeTier tier) {
        int live = PriceLookup.high(tier.itemId);
        int base = live > 0 ? live : tier.fallbackPrice;
        return (int) Math.ceil(base * 1.05) + 1;
    }

    private PickaxeTier bestHeldUsable(TaskContext ctx) {
        int mining = ctx.account.level(Sk.MINING); boolean m = ctx.account.isMembers();
        PickaxeTier[] v = PickaxeTier.values();
        for (int i = v.length - 1; i >= 0; i--) if (v[i].usableAt(mining, m) && owns(v[i])) return v[i];
        return null;
    }
    private PickaxeTier bestUsableInBank(TaskContext ctx) {
        int mining = ctx.account.level(Sk.MINING); boolean m = ctx.account.isMembers();
        PickaxeTier[] v = PickaxeTier.values();
        for (int i = v.length - 1; i >= 0; i--) if (v[i].usableAt(mining, m) && g().bankContains(v[i].itemId)) return v[i];
        return null;
    }
    private PickaxeTier bestAffordable(TaskContext ctx) {
        int mining = ctx.account.level(Sk.MINING); boolean m = ctx.account.isMembers();
        PickaxeTier[] v = PickaxeTier.values();
        for (int i = v.length - 1; i >= 0; i--) if (v[i].usableAt(mining, m) && affordable(ctx, v[i])) return v[i];
        return null;
    }
    private PickaxeTier bestAffordableUpgrade(TaskContext ctx, PickaxeTier held) {
        int mining = ctx.account.level(Sk.MINING); boolean m = ctx.account.isMembers();
        PickaxeTier[] v = PickaxeTier.values();
        for (int i = v.length - 1; i >= 0; i--) {
            if (v[i].ordinal() <= held.ordinal()) break;
            if (v[i].usableAt(mining, m) && affordable(ctx, v[i])) return v[i];
        }
        return null;
    }
    private boolean owns(PickaxeTier p) { GameApi a = g(); return a.isWearing(p.itemId) || a.invContains(p.itemId); }

    private int totalCoins(TaskContext ctx) {
        int c = 0;
        try { c += g().invCount(995); } catch (Throwable ignored) { }
        try { if (ctx.bank != null) c += ctx.bank.count(995); } catch (Throwable ignored) { }
        return c;
    }
    private boolean affordable(TaskContext ctx, PickaxeTier p) { return totalCoins(ctx) - buyOffer(p) >= ctx.config.getGoldReserve(); }

    private int smallSleep() { return 480 + (int) (Math.random() * 420); }
}
