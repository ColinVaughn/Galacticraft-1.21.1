package dev.galacticraft.mod.client.render.block.entity.LCD;

import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

public class LCDrecords {
    public record LCDStaticData (boolean uniColor, Vertex baseTranslation, Direction normal, List<Vertex> staticVertices, List<Integer> staticColors) {
        public LCDStaticData {
            staticVertices = List.copyOf(staticVertices);
            staticColors = List.copyOf(staticColors);
        }

        static public List<Vertex> floats2VertexListOf(float... coordinates){
            if(coordinates.length % 3 != 0)
                throw new IllegalArgumentException("Number of provided coordinates is not 3 per every Vertex");

            List<Vertex> staticVertices = new ArrayList<>();
            for(int i = 0; i < coordinates.length; i+=3)
                staticVertices.add(new Vertex(coordinates[i], coordinates[i+1], coordinates[i+2]));

            return List.copyOf(staticVertices);
        }
    }

    public record LCDDynamicData (List<Vertex> dynamicVertices, List<Integer> dynamicColors){ }
    public record Vertex(float x, float y, float z) {}
}
