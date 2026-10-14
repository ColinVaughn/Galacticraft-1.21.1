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

package dev.galacticraft.mod.client.model;

import de.javagl.obj.Obj;
import de.javagl.obj.ObjReader;
import de.javagl.obj.MtlReader;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CargoRocketModelTest {
    private Obj geometry() throws Exception {
        try (var input = getClass().getResourceAsStream("/assets/galacticraft/models/misc/cargo_rocket.obj")) {
            return ObjReader.read(input);
        }
    }

    @Test void everyFairingSectionKeepsItsMaterialAndValidUvs() throws Exception {
        var obj = geometry();
        assertEquals(obj.getNumFaces(), obj.getMaterialGroup("CargoRocket").getNumFaces());
        Set<String> names = new HashSet<>();
        for (int g = 0; g < obj.getNumGroups(); g++) {
            var group = obj.getGroup(g);
            if (group.getNumFaces() == 0) continue;
            names.add(group.getName());
            for (int f = 0; f < group.getNumFaces(); f++) {
                var face = group.getFace(f);
                assertTrue(face.getNumVertices() >= 3 && face.getNumVertices() <= 4);
                for (int v = 0; v < face.getNumVertices(); v++) {
                    var uv = obj.getTexCoord(face.getTexCoordIndex(v));
                    assertTrue(uv.getX() >= 0 && uv.getX() <= 1 && uv.getY() >= 0 && uv.getY() <= 1);
                    var normal = obj.getNormal(face.getNormalIndex(v));
                    assertEquals(1, normal.getX()*normal.getX()+normal.getY()*normal.getY()+normal.getZ()*normal.getZ(), 0.00001);
                }
            }
        }
        assertEquals(Set.of("Chassis", "FairingMiddle", "FairingUpper", "FairingCap"), names);
        try (var input = getClass().getResourceAsStream("/assets/galacticraft/models/misc/cargo_rocket.mtl")) {
            assertEquals("galacticraft:obj/cargo_rocket", MtlReader.read(input).getFirst().getMapKdOptions().getFileName());
        }
        try (var input = getClass().getResourceAsStream("/assets/galacticraft/textures/obj/cargo_rocket.png")) {
            var texture = ImageIO.read(input);
            assertEquals(256, texture.getWidth());
            assertEquals(256, texture.getHeight());
            for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) assertEquals(255, texture.getRGB(x,y) >>> 24);
        }
    }

    @Test void telescopingSectionsStayConnectedAndFitTheCollisionHeight() throws Exception {
        var obj = geometry();
        var base = bounds(obj, "Chassis");
        var middle = bounds(obj, "FairingMiddle");
        var upper = bounds(obj, "FairingUpper");
        var cap = bounds(obj, "FairingCap");
        assertEquals(0, base[1], 0.00001);
        assertTrue(base[0] >= -1.375 && base[3] <= 1.375 && base[2] >= -1.375 && base[5] <= 1.375);
        for (int i = 0; i <= 100; i++) {
            double load = i / 100.0;
            double middleMove = 0.75 * load, upperMove = 1.5 * load;
            assertTrue(middle[1] + middleMove < base[4], "Lower shell separated");
            assertTrue(upper[1] + upperMove < middle[4] + middleMove, "Upper shell separated");
            assertEquals(upper[4] + upperMove, cap[1] + upperMove, 0.00001);
            assertEquals(4.5 + upperMove, cap[4] + upperMove, 0.00001);
        }
    }

    private double[] bounds(Obj obj, String name) {
        double[] result = {Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        var group = obj.getGroup(name);
        for (int f = 0; f < group.getNumFaces(); f++) {
            var face = group.getFace(f);
            for (int v = 0; v < face.getNumVertices(); v++) {
                var p = obj.getVertex(face.getVertexIndex(v));
                for (int axis = 0; axis < 3; axis++) {
                    double value = p.get(axis);
                    result[axis] = Math.min(result[axis], value);
                    result[axis+3] = Math.max(result[axis+3], value);
                }
            }
        }
        return result;
    }
}
