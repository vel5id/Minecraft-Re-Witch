package com.vel5id.hexerei.ritual;

import com.vel5id.hexerei.ritual.RuneShapes.Connections;
import com.vel5id.hexerei.ritual.RuneShapes.Diagonal;
import com.vel5id.hexerei.ritual.RuneShapes.Dir8;
import com.vel5id.hexerei.ritual.RuneShapes.Shape;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuneShapesTest {

    /** Build a {@code hasNeighbour} predicate from an explicit set of present directions. */
    private static Predicate<Dir8> present(Dir8... dirs) {
        Set<Dir8> set = EnumSet.noneOf(Dir8.class);
        for (Dir8 d : dirs) {
            set.add(d);
        }
        return set::contains;
    }

    private static Connections of(Dir8... dirs) {
        return RuneShapes.connections(present(dirs));
    }

    // --- isolated / endpoint ---

    @Test void isolatedIsEndpoint() {
        Connections c = of();
        assertEquals(Shape.ENDPOINT, c.shape());
        assertTrue(c.diagonals().isEmpty());
    }

    @Test void singleNeighbourIsEndpointFacingThatNeighbour() {
        assertEquals(Shape.ENDPOINT, of(Dir8.N).shape());
        assertEquals(0, of(Dir8.N).rotation());
        assertEquals(90, of(Dir8.E).rotation());
        assertEquals(180, of(Dir8.S).rotation());
        assertEquals(270, of(Dir8.W).rotation());
    }

    // --- straight line (two opposite ortho) ---

    @Test void verticalLineIsLineRotationZero() {
        Connections c = of(Dir8.N, Dir8.S);
        assertEquals(Shape.LINE, c.shape());
        assertEquals(0, c.rotation());
    }

    @Test void horizontalLineIsLineRotationNinety() {
        Connections c = of(Dir8.E, Dir8.W);
        assertEquals(Shape.LINE, c.shape());
        assertEquals(90, c.rotation());
    }

    // --- corner (two adjacent ortho), all four rotations ---

    @Test void cornerRotationsMatchClockwiseFromNorthEast() {
        assertEquals(Shape.CORNER, of(Dir8.N, Dir8.E).shape());
        assertEquals(0, of(Dir8.N, Dir8.E).rotation());
        assertEquals(90, of(Dir8.E, Dir8.S).rotation());
        assertEquals(180, of(Dir8.S, Dir8.W).rotation());
        assertEquals(270, of(Dir8.W, Dir8.N).rotation());
    }

    // --- T junction (three ortho), rotation keyed off the missing arm ---

    @Test void tJunctionRotationsKeyedOffTheGap() {
        assertEquals(Shape.T, of(Dir8.N, Dir8.E, Dir8.S).shape());
        assertEquals(0, of(Dir8.N, Dir8.E, Dir8.S).rotation());   // gap = W
        assertEquals(90, of(Dir8.E, Dir8.S, Dir8.W).rotation());  // gap = N
        assertEquals(180, of(Dir8.S, Dir8.W, Dir8.N).rotation()); // gap = E
        assertEquals(270, of(Dir8.W, Dir8.N, Dir8.E).rotation()); // gap = S
    }

    // --- cross (four ortho) ---

    @Test void fourOrthoIsCross() {
        Connections c = of(Dir8.N, Dir8.E, Dir8.S, Dir8.W);
        assertEquals(Shape.CROSS, c.shape());
        assertEquals(0, c.rotation());
    }

    // --- diagonals are overlays, independent of the base shape ---

    @Test void diagonalOnlyIsEndpointBaseWithDiagonalOverlay() {
        Connections c = of(Dir8.NE);
        assertEquals(Shape.ENDPOINT, c.shape());
        assertEquals(EnumSet.of(Diagonal.NE), c.diagonals());
    }

    @Test void allFourDiagonalsOverlayOnEndpointBase() {
        Connections c = of(Dir8.NE, Dir8.SE, Dir8.SW, Dir8.NW);
        assertEquals(Shape.ENDPOINT, c.shape());
        assertEquals(EnumSet.allOf(Diagonal.class), c.diagonals());
    }

    @Test void diagonalsCoexistWithABaseLine() {
        Connections c = of(Dir8.N, Dir8.S, Dir8.NE);
        assertEquals(Shape.LINE, c.shape());
        assertEquals(0, c.rotation());
        assertEquals(EnumSet.of(Diagonal.NE), c.diagonals());
    }

    // --- direction metadata sanity ---

    @Test void orthogonalFlagSplitsCardinalsFromDiagonals() {
        assertTrue(Dir8.N.orthogonal && Dir8.E.orthogonal && Dir8.S.orthogonal && Dir8.W.orthogonal);
        assertTrue(!Dir8.NE.orthogonal && !Dir8.SE.orthogonal && !Dir8.SW.orthogonal && !Dir8.NW.orthogonal);
    }
}
