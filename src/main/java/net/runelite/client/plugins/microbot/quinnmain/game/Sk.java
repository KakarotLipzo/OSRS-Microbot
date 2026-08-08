package net.runelite.client.plugins.microbot.quinnmain.game;

/**
 * Client-neutral skill enum. Replaces DreamBot's {@code org.dreambot.api.methods.skills.Skill} (used
 * across ~49 files in OSRS-Main) so the ported logic carries no client type. Names match DreamBot's
 * enum exactly, so persisted config keys ({@code Skill.name()}) carry over unchanged.
 *
 * <p>The {@link GameApi} takes/returns skills as this enum's {@link #name()} string, so adapters map
 * it to their own client type at the boundary ({@code MicrobotGameApi} → RuneLite {@code net.runelite.api.Skill}).
 */
public enum Sk {
    ATTACK, DEFENCE, STRENGTH, HITPOINTS, RANGED, PRAYER, MAGIC,
    COOKING, WOODCUTTING, FLETCHING, FISHING, FIREMAKING, CRAFTING,
    SMITHING, MINING, HERBLORE, AGILITY, THIEVING, SLAYER, FARMING,
    RUNECRAFTING, HUNTER, CONSTRUCTION, SAILING;

    /** Human label, e.g. RUNECRAFTING → "Runecraft"; otherwise title-case (matches the HUD/panel). */
    public String label() {
        if (this == RUNECRAFTING) return "Runecraft";
        String n = name().toLowerCase();
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    /** Lenient parse: accepts "Runecraft"/"Runecrafting" and any case; null if unknown. */
    public static Sk parse(String s) {
        if (s == null) return null;
        String n = s.trim();
        if (n.equalsIgnoreCase("Runecraft") || n.equalsIgnoreCase("Runecrafting")) return RUNECRAFTING;
        for (Sk sk : values()) if (sk.name().equalsIgnoreCase(n)) return sk;
        return null;
    }
}
