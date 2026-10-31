package com.friendping;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

public final class PingRenderer {
    // Основной цвет (розово-фиолетовый неон) и внутреннее кольцо (синий)
    private static final int[] PINK = {255, 50, 200};
    private static final int[] TOP = {255, 190, 255};
    private static final int[] BLUE = {70, 130, 255};

    private static final int SEGMENTS = 64;
    private static final int BARS = 56;

    private PingRenderer() {}

    public static void render(WorldRenderContext context) {
        VertexConsumerProvider consumers = context.consumers();
        MatrixStack matrices = context.matrixStack();
        if (consumers == null || matrices == null || context.camera() == null) return;

        Vec3d camera = context.camera().getPos();
        long now = System.currentTimeMillis();
        VertexConsumer buf = consumers.getBuffer(RenderLayer.getLightning());

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        MatrixStack.Entry entry = matrices.peek();

        for (FriendPingState.Ping ping : FriendPingState.pings()) {
            double age = (now - ping.createdAt()) / (double) FriendPingState.PING_LIFETIME_MS;
            double fade = Math.max(0.0, Math.min(1.0, (1.0 - age) * 1.7));

            // Пульсация: кольцо "дышит" и меняет яркость
            double beat = 0.5 + 0.5 * Math.sin(now / 260.0);
            double radius = 0.55 + 0.07 * beat;
            double glow = 0.7 + 0.3 * beat;

            double x = ping.pos().x;
            double y = ping.pos().y + 0.06;
            double z = ping.pos().z;

            // Свечение: широкое слабое кольцо под основным
            ring(buf, entry, x, y, z, radius - 0.10, radius + 0.16, PINK, a(45, fade, glow));
            // Основное толстое кольцо
            ring(buf, entry, x, y + 0.002, z, radius - 0.03, radius + 0.06, PINK, a(200, fade, glow));
            // Тонкое внутреннее синее кольцо
            ring(buf, entry, x, y + 0.004, z, radius - 0.15, radius - 0.12, BLUE, a(230, fade, 1.0));

            // Столбики-"волны" вверх, каждый движется по-своему
            for (int i = 0; i < BARS; i++) {
                double ang = Math.PI * 2.0 * i / BARS;
                double h = 0.08 + 0.55
                        * (0.5 + 0.5 * Math.sin(now / 170.0 + i * 1.7))
                        * (0.5 + 0.5 * Math.sin(now / 290.0 + i * 0.9));
                bar(buf, entry, x, y, z, ang, radius + 0.03, h, 0.045, a(230, fade, 1.0));
            }
        }
        matrices.pop();
    }

    private static int a(int base, double fade, double mul) {
        return (int) Math.max(0, Math.min(255, base * fade * mul));
    }

    // Плоское кольцо (кольцевая полоса) на высоте y
    private static void ring(VertexConsumer buf, MatrixStack.Entry e, double cx, double y, double cz,
                             double rIn, double rOut, int[] c, int alpha) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a1 = Math.PI * 2.0 * i / SEGMENTS;
            double a2 = Math.PI * 2.0 * (i + 1) / SEGMENTS;
            double c1 = Math.cos(a1), s1 = Math.sin(a1), c2 = Math.cos(a2), s2 = Math.sin(a2);
            double[] p1 = {cx + c1 * rIn, y, cz + s1 * rIn};
            double[] p2 = {cx + c1 * rOut, y, cz + s1 * rOut};
            double[] p3 = {cx + c2 * rOut, y, cz + s2 * rOut};
            double[] p4 = {cx + c2 * rIn, y, cz + s2 * rIn};
            quad(buf, e, p1, p2, p3, p4, c, alpha, c, alpha);
        }
    }

    // Вертикальный столбик, стоящий на кольце
    private static void bar(VertexConsumer buf, MatrixStack.Entry e, double cx, double y, double cz,
                            double ang, double r, double h, double halfW, int alpha) {
        double px = cx + Math.cos(ang) * r;
        double pz = cz + Math.sin(ang) * r;
        double tx = -Math.sin(ang) * halfW;
        double tz = Math.cos(ang) * halfW;
        double[] b1 = {px - tx, y, pz - tz};
        double[] b2 = {px + tx, y, pz + tz};
        double[] t2 = {px + tx, y + h, pz + tz};
        double[] t1 = {px - tx, y + h, pz - tz};
        // снизу розовый, к верхушке светлее и прозрачнее
        quad(buf, e, b1, b2, t2, t1, PINK, alpha, TOP, alpha / 2);
    }

    // Четырёхугольник с двух сторон (чтобы был виден с любой стороны).
    // Первые две точки получают цвет c1/a1, последние две - c2/a2.
    private static void quad(VertexConsumer buf, MatrixStack.Entry e,
                             double[] p1, double[] p2, double[] p3, double[] p4,
                             int[] c1, int a1, int[] c2, int a2) {
        v(buf, e, p1, c1, a1);
        v(buf, e, p2, c1, a1);
        v(buf, e, p3, c2, a2);
        v(buf, e, p4, c2, a2);
        // обратная сторона
        v(buf, e, p4, c2, a2);
        v(buf, e, p3, c2, a2);
        v(buf, e, p2, c1, a1);
        v(buf, e, p1, c1, a1);
    }

    private static void v(VertexConsumer buf, MatrixStack.Entry e, double[] p, int[] c, int alpha) {
        buf.vertex(e, (float) p[0], (float) p[1], (float) p[2])
                .color(c[0], c[1], c[2], Math.max(0, Math.min(255, alpha)));
    }
}
