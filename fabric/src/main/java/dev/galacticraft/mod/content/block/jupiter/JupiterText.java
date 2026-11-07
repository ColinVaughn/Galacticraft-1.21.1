/*
 * Copyright (c) 2019-2026 Team Galacticraft
 * Copyright (c) 2026 Colin Vaughn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package dev.galacticraft.mod.content.block.jupiter;

import java.util.LinkedHashMap;
import java.util.Map;

/** Translation keys and English text for the Jupiter machines' screens and messages. */
public final class JupiterText {
    public static final Map<String, String> ENGLISH = new LinkedHashMap<>();
    public static final String HYDROGEN_RATE = add("hydrogen_rate", "Hydrogen: %s mB/s");
    public static final String HELIUM_RATE = add("helium_rate", "Helium: %s mB/s");
    public static final String AMBIENT_PRESSURE = add("ambient_pressure", "Pressure: P%s");
    public static final String VEHICLE_DOCKED = add("vehicle_docked", "Cloud Diver docked");
    public static final String NO_VEHICLE = add("no_vehicle", "No Cloud Diver docked");
    public static final String NO_VEHICLE_HINT = add("no_vehicle_hint", "Park a Cloud Diver beside the dock to service it");
    public static final String SEPARATING = add("separating", "Separating: %s%%");
    public static final String VEHICLE_READY = add("vehicle_ready", "Cloud Diver ready");
    public static final String VEHICLE_HULL = add("vehicle_hull", "Hull: %s");
    public static final String VEHICLE_POWER = add("vehicle_power", "Battery: %s");
    public static final String VEHICLE_OXYGEN = add("vehicle_oxygen", "Oxygen: %s");
    public static final String BEACON_NAME = add("beacon_name", "Beacon: %s");
    public static final String NO_BEACONS = add("no_beacons", "No other beacons broadcasting");
    public static final String BEACON_RENAME_HINT = add("beacon_rename_hint", "Rename in an anvil");
    public static final String STRIKES = add("strikes", "Strikes grounded: %s");
    public static final String PROTECTED_RADIUS = add("protected_radius", "Shelters %s blocks");
    public static final String STORM_ACTIVITY = add("storm_activity", "Storm activity: %s%%");
    public static final String HABITAT_VOLUME = add("habitat_volume", "Volume: %s / %s blocks");
    public static final String HABITAT_RATING = add("habitat_rating", "Rated to hold P%s");
    public static final String LEVIATHAN_GUARDS = add("leviathan_guards", "The Stormheart Leviathan still guards the core");
    public static final String LEVIATHAN_SLAIN = add("leviathan_slain", "The Stormheart Leviathan falls. Claim your core at the pedestal.");
    public static final String HABITAT_LEAK = add("habitat_leak", "Air escapes: close every gap within %s blocks");
    public static final String HABITAT_WALL = add("habitat_wall", "Unrated wall: %s");
    public static final String HABITAT_WALL_AT = add("habitat_wall_at", "at %s, %s, %s");
    public static final String HABITAT_WALL_HINT = add("habitat_wall_hint", "Use reinforced hull, pressure glass or sealed hatches");
    public static final String HABITAT_NONE = add("habitat_none", "Place inside an enclosed room");
    public static final String CHARGE = add("charge", "Charge");
    public static final String ENCOUNTER_STATE = add("encounter_state", "Encounter: %s");
    public static final String ENCOUNTER_IDLE = add("encounter_idle", "Dormant");
    public static final String ENCOUNTER_ACTIVE = add("encounter_active", "Storm active");
    public static final String ENCOUNTER_EXPOSED = add("encounter_exposed", "Core exposed");
    public static final String RELAYS = add("relays", "Relays disabled: %s/%s");
    public static final String PARTICIPANTS = add("participants", "Participants: %s");
    public static final String HINT_IDLE = add("hint_idle", "Begin energizes nearby Storm Relays.");
    public static final String HINT_ACTIVE = add("hint_active", "Work each relay's breaker to disarm it.");
    public static final String HINT_EXPOSED = add("hint_exposed", "Each fighter may claim one core.");
    public static final String BEGIN = add("begin", "Begin");
    public static final String RESET = add("reset", "Reset");
    public static final String CLAIM = add("claim", "Claim");
    public static final String NO_RELAYS = add("no_relays", "No Storm Relays within 32 blocks");
    public static final String RELAY_DISARMING = add("relay_disarming", "Disarming relay... %s%%");
    public static final String RELAY_DISABLED = add("relay_disabled", "Storm Relay disabled");
    public static final String CORE_EXPOSED = add("core_exposed", "All relays are down: the Stormheart core is exposed!");
    public static final String ENCOUNTER_RESET = add("encounter_reset", "The storm encounter has reset");
    public static final String CORE_CLAIMED = add("core_claimed", "You recovered a Stormheart Core");
    public static final String CORE_ALREADY_CLAIMED = add("core_already_claimed", "You have already recovered this core");
    public static final String NOT_A_PARTICIPANT = add("not_a_participant", "Only those who fought the storm can recover its core");
    public static final String DIVER_BEACON = add("diver_beacon", " | %s %sm %s");

    private JupiterText() { }

    private static String add(String id, String english) {
        String key = "ui.galacticraft.jupiter." + id;
        ENGLISH.put(key, english);
        return key;
    }
}
