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
    // Основной розово-фиолетовый неон, светлая верхушка столбиков, синее внутреннее кольцо
    private static final int[] PINK = {255, 50, 200};
    private static final int[] TOP = {255, 190, 255};
    private static final int[] BLUE = {70, 130, 255};

    private static final int SEGMENTS = 64;
    private static final int BARS = 56;
    // Чем меньше число, тем быстрее метка растёт с расстоянием
    private static final double DIST_SCALE = 16.0;

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
            double beat = 0.5 + 0.5 * Math.sin(now / 260.0);
            double radius = (0.55 + 0.07 * beat) * s;
            double glow = 0.7 + 0.3 * beat;

            double x = ping.pos().x;
            double y = ping.pos().y + 0.06 * s;
            double z = ping.pos().z;
            double lift = 0.002 * s;

            // Свечение под основным кольцом
            ring(buf, pos, x, y, z, radius - 0.10 * s, radius + 0.16 * s, PINK, a(45, fade, glow));
            // Основное толстое кольцо
            ring(buf, pos, x, y + lift, z, radius - 0.03 * s, radius + 0.06 * s, PINK, a(200, fade, glow));
            // Тонкое внутреннее синее кольцо
            ring(buf, pos, x, y + 2 * lift, z, radius - 0.15 * s, radius - 0.12 * s, BLUE, a(230, fade, 1.0));

            // Столбики-«волны» вверх
            for (int i = 0; i < BARS; i++) {
                double ang = Math.PI * 2.0 * i / BARS;
                double h = (0.08 + 0.55
                        * (0.5 + 0.5 * Math.sin(now / 170.0 + i * 1.7))
                        * (0.5 + 0.5 * Math.sin(now / 290.0 + i * 0.9))) * s;
                bar(buf, pos, x, y, z, ang, radius + 0.03 * s, h, 0.045 * s, a(230, fade, 1.0));
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

    private static int a(int base, double fade, double mul) {
        return (int) Math.max(0, Math.min(255, base * fade * mul));
    }

    // Плоское кольцо (кольцевая полоса) на высоте y
    private static void ring(BufferBuilder buf, Matrix4f m, double cx, double y, double cz,
                             double rIn, double rOut, int[] c, int alpha) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a1 = Math.PI * 2.0 * i / SEGMENTS;
            double a2 = Math.PI * 2.0 * (i + 1) / SEGMENTS;
            double c1 = Math.cos(a1), s1 = Math.sin(a1), c2 = Math.cos(a2), s2 = Math.sin(a2);
            double[] p1 = {cx + c1 * rIn, y, cz + s1 * rIn};
            double[] p2 = {cx + c1 * rOut, y, cz + s1 * rOut};
            double[] p3 = {cx + c2 * rOut, y, cz + s2 * rOut};
            double[] p4 = {cx + c2 * rIn, y, cz + s2 * rIn};
            quad(buf, m, p1, p2, p3, p4, c, alpha, c, alpha);
        }
    }

    // Вертикальный столбик на кольце
    private static void bar(BufferBuilder buf, Matrix4f m, double cx, double y, double cz,
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
        quad(buf, m, b1, b2, t2, t1, PINK, alpha, TOP, alpha / 2);
    }

    // Четырёхугольник (отсечение граней выключено, виден с обеих сторон)
    private static void quad(BufferBuilder buf, Matrix4f m,
                             double[] p1, double[] p2, double[] p3, double[] p4,
                             int[] c1, int a1, int[] c2, int a2) {
        v(buf, m, p1, c1, a1);
        v(buf, m, p2, c1, a1);
        v(buf, m, p3, c2, a2);
        v(buf, m, p4, c2, a2);
    }

    private static void v(BufferBuilder buf, Matrix4f m, double[] p, int[] c, int alpha) {
        buf.vertex(m, (float) p[0], (float) p[1], (float) p[2])
                .color(c[0], c[1], c[2], Math.max(0, Math.min(255, alpha)));
    }
}
