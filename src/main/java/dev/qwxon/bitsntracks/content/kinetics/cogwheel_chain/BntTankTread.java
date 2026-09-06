package dev.qwxon.bitsntracks.content.kinetics.cogwheel_chain;

import com.kipti.bnb.content.kinetics.cogwheel_chain.render.ChainQuadBuilder.VertexEmitter;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Tread plates joined by links, one plate and its links per link pitch. */
public final class BntTankTread {
    private static final double PX = 1.0 / 16.0;
    private static final double SHEET_U = 32.0;
    private static final double SHEET_V = 16.0;

    private static final double CELL = 4.0 * PX;
    private static final double TREAD_AT = 1.5 / 4.0;
    private static final double LINK_AT = 3.5 / 4.0;

    private static final double TREAD_W = 7.0 * PX;
    private static final double TREAD_T = 1.0 * PX;
    private static final double TREAD_L = 1.5 * PX;

    private static final double LINK_W = 1.5 * PX;
    private static final double LINK_T = 0.5 * PX;
    private static final double LINK_L = 0.5 * PX;
    private static final double LINK_SPREAD = 2.5 * PX;

    /** up, down, north, south, east, west. Each entry is u0, v0, u1, v1, rotation. */
    private static final double[][] TREAD_UV = {
        {0, 0, 14, 3, 0},
        {14, 0, 28, 3, 180},
        {14, 5, 0, 3, 0},
        {28, 5, 14, 3, 0},
        {28, 0, 30, 3, 90},
        {13, 0, 15, 3, 270}
    };

    private static final double[][] LINK_UV = {
        {3, 6, 6, 7, 0},
        {3, 8, 6, 9, 0},
        {3, 7, 6, 8, 0},
        {3, 7, 6, 8, 0},
        {6, 6, 7, 7, 0},
        {2, 6, 3, 7, 0}
    };

    private BntTankTread() {
    }

    /** Plates in a loop, rounded down off the belt length. */
    private static long plateCount() {
        double belt = BntBeltLinks.beltLength();
        return belt <= CELL ? 1L : Math.max(1L, (long)Math.floor(belt / CELL));
    }

    /** Length those plates take up. */
    private static double beltRun() {
        return plateCount() * CELL;
    }

    /** Emits every plate whose middle falls inside this segment of the path. */
    public static void emitSegment(
        VertexEmitter emitter, List<Vec3> source, List<Vec3> destination, double offset, double length
    ) {
        if (source.size() != 4 || destination.size() != 4 || length <= 1.0E-6) {
            return;
        }

        offset = BntBeltLinks.wrapScroll(offset, beltRun());
        double end = offset + length;
        long first = (long)Math.floor(offset / CELL) - 1L;
        long last = (long)Math.ceil(end / CELL) + 1L;

        for (long plate = first; plate <= last; plate++) {
            double base = plate * CELL;
            place(emitter, source, destination, offset, length, base + TREAD_AT * CELL, false);
            place(emitter, source, destination, offset, length, base + LINK_AT * CELL, true);
        }
    }

    private static void place(
        VertexEmitter emitter, List<Vec3> source, List<Vec3> destination,
        double offset, double length, double at, boolean link
    ) {
        if (at < offset || at >= offset + length) {
            return;
        }

        double along = (offset + length - at) / length;
        Vec3[] frame = new Vec3[4];
        for (int i = 0; i < 4; i++) {
            frame[i] = source.get(i).lerp(destination.get(i), along);
        }

        Vec3 centre = frame[0].add(frame[1]).add(frame[2]).add(frame[3]).scale(0.25);
        Vec3 wide = unit(frame[0].subtract(frame[1]));
        Vec3 thick = unit(frame[1].subtract(frame[2]));
        if (wide.lengthSqr() < 0.5 || thick.lengthSqr() < 0.5) {
            return;
        }

        Vec3 forward = unit(sourceCentre(destination).subtract(sourceCentre(source)));
        if (forward.lengthSqr() < 0.5) {
            forward = unit(wide.cross(thick));
            if (forward.lengthSqr() < 0.5) {
                return;
            }
        }

        if (link) {
            for (int side = -1; side <= 1; side += 2) {
                Vec3 seat = centre.add(wide.scale(LINK_SPREAD * side));
                box(emitter, seat, wide.scale(LINK_W), thick.scale(LINK_T), forward.scale(LINK_L), LINK_UV);
            }
        } else {
            box(emitter, centre, wide.scale(TREAD_W), thick.scale(TREAD_T), forward.scale(TREAD_L), TREAD_UV);
        }
    }

    private static Vec3 sourceCentre(List<Vec3> points) {
        return points.get(0).add(points.get(1)).add(points.get(2)).add(points.get(3)).scale(0.25);
    }

    private static Vec3 unit(Vec3 v) {
        double length = v.length();
        return length < 1.0E-9 ? Vec3.ZERO : v.scale(1.0 / length);
    }

    private static void box(VertexEmitter emitter, Vec3 c, Vec3 w, Vec3 t, Vec3 l, double[][] uv) {
        Vec3 pnn = c.subtract(w).subtract(t).subtract(l);
        Vec3 pnp = c.subtract(w).subtract(t).add(l);
        Vec3 ptn = c.subtract(w).add(t).subtract(l);
        Vec3 ptp = c.subtract(w).add(t).add(l);
        Vec3 qnn = c.add(w).subtract(t).subtract(l);
        Vec3 qnp = c.add(w).subtract(t).add(l);
        Vec3 qtn = c.add(w).add(t).subtract(l);
        Vec3 qtp = c.add(w).add(t).add(l);

        face(emitter, c, ptn, ptp, qtp, qtn, uv[0]);
        face(emitter, c, pnp, pnn, qnn, qnp, uv[1]);
        face(emitter, c, ptn, pnn, qnn, qtn, uv[2]);
        face(emitter, c, qtp, qnp, pnp, ptp, uv[3]);
        face(emitter, c, qtn, qnn, qnp, qtp, uv[4]);
        face(emitter, c, ptp, pnp, pnn, ptn, uv[5]);
    }

    /** One face, wound so it looks away from the middle of the box. */
    private static void face(VertexEmitter emitter, Vec3 middle, Vec3 a, Vec3 b, Vec3 d, Vec3 e, double[] uv) {
        Vec3 normal = b.subtract(a).cross(e.subtract(a));
        boolean flip = normal.dot(a.add(d).scale(0.5).subtract(middle)) < 0.0;
        Vec3[] corner = flip ? new Vec3[]{e, d, b, a} : new Vec3[]{a, b, d, e};

        float u0 = (float)(uv[0] / SHEET_U);
        float v0 = (float)(uv[1] / SHEET_V);
        float u1 = (float)(uv[2] / SHEET_U);
        float v1 = (float)(uv[3] / SHEET_V);
        float[][] coords = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};

        int turn = ((int)uv[4] / 90) & 3;
        for (int i = 0; i < 4; i++) {
            Vec3 point = corner[i];
            float[] st = coords[(i + turn) & 3];
            emitter.emit((float)point.x, (float)point.y, (float)point.z, st[0], st[1], 0.0F, 1.0F, 0.0F);
        }
    }
}
