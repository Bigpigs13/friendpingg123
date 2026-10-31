package com.friendping;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class PingRenderer {
    private PingRenderer() {}

    public static void render(WorldRenderContext context) {
        VertexConsumerProvider consumers = context.consumers();
        MatrixStack matrices = context.matrixStack();
        if (consumers == null || matrices == null || context.camera() == null) return;

        Vec3d camera = context.camera().getPos();
        long now = System.currentTimeMillis();
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f position = matrices.peek().getPositionMatrix();
        Matrix3f normal = matrices.peek().getNormalMatrix();

        for (FriendPingState.Ping ping : FriendPingState.pings()) {
            double age = (now - ping.createdAt()) / (double) FriendPingState.PING_LIFETIME_MS;
            double fade = Math.max(0.0, Math.min(1.0, (1.0 - age) * 1.7));
            double pulse = (now % 1800L) / 1800.0;
            double radius = 0.34 + pulse * 0.55;
            int alpha = (int) (155 * fade * (0.75 + 0.25 * Math.sin(now / 230.0)));

            double x = ping.pos().x;
            double y = ping.pos().y + 0.08;
            double z = ping.pos().z;

            // A single expanding cyan ring, slightly lifted above the block.
            for (int i = 0; i < 48; i++) {
                double a1 = Math.PI * 2.0 * i / 48.0;
                double a2 = Math.PI * 2.0 * (i + 1) / 48.0;
                vertex(lines, position, normal, x + Math.cos(a1) * radius, y, z + Math.sin(a1) * radius, alpha);
                vertex(lines, position, normal, x + Math.cos(a2) * radius, y, z + Math.sin(a2) * radius, alpha);
            }

            // Short, faint beacon-like vertical accent above the ring's center.
            vertex(lines, position, normal, x, y + 0.04, z, (int) (95 * fade));
            vertex(lines, position, normal, x, y + 0.72, z, (int) (95 * fade));
        }
        matrices.pop();
    }

    private static void vertex(VertexConsumer consumer, Matrix4f position, Matrix3f normal,
                               double x, double y, double z, int alpha) {
        consumer.vertex(position, (float) x, (float) y, (float) z)
                .color(115, 205, 232, Math.max(0, Math.min(255, alpha)))
                .normal(normal, 0.0f, 1.0f, 0.0f);
    }
}
