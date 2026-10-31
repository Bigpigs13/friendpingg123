package com.friendping;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class FriendPingClient implements ClientModInitializer {
    private static final double MAX_DIST = 256.0;
    private static KeyBinding pingKey;

    @Override
    public void onInitializeClient() {
        FriendPingState.load();

        // Бинд: средняя кнопка мыши (можно сменить в настройках управления)
        pingKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.friendping.ping",
                InputUtil.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
                "category.friendping"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (pingKey.wasPressed()) {
                createPing(client);
            }
            FriendPingState.expire();
        });

        registerCommands();
        ClientReceiveMessageEvents.GAME.register((message, overlay) ->
                FriendPingState.tryReceive(message.getString()));

        WorldRenderEvents.AFTER_ENTITIES.register(PingRenderer::render);
    }

    private static void createPing(MinecraftClient client) {
        Entity cam = client.getCameraEntity();
        if (client.player == null || client.world == null || cam == null) return;

        Vec3d start = cam.getCameraPosVec(1.0f);
        Vec3d dir = cam.getRotationVec(1.0f);
        Vec3d end = start.add(dir.multiply(MAX_DIST));

        // Блок на дистанции до 256
        HitResult blockHit = cam.raycast(MAX_DIST, 1.0f, false);
        double blockDistSq = blockHit.getType() == HitResult.Type.MISS
                ? Double.MAX_VALUE : blockHit.getPos().squaredDistanceTo(start);

        // Игрок на дистанции до 256
        Box box = cam.getBoundingBox().stretch(dir.multiply(MAX_DIST)).expand(1.0);
        EntityHitResult entityHit = ProjectileUtil.raycast(
                cam, start, end, box,
                e -> e instanceof PlayerEntity && !e.isSpectator() && e.canHit(),
                MAX_DIST * MAX_DIST);

        Vec3d pos;
        String targetName = "";

        if (entityHit != null && entityHit.getPos().squaredDistanceTo(start) < blockDistSq) {
            pos = entityHit.getEntity().getPos();
            targetName = entityHit.getEntity().getName().getString();
        } else if (blockHit instanceof BlockHitResult bh && blockHit.getType() == HitResult.Type.BLOCK) {
            // над блоком: центр по X/Z, верхняя грань по Y
            pos = new Vec3d(bh.getBlockPos().getX() + 0.5,
                    bh.getBlockPos().getY() + 1.0,
                    bh.getBlockPos().getZ() + 0.5);
        } else {
            return;
        }

        FriendPingState.addLocal(pos, targetName);
        FriendPingState.broadcast(client, pos, targetName);
        client.player.sendMessage(Text.literal("§bFriendPing §7» §fМетка отправлена друзьям"), true);
    }

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("fp")
                        .then(literal("add")
                                .then(argument("ник", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "ник");
                                            boolean added = FriendPingState.addFriend(name);
                                            feedback(added ? "§bДобавлен друг: §f" + name : "§eЭтот ник уже есть в списке");
                                            return 1;
                                        })))
                        .then(literal("remove")
                                .then(argument("ник", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "ник");
                                            boolean removed = FriendPingState.removeFriend(name);
                                            feedback(removed ? "§cУдалён друг: §f" + name : "§eТакого друга нет в списке");
                                            return 1;
                                        })))
                        .then(literal("list").executes(ctx -> {
                            feedback(FriendPingState.friendList());
                            return 1;
                        }))
                        .then(literal("clear").executes(ctx -> {
                            FriendPingState.clearFriends();
                            feedback("§cСписок друзей очищен");
                            return 1;
                        }))
                        .then(literal("ping").executes(ctx -> {
                            createPing(MinecraftClient.getInstance());
                            return 1;
                        }))
                ));
    }

    private static void feedback(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) client.player.sendMessage(Text.literal("§bFriendPing §7» " + text), false);
    }
}
