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

package dev.galacticraft.mod.client.util;

import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Holds a rocket that exists only to be rendered: item models, recipe previews and the like.
 * <p>
 * Such a rocket must never be built with a {@code null} level. Vanilla tolerates it, but
 * {@link net.minecraft.world.entity.Entity}'s constructor is a popular mixin target and mods that inject
 * there dereference the level (Data Anchor calls {@code level.isClientSide()}), which crashes the game.
 * NeoForge's constructor also reads its fluid-type registry, which is not populated while client renderers
 * are being registered. Building on first use keeps the entity off the class-init path, and re-binding the
 * level on every lookup lets it follow the player between dimensions.
 */
public final class LazyRocketEntity {
    private final float initialYRot;
    private RocketEntity entity;

    public LazyRocketEntity() {
        this(0.0F);
    }

    public LazyRocketEntity(float initialYRot) {
        this.initialYRot = initialYRot;
    }

    /**
     * @return a rocket bound to the current client level, or {@code null} if no level is loaded yet.
     */
    public @Nullable RocketEntity get() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }

        if (this.entity == null) {
            this.entity = new RocketEntity(GCEntityTypes.ROCKET, level);
            this.entity.setYRot(this.initialYRot);
        } else {
            this.entity.setLevel(level);
        }
        return this.entity;
    }
}
