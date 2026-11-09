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

import dev.galacticraft.api.rocket.LaunchStage;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.render.dimension.SpaceflightTransition;
import net.minecraft.client.Minecraft;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import dev.galacticraft.mod.content.GCSounds;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

public class RocketSound extends AbstractTickableSoundInstance {
    private static final Map<UUID, RocketSound> playing = new HashMap<>();
    private RocketEntity rocket;
    private boolean ignition = false;
    private boolean queued;

    public static void play(RocketEntity rocket) {
        // Stage events also fire on the integrated server; audio and its registry belong to the client thread.
        if (!rocket.level().isClientSide()) return;
        if (rocket.getLaunchStage() != LaunchStage.IGNITED && rocket.getLaunchStage() != LaunchStage.LAUNCHED) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level != rocket.level()) return;
        if (mc.player.getVehicle() != rocket && !SpaceflightTransition.carries(rocket.getUUID())
                && rocket.getY() > Constant.SPACE_HEIGHT) return;
        var sounds = mc.getSoundManager();
        var previous = playing.get(rocket.getUUID());
        if (previous != null && !previous.isStopped() && (previous.queued || sounds.isActive(previous))) return;
        if (previous != null) sounds.stop(previous);
        var sound = new RocketSound(rocket);
        playing.put(rocket.getUUID(), sound);
        sounds.play(sound);
    }

    public static void clear() {
        playing.values().forEach(sound -> Minecraft.getInstance().getSoundManager().stop(sound));
        playing.clear();
    }

    public static boolean hasCabinSound(UUID rocket) {
        var sound = playing.get(rocket);
        return sound != null && !sound.isStopped() && sound.isCabinSound()
                && Minecraft.getInstance().getSoundManager().isActive(sound);
    }

    public boolean isCabinSound() { return relative; }

    @Override public boolean canStartSilent() { return relative; }

    private void stopRocket() {
        stop();
        playing.remove(rocket.getUUID(), this);
    }

    private RocketSound(RocketEntity rocket) {
        super(GCSounds.ROCKET_IGNITE, SoundSource.NEUTRAL, rocket.level().getRandom());
        this.rocket = rocket;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = 0.00001F;  //If it's zero it won't start playing
        this.pitch = 0.0F;
        this.looping = true;
        this.delay = 0;
        var player = Minecraft.getInstance().player;
        this.relative = player != null && player.getVehicle() == rocket || SpaceflightTransition.carries(rocket.getUUID());
        if (relative) { volume = .55F; pitch = 1; }
        else setSoundLocation(rocket.getX(), rocket.getY(), rocket.getZ());
    }

    @Override
    public void tick() {
        queued = false;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.player.isDeadOrDying()) { stopRocket(); return; }
        boolean handoff = SpaceflightTransition.carries(rocket.getUUID());
        if (!rocket.isAlive() && !handoff) { stopRocket(); return; }
        boolean aboard = mc.player.getVehicle() instanceof RocketEntity ship && ship.getUUID().equals(rocket.getUUID());
        if (aboard) rocket = (RocketEntity) mc.player.getVehicle();
        // Keep the same loop/channel while native respawn briefly removes the player and vehicle.
        if (relative != (aboard || handoff)) {
            // OpenAL's relative flag is set when the channel starts, not by the ordinary tick update.
            stopRocket();
            var sound = new RocketSound(rocket);
            sound.queued = true;
            playing.put(rocket.getUUID(), sound);
            mc.getSoundManager().queueTickingSound(sound);
            return;
        }
        if (relative && (handoff || rocket.isAlive() && rocket.getLaunchStage() == LaunchStage.LAUNCHED)) {
            volume = handoff ? SpaceflightTransition.engineVolume() : .55F;
            pitch = 1;
            setSoundLocation(0, 0, 0);
            return;
        }
        if (this.rocket.isAlive() && rocket.level() == mc.level) {
            if (this.rocket.getLaunchStage() == LaunchStage.IGNITED) {
                if (!ignition) {
                    this.pitch = 0.0F;
                    ignition = true;
                }
                if (this.rocket.getTimeBeforeLaunch() < this.rocket.getPreLaunchWait()) {
                    if (this.pitch < 1.0F) {
                        this.pitch += 0.0025F;
                    }

                    if (this.pitch > 1.0F) {
                        this.pitch = 1.0F;
                    }
                }
            } else if (this.rocket.getLaunchStage() == LaunchStage.LAUNCHED) {
                pitch = 1.0F;
            } else {
                stopRocket();
            }

            if (this.rocket.getLaunchStage().ordinal() >= LaunchStage.IGNITED.ordinal()) {
                if (this.rocket.getY() > Constant.SPACE_HEIGHT) {
                    this.volume = 0F;
                    if (this.rocket.getLaunchStage() != LaunchStage.FAILED) {
                        stopRocket();
                    }
                } else if (this.rocket.getY() > Constant.OVERWORLD_SKYPROVIDER_STARTHEIGHT) {
                    this.volume = 1.0F - ((float) (this.rocket.getY() - Constant.OVERWORLD_SKYPROVIDER_STARTHEIGHT)) / ((float) (Constant.SPACE_HEIGHT - Constant.OVERWORLD_SKYPROVIDER_STARTHEIGHT));
                } else {
                    this.volume = 1.0F;
                }
            }

            if (relative) setSoundLocation(0, 0, 0);
            else setSoundLocation(rocket.getX(), rocket.getY(), rocket.getZ());
        } else {
            stopRocket();
        }
    }

    public void setSoundLocation(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
