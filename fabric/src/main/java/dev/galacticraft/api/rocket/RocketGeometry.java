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

package dev.galacticraft.api.rocket;

import dev.galacticraft.api.entity.rocket.render.RocketModelSlot;
import dev.galacticraft.api.rocket.entity.Rocket;
import dev.galacticraft.api.rocket.part.RocketPart;
import dev.galacticraft.api.rocket.part.RocketPartLayout;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.EitherHolder;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Assembled rocket dimensions in model units (16 per block), derived from {@link RocketPartLayout}.
 * Parts stack engine, fins, body, cone; boosters share the fin mount. Missing layout values use
 * {@link RocketModelSlot} defaults.
 *
 * @param engineY engine mount height
 * @param finY fin and booster mount height
 * @param bodyY body mount height
 * @param coneY cone mount height
 * @param top cone top and hitbox height
 * @param width widest part and hitbox width
 */
public record RocketGeometry(float engineY, float finY, float bodyY, float coneY, float top, float width) {
    private static final float BASE = (float) (RocketModelSlot.ENGINE.y() * 16.0);

    public static final RocketGeometry DEFAULT = of(null, null, null, null, null);

    public static RocketGeometry of(@Nullable RocketPart<?, ?> cone, @Nullable RocketPart<?, ?> body,
                                    @Nullable RocketPart<?, ?> fin, @Nullable RocketPart<?, ?> booster,
                                    @Nullable RocketPart<?, ?> engine) {
        float engineY = BASE;
        float finY = engineY + height(engine, RocketModelSlot.ENGINE);
        float bodyY = finY + height(fin, RocketModelSlot.FIN);
        float coneY = bodyY + height(body, RocketModelSlot.BODY);
        float top = coneY + height(cone, RocketModelSlot.CONE);

        float width = 0;
        width = Math.max(width, width(cone, RocketModelSlot.CONE));
        width = Math.max(width, width(body, RocketModelSlot.BODY));
        width = Math.max(width, width(fin, RocketModelSlot.FIN));
        width = Math.max(width, width(booster, RocketModelSlot.BOOSTER));
        width = Math.max(width, width(engine, RocketModelSlot.ENGINE));
        return new RocketGeometry(engineY, finY, bodyY, coneY, top, width);
    }

    public static RocketGeometry of(Rocket rocket) {
        return of(value(rocket.cone()), value(rocket.body()), value(rocket.fin()), value(rocket.booster()), value(rocket.engine()));
    }

    public static RocketGeometry of(RocketData data, HolderLookup.Provider lookup) {
        return of(value(data.cone(), lookup), value(data.body(), lookup), value(data.fin(), lookup),
                value(data.booster(), lookup), value(data.engine(), lookup));
    }

    /** The mount height of a slot, in model units. */
    public float mountY(RocketModelSlot slot) {
        return switch (slot) {
            case ENGINE -> this.engineY;
            case FIN, BOOSTER -> this.finY;
            case BODY -> this.bodyY;
            case CONE -> this.coneY;
        };
    }

    /** The mount height of a slot, in blocks. */
    public double mountBlocks(RocketModelSlot slot) {
        return this.mountY(slot) / 16.0;
    }

    /**
     * The extent of {@code part} along the rocket's axis, in model units.
     *
     * @param slot the slot whose stock height applies when the part declares none
     */
    public static float height(@Nullable RocketPart<?, ?> part, RocketModelSlot slot) {
        return layout(part).height().orElse(slot.defaultHeight());
    }

    /**
     * The extent of {@code part} across the rocket, in model units.
     *
     * @param slot the slot whose stock width applies when the part declares none
     */
    public static float width(@Nullable RocketPart<?, ?> part, RocketModelSlot slot) {
        return layout(part).width().orElse(slot.defaultWidth());
    }

    private static RocketPartLayout layout(@Nullable RocketPart<?, ?> part) {
        return part == null ? RocketPartLayout.DEFAULT : part.layout();
    }

    private static <T extends RocketPart<?, ?>> @Nullable T value(@Nullable Holder<T> holder) {
        return holder == null || !holder.isBound() ? null : holder.value();
    }

    private static <T extends RocketPart<?, ?>> @Nullable T value(Optional<EitherHolder<T>> holder, HolderLookup.Provider lookup) {
        return holder.flatMap(h -> h.unwrap(lookup)).filter(Holder::isBound).map(Holder::value).orElse(null);
    }
}
