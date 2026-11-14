package dev.galacticraft.mod.client.render.block.entity.blockLCDdata;

import dev.galacticraft.mod.client.render.block.entity.LCD.LCD;
import dev.galacticraft.mod.client.render.block.entity.LCD.LCDrecords.*;
import net.minecraft.core.Direction;

import java.util.List;

//DO NOT use #0xFFFFFFFF for white color. It is equal to sentinel value DC (which is -1).
//Any color value except that one can be used. You can use static final value WHITE (it's simply #0xFFFFFFFE).

public record ResourceStorageBlockLCDData(List<LCDDynamicData> getDynamicData) implements LCD {
    public ResourceStorageBlockLCDData { getDynamicData = List.copyOf(getDynamicData); }
    @Override
    public LCDStaticData getStaticData(int region){ return STATIC_DATA.get(region); }

    //uniColor, Base translation, Static vertices, Static colors
    static final List<LCDStaticData> STATIC_DATA = List.of(
            new LCDStaticData(
                    true,
                    new Vertex(0.125f, 1.f-SIZE16, 1.001F),
                    Direction.SOUTH,
                    LCDStaticData.floats2VertexListOf(
                            0.f, 0.f, 0.f,
                            DV, DV, DV,
                            DV, DV, DV,
                            0.f, -SIZE16, 0.f
                    ),
                    //DO NOT use #0xFFFFFFFF for white color. It is equal to sentinel value DC (which is -1).
                    //Any color value except that one can be used. You can use static final value WHITE (it's simply #0xFFFFFFFE).
                    List.of(
                            DC
                    )
            )
    );
}