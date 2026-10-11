package dev.galacticraft.mod.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BurrowerGrowthTest {
    @Test void bodyTapersAcrossEverySupportedLengthWithoutAGapAtTheOldTail() {
        for (int length = 6; length <= 12; length++) {
            assertEquals(1, BurrowerGrowth.bodyProfile(1, length));
            assertEquals(1, BurrowerGrowth.bodyProfile(2, length));
            float previous = 1;
            for (int section = 3; section <= length; section++) {
                float profile = BurrowerGrowth.bodyProfile(section, length);
                assertTrue(profile <= previous && profile >= 0.4799F);
                assertTrue(previous - profile < 0.19F, "Abrupt waist or rear expansion at " + section);
                previous = profile;
            }
            assertEquals(0.48F, previous, 0.0001);
        }
        assertTrue(BurrowerGrowth.bodyProfile(6, 12) > BurrowerGrowth.bodyProfile(6, 6));
    }

    @Test void exportedGiantArmorMatchesTheContinuousProfile() throws java.io.IOException {
        try (var reader = new java.io.InputStreamReader(java.util.Objects.requireNonNull(getClass().getResourceAsStream(
                "/assets/galacticraft/models/entity/lunar_burrower_grown.geo.json")))) {
            var bones = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject()
                    .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
            for (int section = 2; section <= 12; section++) {
                float height = 0;
                for (var entry : bones) {
                    var bone = entry.getAsJsonObject();
                    if (!bone.get("name").getAsString().equals("body_" + section + "_shell")) continue;
                    for (var element : bone.getAsJsonArray("cubes")) {
                        var cube = element.getAsJsonObject();
                        height = Math.max(height, cube.getAsJsonArray("origin").get(1).getAsFloat()
                                + cube.getAsJsonArray("size").get(1).getAsFloat());
                    }
                }
                assertEquals(20 * BurrowerGrowth.bodyProfile(section, 12), height, 0.001, "Wrong giant armor section " + section);
            }
        }
    }
    @Test void growthChangesBothSizeAndSectionCount() {
        assertEquals(0.35F, BurrowerGrowth.scale(0), 0.0001);
        assertEquals(3, BurrowerGrowth.sections(0));
        assertEquals(0.675F, BurrowerGrowth.scale(6000), 0.0001);
        assertEquals(4, BurrowerGrowth.sections(6000));
        assertEquals(1, BurrowerGrowth.scale(BurrowerGrowth.ADULT_AGE));
        assertEquals(6, BurrowerGrowth.sections(BurrowerGrowth.ADULT_AGE));
        assertEquals(3, BurrowerGrowth.scale(BurrowerGrowth.MAX_AGE));
        assertEquals(12, BurrowerGrowth.sections(BurrowerGrowth.MAX_AGE));
    }
    @Test void scaleIsContinuousAndGrowthIsBoundedAndMonotonic() {
        float previousScale = 0;
        int previousSections = 0;
        for (int age = 0; age <= BurrowerGrowth.MAX_AGE; age++) {
            float scale = BurrowerGrowth.scale(age);
            int sections = BurrowerGrowth.sections(age);
            assertTrue(scale >= previousScale);
            if (age > 0) assertTrue(scale - previousScale < 0.001);
            assertTrue(sections >= previousSections && sections <= 12);
            previousScale = scale; previousSections = sections;
        }
        assertEquals(BurrowerGrowth.scale(0), BurrowerGrowth.scale(-100));
        assertEquals(12, BurrowerGrowth.sections(Integer.MAX_VALUE));
        assertEquals(3, BurrowerGrowth.scale(Integer.MAX_VALUE));
    }
}
