package com.vel5id.hexerei.ritual;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Pure connection-shape logic for the {@code rune} decal block.
 *
 * <p>A rune connects to neighbouring runes orthogonally (N/E/S/W) and diagonally
 * (NE/SE/SW/NW), like vanilla {@code redstone_wire}/{@code tripwire}. The in-world
 * {@code RuneBlock} stores those eight connections as boolean blockstate properties and
 * renders them with a multipart blockstate. This class contains the decidable boolean
 * -> ({@link Shape}, Y-rotation) mapping that the multipart encodes, factored out so it
 * can be unit-tested without a Minecraft level (CLAUDE.md two-tier strategy).
 *
 * <p>The mapping here is the single source of truth that {@code blockstates/rune.json}
 * mirrors; the rotations match the multipart {@code y} values in the design spec
 * (§7): {@code rune_line} N|S=0 / E|W=90; {@code rune_corner} N+E=0 / E+S=90 / S+W=180
 * / W+N=270; {@code rune_t} arm-opposite-the-gap convention; {@code rune_cross}=0;
 * {@code rune_diagonal} overlay NE=0 / SE=90 / SW=180 / NW=270.
 *
 * <p>Note: the ritual-completeness matcher ({@code RitualActivation}) only tests for the
 * <em>presence</em> of a rune, never its connection shape, so this class is purely
 * cosmetic to activation and the geometry tests stay valid regardless of shape.
 */
public final class RuneShapes {
    private RuneShapes() {}

    /** The eight neighbour directions a rune can connect to. Orthogonal first, then diagonal. */
    public enum Dir8 {
        N(0, -1, true), E(1, 0, true), S(0, 1, true), W(-1, 0, true),
        NE(1, -1, false), SE(1, 1, false), SW(-1, 1, false), NW(-1, -1, false);

        /** Block-space X offset of this neighbour (east = +X). */
        public final int dx;
        /** Block-space Z offset of this neighbour (south = +Z). */
        public final int dz;
        /** True for the four cardinal directions, false for the four diagonals. */
        public final boolean orthogonal;

        Dir8(int dx, int dz, boolean orthogonal) {
            this.dx = dx;
            this.dz = dz;
            this.orthogonal = orthogonal;
        }
    }

    /**
     * The six base decal model classes a rune resolves to. Each maps to one
     * {@code rune_*} texture/model; a Y-rotation orients it (see {@link Connections#shape()}).
     */
    public enum Shape {
        /** Zero or one orthogonal connection — the standalone dot / terminus ({@code rune_end}). */
        ENDPOINT,
        /** Two opposite orthogonal connections (N+S or E+W) — {@code rune_line}. */
        LINE,
        /** Two adjacent orthogonal connections (an L-bend) — {@code rune_corner}. */
        CORNER,
        /** Three orthogonal connections — {@code rune_t}. */
        T,
        /** Four orthogonal connections — {@code rune_cross}. */
        CROSS
    }

    /** A diagonal overlay layered on top of the base shape, one per present diagonal. */
    public enum Diagonal {
        NE(0), SE(90), SW(180), NW(270);

        /** Y-rotation (degrees) the {@code rune_diagonal} model is applied with. */
        public final int rotation;

        Diagonal(int rotation) {
            this.rotation = rotation;
        }
    }

    /**
     * Resolved base shape plus its Y-rotation in degrees (0/90/180/270). The diagonal
     * overlays present alongside the base shape are listed separately; in the multipart
     * blockstate they stack on top, so they are returned here as their own set rather than
     * folded into {@code shape}/{@code rotation}.
     */
    public static final class Connections {
        private final Shape shape;
        private final int rotation;
        private final Set<Diagonal> diagonals;

        Connections(Shape shape, int rotation, Set<Diagonal> diagonals) {
            this.shape = shape;
            this.rotation = rotation;
            this.diagonals = diagonals;
        }

        /** The base model class selected by the orthogonal neighbours. */
        public Shape shape() {
            return shape;
        }

        /** Y-rotation of the base model, in degrees: one of 0, 90, 180, 270. */
        public int rotation() {
            return rotation;
        }

        /** Diagonal overlays present (may be empty); each renders as a rotated {@code rune_diagonal}. */
        public Set<Diagonal> diagonals() {
            return diagonals;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Connections other)) {
                return false;
            }
            return rotation == other.rotation
                    && shape == other.shape
                    && diagonals.equals(other.diagonals);
        }

        @Override
        public int hashCode() {
            int h = shape.hashCode();
            h = 31 * h + rotation;
            h = 31 * h + diagonals.hashCode();
            return h;
        }

        @Override
        public String toString() {
            return "Connections{shape=" + shape + ", rotation=" + rotation + ", diagonals=" + diagonals + '}';
        }
    }

    /**
     * Maps the eight connection booleans to the base shape, its rotation, and the diagonal
     * overlays the multipart blockstate should render.
     *
     * @param hasNeighbour returns true iff a connecting rune sits in the given direction
     */
    public static Connections connections(Predicate<Dir8> hasNeighbour) {
        boolean n = hasNeighbour.test(Dir8.N);
        boolean e = hasNeighbour.test(Dir8.E);
        boolean s = hasNeighbour.test(Dir8.S);
        boolean w = hasNeighbour.test(Dir8.W);

        int orthoCount = (n ? 1 : 0) + (e ? 1 : 0) + (s ? 1 : 0) + (w ? 1 : 0);

        Shape shape;
        int rotation;
        switch (orthoCount) {
            case 4 -> {
                shape = Shape.CROSS;
                rotation = 0;
            }
            case 3 -> {
                shape = Shape.T;
                // rotation keyed off which single arm is MISSING; base rune_t branches N/E/S (gap = W).
                if (!w) {
                    rotation = 0;        // N+E+S
                } else if (!n) {
                    rotation = 90;       // E+S+W
                } else if (!e) {
                    rotation = 180;      // S+W+N
                } else {
                    rotation = 270;      // W+N+E (gap = S)
                }
            }
            case 2 -> {
                if ((n && s) || (e && w)) {
                    shape = Shape.LINE;
                    rotation = (n && s) ? 0 : 90;          // base rune_line is vertical (N|S)
                } else {
                    shape = Shape.CORNER;
                    // base rune_corner is N+E; rotate clockwise through E+S, S+W, W+N.
                    if (n && e) {
                        rotation = 0;
                    } else if (e && s) {
                        rotation = 90;
                    } else if (s && w) {
                        rotation = 180;
                    } else {
                        rotation = 270;                    // w && n
                    }
                }
            }
            default -> {
                // zero or one orthogonal neighbour: terminus dot. Single-arm gets oriented
                // so the stroke points at its one neighbour (N=0, E=90, S=180, W=270).
                shape = Shape.ENDPOINT;
                if (n) {
                    rotation = 0;
                } else if (e) {
                    rotation = 90;
                } else if (s) {
                    rotation = 180;
                } else if (w) {
                    rotation = 270;
                } else {
                    rotation = 0;                          // isolated: no preferred facing
                }
            }
        }

        Set<Diagonal> diagonals = EnumSet.noneOf(Diagonal.class);
        if (hasNeighbour.test(Dir8.NE)) {
            diagonals.add(Diagonal.NE);
        }
        if (hasNeighbour.test(Dir8.SE)) {
            diagonals.add(Diagonal.SE);
        }
        if (hasNeighbour.test(Dir8.SW)) {
            diagonals.add(Diagonal.SW);
        }
        if (hasNeighbour.test(Dir8.NW)) {
            diagonals.add(Diagonal.NW);
        }

        return new Connections(shape, rotation, diagonals);
    }
}
