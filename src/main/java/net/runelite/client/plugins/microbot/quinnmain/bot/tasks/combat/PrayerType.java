package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.combat;

/**
 * Neutral prayer enum, replacing DreamBot's {@code org.dreambot.api.methods.prayer.Prayer}. Carries the
 * level requirement (so the manager picks the best it can use). Names match the game's prayer names so
 * the adapter can map to Microbot's {@code Rs2PrayerEnum} by name.
 */
public enum PrayerType {
    THICK_SKIN(1), BURST_OF_STRENGTH(4), CLARITY_OF_THOUGHT(7), SHARP_EYE(8), MYSTIC_WILL(9),
    ROCK_SKIN(10), SUPERHUMAN_STRENGTH(13), IMPROVED_REFLEXES(16), HAWK_EYE(26), MYSTIC_LORE(27),
    STEEL_SKIN(28), ULTIMATE_STRENGTH(31), INCREDIBLE_REFLEXES(34), PROTECT_FROM_MELEE(43),
    EAGLE_EYE(44), MYSTIC_MIGHT(45), CHIVALRY(60), PIETY(70), RIGOUR(74), AUGURY(77);

    public final int level;
    PrayerType(int level) { this.level = level; }
    public int getLevel() { return level; }
}
