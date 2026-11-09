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

import dev.galacticraft.machinelib.api.machine.MachineStatus;
import net.minecraft.ChatFormatting;

import java.util.LinkedHashMap;
import java.util.Map;

/** Machine statuses shown on the Jupiter machines' screens. */
public final class JupiterMachineStatuses {
    public static final Map<String, String> ENGLISH = new LinkedHashMap<>();
    public static final MachineStatus NOT_ON_GAS_GIANT = status("not_on_gas_giant", "Needs a gas giant atmosphere", ChatFormatting.RED, MachineStatus.Type.MISSING_RESOURCE);
    public static final MachineStatus INTAKE_BLOCKED = status("intake_blocked", "Intake blocked", ChatFormatting.RED, MachineStatus.Type.OTHER);
    public static final MachineStatus GAS_TANKS_FULL = status("gas_tanks_full", "Gas tanks full", ChatFormatting.GOLD, MachineStatus.Type.OTHER);
    public static final MachineStatus COLLECTING_GAS = status("collecting_gas", "Collecting gas", ChatFormatting.GREEN, MachineStatus.Type.WORKING);
    public static final MachineStatus SEPARATING = status("separating", "Separating", ChatFormatting.GREEN, MachineStatus.Type.WORKING);
    public static final MachineStatus NO_CONDENSATE = status("no_condensate", "No condensate", ChatFormatting.RED, MachineStatus.Type.MISSING_ITEMS);
    public static final MachineStatus OUTPUT_FULL = status("output_full", "Output full", ChatFormatting.GOLD, MachineStatus.Type.OUTPUT_FULL);
    public static final MachineStatus SERVICING = status("servicing", "Servicing vehicle", ChatFormatting.GREEN, MachineStatus.Type.WORKING);
    public static final MachineStatus VEHICLE_READY = status("vehicle_ready", "Vehicle ready", ChatFormatting.GREEN, MachineStatus.Type.OTHER);
    public static final MachineStatus NO_VEHICLE = status("no_vehicle", "Waiting for a vehicle", ChatFormatting.DARK_GRAY, MachineStatus.Type.OTHER);
    public static final MachineStatus BROADCASTING = status("broadcasting", "Broadcasting", ChatFormatting.GREEN, MachineStatus.Type.WORKING);
    public static final MachineStatus STORING = status("storing", "Storing storm energy", ChatFormatting.GREEN, MachineStatus.Type.WORKING);
    public static final MachineStatus GROUNDING = status("grounding", "Grounding strikes", ChatFormatting.GREEN, MachineStatus.Type.WORKING);
    public static final MachineStatus CALM_SKIES = status("calm_skies", "No storm overhead", ChatFormatting.DARK_GRAY, MachineStatus.Type.OTHER);
    public static final MachineStatus HABITAT_SEALED = status("habitat_sealed", "Habitat pressurised", ChatFormatting.GREEN, MachineStatus.Type.WORKING);
    public static final MachineStatus HABITAT_LEAKING = status("habitat_leaking", "Habitat not sealed", ChatFormatting.RED, MachineStatus.Type.OTHER);
    public static final MachineStatus UNRATED_WALL = status("unrated_wall", "Wall not pressure-rated", ChatFormatting.RED, MachineStatus.Type.OTHER);
    public static final MachineStatus NO_HABITAT = status("no_habitat", "No enclosed air", ChatFormatting.RED, MachineStatus.Type.OTHER);

    private JupiterMachineStatuses() { }

    private static MachineStatus status(String id, String english, ChatFormatting color, MachineStatus.Type type) {
        String key = "ui.galacticraft.machine.status.jupiter." + id;
        ENGLISH.put(key, english);
        return MachineStatus.create(key, color, type);
    }
}
