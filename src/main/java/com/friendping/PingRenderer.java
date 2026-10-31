package com.friendping;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public final class PingRenderer {
    // Цвета: белое ядро кольца, бирюзовое свечение, синий ореол
    private static final int[] WHITE = {235, 250, 255};
    private static final int[] CYAN = {90, 220, 255};
    private static final int[] BLUE = {40, 110, 255};

    private static final double TAU = Math.PI * 2.0;
    // Количество сторон кольца (меньше число, тем заметнее многоугольник, как на референсе)
    private static final int SEGMENTS = 40;
    // Количество ромбиков и штрихов (каждый четвёртый это штрих)
    private static final int SHARDS = 48;
    // Чем меньше число, тем быстрее метка растёт с расстоянием
    private static final double DIST_SCALE = 16.0;
    // Высота тонкого светового луча над меткой (0 отключает луч)
    private static final double BEAM_HEIGHT = 2.2;

    private PingRenderer() {}

    public static void render(WorldRenderContext context) {
        MatrixStack matrices = context.matrixStack();
        if (matrices == null || context.camera() == null) return;

        Vec3d camera = context.camera().getPos();
        long now = System.currentTimeMillis();

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f pos = matrices.peek().getPositionMatrix();

        BufferBuilder buf = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        boolean any = false;

        for (FriendPingState.Ping ping : FriendPingState.pings()) {
            any = true;
            double age = (now - ping.createdAt()) / (double) FriendPingState.PING_LIFETIME_MS;
            double fade = Math.max(0.0, Math.min(1.0, (1.0 - age) * 1.7));

            // Размер растёт с расстоянием, чтобы метка была видна и на 256 блоках
            double dist = camera.distanceTo(ping.pos());
            double s = Math.max(1.0, dist / DIST_SCALE);

            // Пульсация
            double beat = 0.5 + 0.5 * Math.sin(now / 420.0);
            double mul = fade * (0.85 + 0.15 * beat);
            double radius = (0.85 + 0.05 * beat) * s;

            double x = ping.pos().x;
            double y = ping.pos().y + 0.05 * s;
            double z = ping.pos().z;

            // Радиус каждой вершины кольца слегка "дрожит", чтобы контур был неровным
            double[] rad = new double[SEGMENTS + 1];
            for (int k = 0; k <= SEGMENTS; k++) {
                int i = k % SEGMENTS;
                double w = 0.5 * Math.sin(i * 2.3 + now / 700.0) + 0.5 * Math.sin(i * 5.1 - now / 530.0);
                rad[k] = radius * (1.0 + 0.035 * w);
            }

            // Внешний мягкий ореол
            band(buf, pos, x, y, z, rad, s, 0.0, 0.55, BLUE, a(120, mul), BLUE, 0);
            // Внутреннее свечение у края
            band(buf, pos, x, y, z, rad, s, -0.45, 0.0, BLUE, 0, CYAN, a(90, mul));
            // Бирюзовая полоса
            band(buf, pos, x, y, z, rad, s, -0.06, 0.06, CYAN, a(170, mul), CYAN, a(170, mul));
            // Белое ядро
            band(buf, pos, x, y, z, rad, s, -0.025, 0.025, WHITE, a(255, mul), WHITE, a(255, mul));

            // Ромбики и штрихи внутри
            shards(buf, pos, x, y, z, radius, s, now, mul);

            // Тонкий световой луч
            if (BEAM_HEIGHT > 0) {
                beam(buf, pos, x, y, z, 0.05 * s, BEAM_HEIGHT * s, mul);
            }
        }

        // Застывший розовый силуэт игрока (если метка стоит на игроке)
        FrozenGlow.draw(buf, pos, camera, now);

        matrices.pop();

        if (!any) return;
        BuiltBuffer built = buf.endNullable();
        if (built == null) return;

        // Рисуем поверх всего: без проверки глубины, с аддитивным свечением
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferRenderer.drawWithGlobalProgram(built);

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static int a(int base, double mul) {
        return (int) Math.max(0, Math.min(255, base * mul));
    }

    // Псевдослучайное число от 0 до 1 по номеру (одинаковое каждый кадр)
    private static double hash(int n) {
        double x = Math.sin(n * 12.9898) * 43758.5453;
        return x - Math.floor(x);
    }

    // Полоса вдоль кольца с градиентом: от offIn до offOut (в долях масштаба s) относительно края
    private static void band(BufferBuilder buf, Matrix4f m, double cx, double y, double cz,
                             double[] rad, double s, double offIn, double offOut,
                             int[] cIn, int aIn, int[] cOut, int aOut) {
        int n = rad.length - 1;
        for (int k = 0; k < n; k++) {
            double a1 = TAU * k / n;
            double a2 = TAU * (k + 1) / n;
            double c1 = Math.cos(a1), s1 = Math.sin(a1), c2 = Math.cos(a2), s2 = Math.sin(a2);
            double r1i = Math.max(0.0, rad[k] + offIn * s);
            double r1o = Math.max(0.0, rad[k] + offOut * s);
            double r2i = Math.max(0.0, rad[k + 1] + offIn * s);
            double r2o = Math.max(0.0, rad[k + 1] + offOut * s);
            v(buf, m, new double[]{cx + c1 * r1i, y, cz + s1 * r1i}, cIn, aIn);
            v(buf, m, new double[]{cx + c1 * r1o, y, cz + s1 * r1o}, cOut, aOut);
            v(buf, m, new double[]{cx + c2 * r2o, y, cz + s2 * r2o}, cOut, aOut);
            v(buf, m, new double[]{cx + c2 * r2i, y, cz + s2 * r2i}, cIn, aIn);
        }
    }

    // Мелкие ромбики (мерцают) и штрихи (вылетают от центра к краю и гаснут)
    private static void shards(BufferBuilder buf, Matrix4f m, double cx, double y, double cz,
                               double radius, double s, long now, double mul) {
        for (int i = 0; i < SHARDS; i++) {
            double h1 = hash(i), h2 = hash(i + 1000), h3 = hash(i + 2000);
            boolean streak = i % 4 == 0;
            double ang, r, lift, hl, hw, alphaF;

            if (streak) {
                double period = 1100.0 + 900.0 * h3;
                double t = (now / period + h2) % 1.0;
                ang = h1 * TAU;
                r = radius * (0.25 + 0.8 * t);
                lift = (0.02 + 0.12 * t) * s;
                hl = 0.30 * s;
                hw = 0.018 * s;
                alphaF = Math.sin(Math.PI * t);
            } else {
                ang = h1 * TAU + now / 5000.0 * (h3 - 0.5);
                r = radius * (0.3 + 0.85 * h2);
                lift = (0.03 + 0.10 * (0.5 + 0.5 * Math.sin(now / 500.0 + i))) * s;
                hl = 0.07 * s;
                hw = 0.04 * s;
                double tw = Math.sin(now / 260.0 + h3 * 20.0);
                alphaF = 0.35 + 0.65 * tw * tw;
            }

            int[] col = (i % 3 == 0) ? CYAN : WHITE;
            int al = a(255, mul * alphaF);
            if (al <= 0) continue;

            double dx = Math.cos(ang), dz = Math.sin(ang);
            double tx = -dz, tz = dx;
            double px = cx + dx * r, pz = cz + dz * r, py = y + lift;

            double[] head = {px + dx * hl, py, pz + dz * hl};
            double[] side1 = {px + tx * hw, py, pz + tz * hw};
            double[] tail = {px - dx * hl, py, pz - dz * hl};
            double[] side2 = {px - tx * hw, py, pz - tz * hw};

            if (streak) {
                // голова яркая, хвост гаснет
                v(buf, m, head, col, al);
                v(buf, m, side1, col, (int) (al * 0.6));
                v(buf, m, tail, col, (int) (al * 0.05));
                v(buf, m, side2, col, (int) (al * 0.6));
            } else {
                v(buf, m, head, col, al);
                v(buf, m, side1, col, al);
                v(buf, m, tail, col, al);
                v(buf, m, side2, col, al);
            }
        }
    }

    // Тонкий вертикальный луч, который тает к верхушке
    private static void beam(BufferBuilder buf, Matrix4f m, double cx, double y, double cz,
                             double r, double h, double mul) {
        int sides = 8;
        for (int k = 0; k < sides; k++) {
            double a1 = TAU * k / sides;
            double a2 = TAU * (k + 1) / sides;
            double x1 = cx + Math.cos(a1) * r, z1 = cz + Math.sin(a1) * r;
            double x2 = cx + Math.cos(a2) * r, z2 = cz + Math.sin(a2) * r;
            v(buf, m, new double[]{x1, y, z1}, CYAN, a(90, mul));
            v(buf, m, new double[]{x2, y, z2}, CYAN, a(90, mul));
            v(buf, m, new double[]{x2, y + h, z2}, CYAN, 0);
            v(buf, m, new double[]{x1, y + h, z1}, CYAN, 0);
        }
    }

    private static void v(BufferBuilder buf, Matrix4f m, double[] p, int[] c, int alpha) {
        buf.vertex(m, (float) p[0], (float) p[1], (float) p[2])
                .color(c[0], c[1], c[2], Math.max(0, Math.min(255, alpha)));
    }
}
