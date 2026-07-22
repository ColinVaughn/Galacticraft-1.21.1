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

package dev.galacticraft.impl.internal.client.tabs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class InventoryTabLayout {
    public static final int TAB_WIDTH = 29;
    public static final int TAB_HEIGHT = 32;

    private InventoryTabLayout() {
    }

    public static Position findPosition(int preferredX, int preferredY, int tabCount, int screenWidth, int screenHeight, Collection<Bounds> occupied) {
        int stripWidth = tabCount * TAB_WIDTH;
        Position preferred = new Position(preferredX, preferredY);
        if (tabCount <= 0 || (fits(preferred, stripWidth, screenWidth, screenHeight) && !collides(preferred, stripWidth, occupied))) {
            return preferred;
        }

        Set<Integer> candidateXs = new LinkedHashSet<>();
        candidateXs.add(preferredX);
        candidateXs.add(0);
        candidateXs.add(screenWidth - stripWidth);
        for (Bounds bounds : occupied) {
            candidateXs.add(bounds.right());
            candidateXs.add(bounds.x() - stripWidth);
        }

        Set<Integer> candidateYs = new LinkedHashSet<>();
        candidateYs.add(preferredY);
        candidateYs.add(0);
        candidateYs.add(screenHeight - TAB_HEIGHT);
        for (Bounds bounds : occupied) {
            candidateYs.add(bounds.y() - TAB_HEIGHT);
            candidateYs.add(bounds.bottom());
        }

        List<Position> candidates = new ArrayList<>();
        for (int y : candidateYs) {
            for (int x : candidateXs) {
                candidates.add(new Position(x, y));
            }
        }

        Comparator<Position> closestToPreferred = Comparator.comparingInt(position -> score(position, preferred));
        return candidates.stream()
                .filter(position -> fits(position, stripWidth, screenWidth, screenHeight))
                .filter(position -> !collides(position, stripWidth, occupied))
                .min(closestToPreferred)
                .orElseGet(() -> candidates.stream()
                        .filter(position -> fits(position, stripWidth, screenWidth, screenHeight))
                        .min(Comparator.comparingInt((Position position) -> overlapArea(position, stripWidth, occupied)).thenComparing(closestToPreferred))
                        .orElse(preferred));
    }

    private static boolean collides(Position position, int stripWidth, Collection<Bounds> occupied) {
        Bounds strip = new Bounds(position.x(), position.y(), stripWidth, TAB_HEIGHT);
        return occupied.stream().anyMatch(strip::intersects);
    }

    private static boolean fits(Position position, int stripWidth, int screenWidth, int screenHeight) {
        return position.x() >= 0 && position.x() + stripWidth <= screenWidth
                && position.y() >= 0 && position.y() + TAB_HEIGHT <= screenHeight;
    }

    private static int score(Position position, Position preferred) {
        return Math.abs(position.x() - preferred.x()) + (Math.abs(position.y() - preferred.y()) * 4);
    }

    private static int overlapArea(Position position, int stripWidth, Collection<Bounds> occupied) {
        Bounds strip = new Bounds(position.x(), position.y(), stripWidth, TAB_HEIGHT);
        return occupied.stream().mapToInt(bounds -> strip.overlapArea(bounds)).sum();
    }

    public record Position(int x, int y) {
    }

    public record Bounds(int x, int y, int width, int height) {
        public int right() {
            return this.x + this.width;
        }

        public int bottom() {
            return this.y + this.height;
        }

        public boolean intersects(Bounds other) {
            return this.x < other.right() && this.right() > other.x
                    && this.y < other.bottom() && this.bottom() > other.y;
        }

        public int overlapArea(Bounds other) {
            int overlapWidth = Math.max(0, Math.min(this.right(), other.right()) - Math.max(this.x, other.x));
            int overlapHeight = Math.max(0, Math.min(this.bottom(), other.bottom()) - Math.max(this.y, other.y));
            return overlapWidth * overlapHeight;
        }
    }
}
