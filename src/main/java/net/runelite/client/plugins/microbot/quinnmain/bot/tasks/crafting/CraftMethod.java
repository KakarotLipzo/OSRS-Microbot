package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.crafting;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

import net.runelite.client.plugins.microbot.quinnmain.bot.core.Aggression;


/**
 * The F2P crafting methods the trainer supports, selected by {@code crafting.method} (default
 * GOLD_JEWELRY). All three are built so the future GUI can switch between them:
 *
 * <ul>
 *   <li><b>GOLD_JEWELRY</b> — smelt gold bars at a furnace with a ring mould → gold rings (Al Kharid).</li>
 *   <li><b>LEATHER</b> — needle + thread on leather → leather armour (at the bank).</li>
 *   <li><b>GEMS</b> — chisel on uncut gems → cut gems (at the bank).</li>
 * </ul>
 * Materials are GE-bought when the bank is empty (like the other processing trainers).
 */
public enum CraftMethod {
    // GOLD_JEWELRY uses the Al Kharid furnace (aggressive lvl-9 warriors) — a low-combat account uses the
    // safe Edgeville furnace instead until combat ≥ 20. Bank-only methods have no aggro concern.
    GOLD_JEWELRY(BankLoc.AL_KHARID,   true,  new Pos(3276, 3186, 0),
                 Aggression.AL_KHARID_WARRIOR, BankLoc.EDGEVILLE, new Pos(3108, 3499, 0)),
    LEATHER     (BankLoc.VARROCK_WEST, false, null, 0, null, null),
    GEMS        (BankLoc.VARROCK_WEST, false, null, 0, null, null);

    public final BankLoc bank;
    public final boolean needsFurnace;
    public final Pos furnaceAnchor;
    /** Combat level of the worst aggressive mob at the primary furnace (0 = none / bank-only method). */
    public final int aggroLevel;
    public final BankLoc safeBank;
    public final Pos safeFurnace;

    CraftMethod(BankLoc bank, boolean needsFurnace, Pos furnaceAnchor,
                int aggroLevel, BankLoc safeBank, Pos safeFurnace) {
        this.bank = bank;
        this.needsFurnace = needsFurnace;
        this.furnaceAnchor = furnaceAnchor;
        this.aggroLevel = aggroLevel;
        this.safeBank = safeBank;
        this.safeFurnace = safeFurnace;
    }

    private boolean divertToSafe() {
        return aggroLevel > 0 && safeBank != null && !Aggression.safeFrom(aggroLevel);
    }

    /** Bank to use right now (safe alternative while low-combat, else the primary). */
    public BankLoc bankNow() { return divertToSafe() ? safeBank : bank; }

    /** Furnace anchor to use right now, matching {@link #bankNow()}. */
    public Pos furnaceNow() { return divertToSafe() ? safeFurnace : furnaceAnchor; }

    public static CraftMethod parse(String s) {
        if (s != null) {
            try { return valueOf(s.trim().toUpperCase()); } catch (IllegalArgumentException ignored) { }
        }
        return GOLD_JEWELRY;
    }
}
