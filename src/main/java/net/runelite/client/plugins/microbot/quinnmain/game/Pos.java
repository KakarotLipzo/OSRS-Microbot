package net.runelite.client.plugins.microbot.quinnmain.game;

/**
 * Client-neutral world coordinate — same values as DreamBot {@code Tile} / RuneLite {@code WorldPoint}.
 * Every tile/area constant in the ported logic uses this; adapters convert at the boundary. Top-level
 * (not nested in GameApi) because it's referenced pervasively across the ported code.
 */
public final class Pos {
    public final int x, y, plane;
    public Pos(int x, int y, int plane) { this.x = x; this.y = y; this.plane = plane; }
    public Pos(int x, int y) { this(x, y, 0); }

    // Tile-compatible accessors so DreamBot-style `.getX()/.getY()/.getZ()` call sites port unchanged.
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return plane; }
    public int getPlane() { return plane; }

    /** Chebyshev tile distance (matches DreamBot Tile.distance for straight-line reach checks). */
    public double distance(Pos o) {
        if (o == null) return Double.MAX_VALUE;
        return Math.max(Math.abs(x - o.x), Math.abs(y - o.y));
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof Pos)) return false;
        Pos p = (Pos) o; return x == p.x && y == p.y && plane == p.plane;
    }
    @Override public int hashCode() { return (x * 31 + y) * 31 + plane; }
    @Override public String toString() { return "(" + x + "," + y + "," + plane + ")"; }
}
