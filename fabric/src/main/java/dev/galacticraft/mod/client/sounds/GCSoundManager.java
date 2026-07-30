/*
 * Copyright (c) 2019-2026 Team Galacticraft
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

package dev.galacticraft.mod.client.sounds;

import dev.galacticraft.machinelib.api.block.entity.MachineBlockEntity;
import dev.galacticraft.machinelib.api.machine.MachineStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class GCSoundManager implements SoundCallback {

    private static final Minecraft client = Minecraft.getInstance();
    private static GCSoundManager instance;
    /**
     * The one live looping sound per machine, keyed by position.
     *
     * <p>At most one entry per machine is the whole point. This was a list searched by sound event,
     * which returned the first match; because most statuses share {@code MACHINE_BUZZ}, a machine
     * flipping between two of them kept re-ending the instance that was already fading and left the
     * live one running, stacking a new loop on every flip until the sound engine ran out of
     * channels and the game fell silent for good.
     */
    private final Map<BlockPos, MachineSound> activeSounds = new HashMap<>();

    private GCSoundManager() {}

    public static GCSoundManager getInstance() {
        if (instance == null) {
            instance = new GCSoundManager();
        }
        return instance;
    }

    /** A sound has finished fading out: release it and forget it, unless it was already replaced. */
    @Override
    public <T extends MachineSound> void onFinished(T soundInstance) {
        client.getSoundManager().stop(soundInstance);
        this.activeSounds.remove(soundInstance.machine.getBlockPos(), soundInstance);
    }

    public static void onStatusChanged(Minecraft minecraft, LocalPlayer player, BlockPos pos, MachineStatus status, MachineStatus oldStatus) {
        if (minecraft.level == null) return;
        if (!(minecraft.level.getBlockEntity(pos) instanceof MachineBlockEntity machine)) return;

        GCSoundManager manager = GCSoundManager.getInstance();
        manager.apply(machine, GCSoundMap.get(status, machine), status.getType().isActive() ? 1.0F : 0.2F);
    }

    /** Brings this machine's looping sound in line with what its new status calls for. */
    private void apply(MachineBlockEntity machine, @Nullable SoundEvent next, float maxVolume) {
        BlockPos pos = machine.getBlockPos();
        MachineSound current = this.activeSounds.get(pos);

        // A sound left over from a previous world would sit on the same position as an unrelated
        // machine, so anything not belonging to this block entity is treated as gone.
        if (current != null && current.machine != machine) {
            current.end();
            this.activeSounds.remove(pos, current);
            current = null;
        }

        switch (MachineSoundPolicy.decide(current == null ? null : current.event(),
                current != null && current.isFading(), next)) {
            case NOTHING -> {
            }
            case KEEP -> current.setMaxVolume(maxVolume);
            case STOP -> {
                current.end();
                this.activeSounds.remove(pos, current);
            }
            case RESTART -> {
                current.end();
                this.activeSounds.remove(pos, current);
                this.start(machine, next, maxVolume);
            }
            case START -> this.start(machine, next, maxVolume);
        }
    }

    private void start(MachineBlockEntity machine, SoundEvent event, float maxVolume) {
        Level level = machine.getLevel();
        if (level == null || !level.isClientSide) return;

        MachineSound sound = new MachineSound(machine, event, this, maxVolume);
        client.getSoundManager().play(sound);
        this.activeSounds.put(machine.getBlockPos(), sound);
    }
}
