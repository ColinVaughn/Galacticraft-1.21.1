package dev.galacticraft.mod.util;

/** Ten minutes to adulthood, followed by fifty minutes of growth into a twelve-section giant. */
public final class BurrowerGrowth {
    public static final int ADULT_AGE = 12000;
    public static final int MAX_AGE = 72000;
    public static final int MAX_SECTIONS = 12;
    private BurrowerGrowth() {}
    public static float scale(int age) {
        age = Math.clamp(age, 0, MAX_AGE);
        return age < ADULT_AGE ? 0.35F + 0.65F * age / ADULT_AGE
                : 1 + 2F * (age - ADULT_AGE) / (MAX_AGE - ADULT_AGE);
    }
    public static int sections(int age) {
        age = Math.clamp(age, 0, MAX_AGE);
        return age < ADULT_AGE ? 3 + age / 4000 : 6 + (age - ADULT_AGE) / 10000;
    }
    public static float bodyProfile(int section, int sections) {
        float tail = Math.clamp((section - 2F) / Math.max(1, sections - 2), 0, 1);
        return 1 - 0.52F * tail * tail * (3 - 2 * tail);
    }
}
