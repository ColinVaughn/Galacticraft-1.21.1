package dev.galacticraft.mod.api.oxygencompressor;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.util.StringRepresentable;

public enum OxygenTankInside implements StringRepresentable{
    NONE,
    SMALL,
    MEDIUM,
    LARGE;

    @Override
    @MethodsReturnNonnullByDefault
    public String getSerializedName() {
        return name().toLowerCase();
    }
}
