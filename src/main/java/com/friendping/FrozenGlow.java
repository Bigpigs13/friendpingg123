package com.friendping;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class FrozenGlow {
    public record Ghost(long createdAt, Vec3d pos, float bodyYaw, float headYaw, float pitch, float swing) {}

    // Розовый glow
    private static final int[] GLOW = {255, 60, 210};
    // Силуэт растёт с расстоянием медленнее кольца (чем больше число, тем меньше рост)
    private static final double GHOST_DIST_SCALE = 64.0;
    // Радиус поиска игрока вокруг метки (в блоках)
    private static final double MATCH_RADIUS = 0.7;

    private static final Map<Long, Ghost> GHOSTS = new HashMap<>();
    private static final Set<Long> CHECKED = new HashSet<>();

    private FrozenGlow() {}

    public static Collection<Ghost> ghosts() {
        return GHOSTS.values();
    }

    // Вызывать каждый тик: делает снимок игрока, если метка поставлена на него
    public static void update(MinecraftClient client) {
        if (client.world == null) {
            GHOSTS.clear();
            CHECKED.clear();
            return;
        }

        Set<Long> alive = new HashSet<>();
        for (FriendPingState.Ping ping : FriendPingState.pings()) {
            long id = ping.createdAt();
            alive.add(id);
            if (!CHECKED.add(id)) continue; // этот пинг уже обработан

            PlayerEntity target = null;
            double best = MATCH_RADIUS * MATCH_RADIUS;
            for (PlayerEntity p : client.world.getPlayers()) {
                double dx = p.getX() - ping.pos().x;
                double dz = p.getZ() - ping.pos().z;
                double dy = p.getY() - ping.pos().y;
                double d = dx * dx + dz * dz;
                if (Math.abs(dy) < 1.0 && d < best) {
                    best = d;
                    target = p;
                }
            }
            if (target == null) continue;

            float swing = MathHelper.cos(target.limbAnimator.getPos() * 0.6662f)
                    * 1.4f * Math.min(target.limbAnimator.getSpeed(), 1.0f);
            GHOSTS.put(id, new Ghost(id, ping.pos(),
                    target.getBodyYaw(), target.getHeadYaw(), target.getPitch(), swing));
        }

        GHOSTS.keySet().retainAll(alive);
        CHECKED.retainAll(alive);
    }

    public static void draw(BufferBuilder buf, Matrix4f m, Vec3d camera, long now) {
        for (Ghost g : GHOSTS.values()) {
            double ageMs = now - g.createdAt();
            double age = ageMs / (double) FriendPingState.PING_LIFETIME_MS;
            double fade = Math.max(0.0, Math.min(1.0, (1.0 - age) * 1.7));
            // Яркая вспышка в момент метки, потом ровное свечение
            double flash = 1.0 + 1.2 * Math.max(0.0, 1.0 - ageMs / 450.0);
            double beat = 0.85 + 0.15 * Math.sin(now / 260.0);
            double mul = fade * flash * beat;

            double dist = camera.distanceTo(g.pos());
            double s = Math.max(1.0, dist / GHOST_DIST_SCALE);
            double u = 0.9375 / 16.0 * s; // размер одного "пикселя" модели в блоках

            double yaw = Math.toRadians(g.bodyYaw());
            double headRel = Math.toRadians(g.headYaw() - g.bodyYaw());
            double pitch = Math.toRadians(g.pitch());
            double sw = g.swing();

            // два слоя: яркая основа и широкое слабое свечение вокруг
            for (int layer = 0; layer < 2; layer++) {
                double infl = layer == 0 ? 0.0 : 1.4;
                int alpha = (int) Math.min(255, (layer == 0 ? 120 : 40) * mul);
                if (alpha <= 0) continue;

                // ноги
                box(buf, m, g.pos(), u, yaw, -2, 12, 0, sw, 0, -4, 0, -2, 0, 12, 2, infl, alpha);
                box(buf, m, g.pos(), u, yaw, 2, 12, 0, -sw, 0, 0, 0, -2, 4, 12, 2, infl, alpha);
                // туловище
                box(buf, m, g.pos(), u, yaw, 0, 0, 0, 0, 0, -4, 12, -2, 4, 24, 2, infl, alpha);
                // руки
                box(buf, m, g.pos(), u, yaw, -6, 22, 0, -sw, 0, -8, 12, -2, -4, 24, 2, infl, alpha);
                box(buf, m, g.pos(), u, yaw, 6, 22, 0, sw, 0, 4, 12, -2, 8, 24, 2, infl, alpha);
                // голова (наклон и поворот независимо от тела)
                box(buf, m, g.pos(), u, yaw, 0, 24, 0, pitch, headRel, -4, 24, -4, 4, 32, 4, infl, alpha);
            }
        }
    }

    // Параллелепипед в координатах модели (в "пикселях"), с поворотом вокруг точки (px,py,pz)
    private static void box(BufferBuilder buf, Matrix4f m, Vec3d o, double u, double yaw,
                            double px, double py, double pz, double rotX, double extraYaw,
                            double x0, double y0, double z0, double x1, double y1, double z1,
                            double infl, int alpha) {
        x0 -= infl; y0 -= infl; z0 -= infl;
        x1 += infl; y1 += infl; z1 += infl;

        double[][] c = new double[8][];
        for (int ix = 0; ix < 2; ix++) {
            for (int iy = 0; iy < 2; iy++) {
                for (int iz = 0; iz < 2; iz++) {
                    c[ix * 4 + iy * 2 + iz] = tr(ix == 0 ? x0 : x1, iy == 0 ? y0 : y1, iz == 0 ? z0 : z1,
                            px, py, pz, rotX, extraYaw, yaw, u, o);
                }
            }
        }

        int[][] faces = {{0, 4, 6, 2}, {1, 5, 7, 3}, {0, 1, 3, 2}, {4, 5, 7, 6}, {0, 4, 5, 1}, {2, 6, 7, 3}};
        for (int[] f : faces) {
            for (int idx : f) {
                buf.vertex(m, (float) c[idx][0], (float) c[idx][1], (float) c[idx][2])
                        .color(GLOW[0], GLOW[1], GLOW[2], alpha);
            }
        }
    }

    private static double[] tr(double x, double y, double z, double px, double py, double pz,
                               double rotX, double extraYaw, double yaw, double u, Vec3d o) {
        double rx = x - px, ry = y - py, rz = z - pz;

        // поворот вокруг оси X (наклон головы, взмах рук и ног)
        double cx = Math.cos(rotX), sx = Math.sin(rotX);
        double y1 = ry * cx - rz * sx;
        double z1 = ry * sx + rz * cx;

        // дополнительный поворот вокруг Y (голова относительно тела)
        double ce = Math.cos(extraYaw), se = Math.sin(extraYaw);
        double x2 = rx * ce - z1 * se + px;
        double z2 = rx * se + z1 * ce + pz;
        double y2 = y1 + py;

        // поворот тела
        double cb = Math.cos(yaw), sb = Math.sin(yaw);
        double wx = x2 * cb - z2 * sb;
        double wz = x2 * sb + z2 * cb;

        return new double[]{o.x + wx * u, o.y + y2 * u, o.z + wz * u};
    }
}
