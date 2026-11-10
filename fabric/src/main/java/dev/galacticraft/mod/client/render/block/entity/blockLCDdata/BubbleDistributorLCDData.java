package dev.galacticraft.mod.client.render.block.entity.blockLCDdata;

import dev.galacticraft.mod.client.render.block.entity.LCD.LCD;
import dev.galacticraft.mod.client.render.block.entity.LCD.LCDrecords.LCDDynamicData;
import dev.galacticraft.mod.client.render.block.entity.LCD.LCDrecords.LCDStaticData;
import dev.galacticraft.mod.client.render.block.entity.LCD.LCDrecords.Vertex;
import net.minecraft.core.Direction;

import java.util.List;

//DO NOT use #0xFFFFFFFF for white color. It is equal to sentinel value DC (which is -1).
//Any color value except that one can be used. You can use static final value WHITE (it's simply #0xFFFFFFFE).

public record BubbleDistributorLCDData(List<LCDDynamicData> getDynamicData) implements LCD {
    public BubbleDistributorLCDData { getDynamicData = List.copyOf(getDynamicData); }
    @Override
    public LCDStaticData getStaticData(int region){ return STATIC_DATA.get(region); }

    public static void updateFadeValue() {
        regionFadeValue[0] = LCD.fade(0xFFB91C1C, 1600, 0, 3);
        regionFadeValue[1] = LCD.fade(0xFFB91C1C, 1600, 0, 3);
        regionFadeValue[2] = LCD.fade(0xFF09D1EF, 1600, 0, 3);
        regionFadeValue[3] = LCD.fade(0xFF09D1EF, 1600, 375, 3);
        regionFadeValue[4] = LCD.fade(0xFF09D1EF, 1600, 750, 3);
    }

    public static int getFadeValue(int region){ return regionFadeValue[region]; }

    //Fade is system time dependent, therefore calculating value for every single block seperately is obsolete
    static int[] regionFadeValue = new int[5];

    static final List<LCDStaticData> STATIC_DATA = List.of(
            //region 0
            new LCDStaticData(
                    true, //uniColor
                    new Vertex(0.f, 1.001f, 0.f), //Base Translation,
                    Direction.UP,
                    LCDStaticData.floats2VertexListOf( //Static vertices
                            0.3125f, 0.0f, 0.46875f,
                            0.34375f, 0.0f, 0.46875f,
                            0.34375f, 0.0f, 0.25f,
                            0.3125f, 0.0f, 0.25f,
                            0.4375f, 0.0f, 0.46875f,
                            0.46875f, 0.0f, 0.46875f,
                            0.46875f, 0.0f, 0.25f,
                            0.4375f, 0.0f, 0.25f,
                            0.5625f, 0.0f, 0.28125f,
                            0.65625f, 0.0f, 0.28125f,
                            0.65625f, 0.0f, 0.25f,
                            0.5625f, 0.0f, 0.25f,
                            0.53125f, 0.0f, 0.4375f,
                            0.5625f, 0.0f, 0.4375f,
                            0.5625f, 0.0f, 0.28125f,
                            0.53125f, 0.0f, 0.28125f,
                            0.65625f, 0.0f, 0.4375f,
                            0.6875f, 0.0f, 0.4375f,
                            0.6875f, 0.0f, 0.28125f,
                            0.65625f, 0.0f, 0.28125f,
                            0.34375f, 0.0f, 0.34375f,
                            0.375f, 0.0f, 0.34375f,
                            0.375f, 0.0f, 0.3125f,
                            0.34375f, 0.0f, 0.3125f,
                            0.375f, 0.0f, 0.375f,
                            0.40625f, 0.0f, 0.375f,
                            0.40625f, 0.0f, 0.34375f,
                            0.375f, 0.0f, 0.34375f,
                            0.40625f, 0.0f, 0.40625f,
                            0.4375f, 0.0f, 0.40625f,
                            0.4375f, 0.0f, 0.375f,
                            0.40625f, 0.0f, 0.375f,
                            0.5625f, 0.0f, 0.46875f,
                            0.65625f, 0.0f, 0.46875f,
                            0.65625f, 0.0f, 0.4375f,
                            0.5625f, 0.0f, 0.4375f,
                            0.3125f, 0.0f, 0.5625f,
                            0.4375f, 0.0f, 0.5625f,
                            0.4375f, 0.0f, 0.53125f,
                            0.3125f, 0.0f, 0.53125f,
                            0.53125f, 0.0f, 0.71875f,
                            0.5625f, 0.0f, 0.71875f,
                            0.5625f, 0.0f, 0.53125f,
                            0.53125f, 0.0f, 0.53125f,
                            0.65625f, 0.0f, 0.71875f,
                            0.6875f, 0.0f, 0.71875f,
                            0.6875f, 0.0f, 0.53125f,
                            0.65625f, 0.0f, 0.53125f,
                            0.3125f, 0.0f, 0.75f,
                            0.34375f, 0.0f, 0.75f,
                            0.34375f, 0.0f, 0.5625f,
                            0.3125f, 0.0f, 0.5625f,
                            0.4375f, 0.0f, 0.625f,
                            0.46875f, 0.0f, 0.625f,
                            0.46875f, 0.0f, 0.5625f,
                            0.4375f, 0.0f, 0.5625f,
                            0.34375f, 0.0f, 0.65625f,
                            0.4375f, 0.0f, 0.65625f,
                            0.4375f, 0.0f, 0.625f,
                            0.34375f, 0.0f, 0.625f,
                            0.59375f, 0.0f, 0.71875f,
                            0.625f, 0.0f, 0.71875f,
                            0.625f, 0.0f, 0.625f,
                            0.59375f, 0.0f, 0.625f,
                            0.5625f, 0.0f, 0.75f,
                            0.59375f, 0.0f, 0.75f,
                            0.59375f, 0.0f, 0.71875f,
                            0.5625f, 0.0f, 0.71875f,
                            0.625f, 0.0f, 0.75f,
                            0.65625f, 0.0f, 0.75f,
                            0.65625f, 0.0f, 0.71875f,
                            0.625f, 0.0f, 0.71875f
                    ),
                    //DO NOT use #0xFFFFFFFF for white color. It is equal to sentinel value DC (which is -1).
                    //Any color value except that one can be used. You can use static final value WHITE (it's simply #0xFFFFFFFE).
                    List.of( //Static colors
                            DC
                    )
            ),
            //region 1
            new LCDStaticData(
                    true,
                    new Vertex(0.f, 1.001f, 0.f),
                    Direction.UP,
                    LCDStaticData.floats2VertexListOf(
                            0.3125f, 0.0f, 0.46875f,
                            0.34375f, 0.0f, 0.46875f,
                            0.34375f, 0.0f, 0.25f,
                            0.3125f, 0.0f, 0.25f,
                            0.4375f, 0.0f, 0.46875f,
                            0.46875f, 0.0f, 0.46875f,
                            0.46875f, 0.0f, 0.25f,
                            0.4375f, 0.0f, 0.25f,
                            0.5625f, 0.0f, 0.28125f,
                            0.65625f, 0.0f, 0.28125f,
                            0.65625f, 0.0f, 0.25f,
                            0.5625f, 0.0f, 0.25f,
                            0.53125f, 0.0f, 0.4375f,
                            0.5625f, 0.0f, 0.4375f,
                            0.5625f, 0.0f, 0.28125f,
                            0.53125f, 0.0f, 0.28125f,
                            0.65625f, 0.0f, 0.4375f,
                            0.6875f, 0.0f, 0.4375f,
                            0.6875f, 0.0f, 0.28125f,
                            0.65625f, 0.0f, 0.28125f,
                            0.34375f, 0.0f, 0.34375f,
                            0.375f, 0.0f, 0.34375f,
                            0.375f, 0.0f, 0.3125f,
                            0.34375f, 0.0f, 0.3125f,
                            0.375f, 0.0f, 0.375f,
                            0.40625f, 0.0f, 0.375f,
                            0.40625f, 0.0f, 0.34375f,
                            0.375f, 0.0f, 0.34375f,
                            0.40625f, 0.0f, 0.40625f,
                            0.4375f, 0.0f, 0.40625f,
                            0.4375f, 0.0f, 0.375f,
                            0.40625f, 0.0f, 0.375f,
                            0.5625f, 0.0f, 0.46875f,
                            0.65625f, 0.0f, 0.46875f,
                            0.65625f, 0.0f, 0.4375f,
                            0.5625f, 0.0f, 0.4375f,
                            0.34375f, 0.0f, 0.5625f,
                            0.4375f, 0.0f, 0.5625f,
                            0.4375f, 0.0f, 0.53125f,
                            0.34375f, 0.0f, 0.53125f,
                            0.5625f, 0.0f, 0.5625f,
                            0.65625f, 0.0f, 0.5625f,
                            0.65625f, 0.0f, 0.53125f,
                            0.5625f, 0.0f, 0.53125f,
                            0.3125f, 0.0f, 0.71875f,
                            0.34375f, 0.0f, 0.71875f,
                            0.34375f, 0.0f, 0.5625f,
                            0.3125f, 0.0f, 0.5625f,
                            0.4375f, 0.0f, 0.71875f,
                            0.46875f, 0.0f, 0.71875f,
                            0.46875f, 0.0f, 0.5625f,
                            0.4375f, 0.0f, 0.5625f,
                            0.53125f, 0.0f, 0.59375f,
                            0.5625f, 0.0f, 0.59375f,
                            0.5625f, 0.0f, 0.5625f,
                            0.53125f, 0.0f, 0.5625f,
                            0.65625f, 0.0f, 0.625f,
                            0.6875f, 0.0f, 0.625f,
                            0.6875f, 0.0f, 0.5625f,
                            0.65625f, 0.0f, 0.5625f,
                            0.625f, 0.0f, 0.65625f,
                            0.65625f, 0.0f, 0.65625f,
                            0.65625f, 0.0f, 0.625f,
                            0.625f, 0.0f, 0.625f,
                            0.59375f, 0.0f, 0.6875f,
                            0.625f, 0.0f, 0.6875f,
                            0.625f, 0.0f, 0.65625f,
                            0.59375f, 0.0f, 0.65625f,
                            0.5625f, 0.0f, 0.75f,
                            0.59375f, 0.0f, 0.75f,
                            0.59375f, 0.0f, 0.6875f,
                            0.5625f, 0.0f, 0.6875f,
                            0.34375f, 0.0f, 0.75f,
                            0.4375f, 0.0f, 0.75f,
                            0.4375f, 0.0f, 0.71875f,
                            0.34375f, 0.0f, 0.71875f,
                            0.53125f, 0.0f, 0.75f,
                            0.5625f, 0.0f, 0.75f,
                            0.5625f, 0.0f, 0.71875f,
                            0.53125f, 0.0f, 0.71875f,
                            0.59375f, 0.0f, 0.75f,
                            0.6875f, 0.0f, 0.75f,
                            0.6875f, 0.0f, 0.71875f,
                            0.59375f, 0.0f, 0.71875f
                    ),
                    List.of(
                            DC
                    )
            ),
            //region 2
            new LCDStaticData(
                    true,
                    new Vertex(0.375f, 1.001f, 0.375f),
                    Direction.UP,
                    LCDStaticData.floats2VertexListOf(
                            0.0625f, 0.0f, 0.0625f,
                            0.1875f, 0.0f, 0.0625f,
                            0.1875f, 0.0f, 0.0f,
                            0.0625f, 0.0f, 0.0f,
                            0.0f, 0.0f, 0.1875f,
                            0.0625f, 0.0f, 0.1875f,
                            0.0625f, 0.0f, 0.0625f,
                            0.0f, 0.0f, 0.0625f,
                            0.1875f, 0.0f, 0.1875f,
                            0.25f, 0.0f, 0.1875f,
                            0.25f, 0.0f, 0.0625f,
                            0.1875f, 0.0f, 0.0625f,
                            0.0625f, 0.0f, 0.25f,
                            0.1875f, 0.0f, 0.25f,
                            0.1875f, 0.0f, 0.1875f,
                            0.0625f, 0.0f, 0.1875f
                    ),
                    List.of(
                            DC
                    )
            ),
            //region 3
            new LCDStaticData(
                    true,
                    new Vertex(0.250f, 1.001f, 0.250f),
                    Direction.UP,
                    LCDStaticData.floats2VertexListOf(
                            0.125f, 0.0f, 0.0625f,
                            0.375f, 0.0f, 0.0625f,
                            0.375f, 0.0f, 0.0f,
                            0.125f, 0.0f, 0.0f,
                            0.0625f, 0.0f, 0.125f,
                            0.125f, 0.0f, 0.125f,
                            0.125f, 0.0f, 0.0625f,
                            0.0625f, 0.0f, 0.0625f,
                            0.375f, 0.0f, 0.125f,
                            0.4375f, 0.0f, 0.125f,
                            0.4375f, 0.0f, 0.0625f,
                            0.375f, 0.0f, 0.0625f,
                            0.0f, 0.0f, 0.375f,
                            0.0625f, 0.0f, 0.375f,
                            0.0625f, 0.0f, 0.125f,
                            0.0f, 0.0f, 0.125f,
                            0.4375f, 0.0f, 0.375f,
                            0.5f, 0.0f, 0.375f,
                            0.5f, 0.0f, 0.125f,
                            0.4375f, 0.0f, 0.125f,
                            0.0625f, 0.0f, 0.4375f,
                            0.125f, 0.0f, 0.4375f,
                            0.125f, 0.0f, 0.375f,
                            0.0625f, 0.0f, 0.375f,
                            0.375f, 0.0f, 0.4375f,
                            0.4375f, 0.0f, 0.4375f,
                            0.4375f, 0.0f, 0.375f,
                            0.375f, 0.0f, 0.375f,
                            0.125f, 0.0f, 0.5f,
                            0.375f, 0.0f, 0.5f,
                            0.375f, 0.0f, 0.4375f,
                            0.125f, 0.0f, 0.4375f
                    ),
                    List.of(
                            DC
                    )
            ),
            //region 4
            new LCDStaticData(
                    true,
                    new Vertex(0.125f, 1.001f, 0.125f),
                    Direction.UP,
                    LCDStaticData.floats2VertexListOf(
                            0.25f, 0.0f, 0.0625f,
                            0.5f, 0.0f, 0.0625f,
                            0.5f, 0.0f, 0.0f,
                            0.25f, 0.0f, 0.0f,
                            0.125f, 0.0f, 0.125f,
                            0.25f, 0.0f, 0.125f,
                            0.25f, 0.0f, 0.0625f,
                            0.125f, 0.0f, 0.0625f,
                            0.5f, 0.0f, 0.125f,
                            0.625f, 0.0f, 0.125f,
                            0.625f, 0.0f, 0.0625f,
                            0.5f, 0.0f, 0.0625f,
                            0.0625f, 0.0f, 0.25f,
                            0.125f, 0.0f, 0.25f,
                            0.125f, 0.0f, 0.125f,
                            0.0625f, 0.0f, 0.125f,
                            0.625f, 0.0f, 0.25f,
                            0.6875f, 0.0f, 0.25f,
                            0.6875f, 0.0f, 0.125f,
                            0.625f, 0.0f, 0.125f,
                            0.0f, 0.0f, 0.5f,
                            0.0625f, 0.0f, 0.5f,
                            0.0625f, 0.0f, 0.25f,
                            0.0f, 0.0f, 0.25f,
                            0.6875f, 0.0f, 0.5f,
                            0.75f, 0.0f, 0.5f,
                            0.75f, 0.0f, 0.25f,
                            0.6875f, 0.0f, 0.25f,
                            0.0625f, 0.0f, 0.625f,
                            0.125f, 0.0f, 0.625f,
                            0.125f, 0.0f, 0.5f,
                            0.0625f, 0.0f, 0.5f,
                            0.625f, 0.0f, 0.625f,
                            0.6875f, 0.0f, 0.625f,
                            0.6875f, 0.0f, 0.5f,
                            0.625f, 0.0f, 0.5f,
                            0.125f, 0.0f, 0.6875f,
                            0.25f, 0.0f, 0.6875f,
                            0.25f, 0.0f, 0.625f,
                            0.125f, 0.0f, 0.625f,
                            0.5f, 0.0f, 0.6875f,
                            0.625f, 0.0f, 0.6875f,
                            0.625f, 0.0f, 0.625f,
                            0.5f, 0.0f, 0.625f,
                            0.25f, 0.0f, 0.75f,
                            0.5f, 0.0f, 0.75f,
                            0.5f, 0.0f, 0.6875f,
                            0.25f, 0.0f, 0.6875f
                            ),
                    List.of(
                            DC
                    )
            )

    );
}