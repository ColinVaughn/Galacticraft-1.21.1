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

package dev.galacticraft.mod.client.sounds;

import dev.galacticraft.api.universe.celestialbody.CelestialBodyTags;
import dev.galacticraft.mod.client.render.dimension.duststorm.ClientDustStorms;
import dev.galacticraft.mod.content.GCSounds;
import dev.galacticraft.mod.world.dimension.duststorm.DustStormExposure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/**
 * Listener-relative dust storm loop. Volume follows intensity and {@link DustStormExposure#wind};
 * stops when the storm clears or the player leaves Mars.
 */
public class DustStormSound extends AbstractTickableSoundInstance {
    /** How far the volume moves toward its target each tick, so moving under cover fades the wind rather than cutting it. */
    private static final float EASING = 0.1F;
    private static DustStormSound active;

    private DustStormSound(Minecraft mc) {
        super(GCSounds.AMBIENT_DUST_STORM, SoundSource.WEATHER, mc.level.getRandom());
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.relative = true;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    /** Starts (or keeps alive) the wind loop while a storm is active; call each client tick on Mars. */
    public static void tickAmbient() {
        if (ClientDustStorms.isStormActive() && ClientDustStorms.intensity() > 0.02F && (active == null || active.isStopped())) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            active = new DustStormSound(mc);
            mc.getSoundManager().play(active);
        }
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !CelestialBodyTags.is(mc.level, CelestialBodyTags.DUST_STORMS)) {
            this.stop();
            return;
        }
        float intensity = ClientDustStorms.intensity();
        if (intensity <= 0.02F) {
            this.stop();
            return;
        }
        float target = Math.min(1.0F, 0.35F + intensity * 0.85F)
                * DustStormExposure.wind(mc.level, DustStormExposure.eyeBlock(player));
        this.volume += (target - this.volume) * EASING;
    }
}
