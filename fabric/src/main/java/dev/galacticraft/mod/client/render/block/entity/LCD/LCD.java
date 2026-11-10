package dev.galacticraft.mod.client.render.block.entity.LCD;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface LCD {
    float DV = -1; //Dynamic Vertex
    int DC = -1; //Dynamic Color

    float SIZE16 = 0.0625f;
    int WHITE = 0xfffffffe;

    LCDrecords.LCDStaticData getStaticData(int region);
    List<LCDrecords.LCDDynamicData> getDynamicData();
    default LCDrecords.LCDDynamicData getDynamicData(int region){ return getDynamicData().get(region); }


    static List<LCDrecords.LCDDynamicData> reserve(int... verticesNcolors){
        if(verticesNcolors.length%2 != 0)
            throw new IllegalArgumentException("Number of provided color and vertices is not 2 per every region");

        int regions = verticesNcolors.length/2;

        List<LCDrecords.LCDDynamicData> reserve = new ArrayList<>(regions);
        for(int i = 0; i < regions; i++)
            reserve.add(new LCDrecords.LCDDynamicData(
                    new ArrayList<>(Collections.nCopies(verticesNcolors[i*2], new LCDrecords.Vertex(0.f, 0.f, 0.f))),
                    new ArrayList<>(Collections.nCopies(verticesNcolors[i*2+1], 0)))
            );

        return List.copyOf(reserve);
    }

    static int fade(int color, int duration, int offset, int ratio){
        long time = Math.floorMod(
                System.currentTimeMillis() - offset,
                duration * ratio
        );
        int alpha = (int) ((1.0 - Math.cos(Math.min((double) time / duration, 1.0) * 2.0 * Math.PI)) * 127.5);

        return (alpha << 24) | (color & 0xFFFFFF);
    }
}
