package com.friendping;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FriendPingState {
    public static final long PING_LIFETIME_MS = 15_000L;
    private static final String PREFIX = "FP1|";
    private static final List<String> FRIENDS = new ArrayList<>();
    private static final List<Ping> PINGS = new CopyOnWriteArrayList<>();
    private static final Path CONFIG = MinecraftClient.getInstance().runDirectory.toPath()
            .resolve("config").resolve("friendping.properties");

    private FriendPingState() {}

    public record Ping(Vec3d pos, String targetName, long createdAt) {}

    public static synchronized void load() {
        try {
            Files.createDirectories(CONFIG.getParent());
            if (!Files.exists(CONFIG)) return;
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(CONFIG)) {
                p.load(in);
            }
            String raw = p.getProperty("friends", "");
            FRIENDS.clear();
            for (String name : raw.split(",")) {
                if (!name.isBlank()) FRIENDS.add(name.trim());
            }
        } catch (IOException ignored) {
        }
    }

    private static synchronized void save() {
        try {
            Files.createDirectories(CONFIG.getParent());
            Properties p = new Properties();
            p.setProperty("friends", String.join(",", FRIENDS));
            try (OutputStream out = Files.newOutputStream(CONFIG)) {
                p.store(out, "FriendPing friends");
            }
        } catch (IOException ignored) {
        }
    }

    public static synchronized boolean addFriend(String name) {
        if (name == null || name.isBlank() || FRIENDS.stream().anyMatch(n -> n.equalsIgnoreCase(name))) return false;
        FRIENDS.add(name);
        save();
        return true;
    }

    public static synchronized boolean removeFriend(String name) {
        boolean changed = FRIENDS.removeIf(n -> n.equalsIgnoreCase(name));
        if (changed) save();
        return changed;
    }

    public static synchronized void clearFriends() {
        FRIENDS.clear();
        save();
    }

    public static synchronized String friendList() {
        return FRIENDS.isEmpty() ? "§7Список друзей пуст. Используй /fp add ник" :
                "§fДрузья: §b" + String.join("§7, §b", FRIENDS);
    }

    public static void addLocal(Vec3d pos, String targetName) {
        PINGS.add(new Ping(pos, targetName, System.currentTimeMillis()));
    }

    public static void expire() {
        long now = System.currentTimeMillis();
        PINGS.removeIf(p -> now - p.createdAt() >= PING_LIFETIME_MS);
    }

    public static List<Ping> pings() {
        expire();
        return PINGS;
    }

    public static void broadcast(MinecraftClient client, Vec3d pos, String targetName) {
        if (client.getNetworkHandler() == null) return;
        String payload = PREFIX + String.format(Locale.ROOT, "%.2f|%.2f|%.2f|%s",
                pos.x, pos.y, pos.z, targetName == null ? "" : targetName.replace("|", ""));
        List<String> copy;
        synchronized (FriendPingState.class) {
            copy = List.copyOf(FRIENDS);
        }
        for (String friend : copy) {
            // /m is issued as a normal server command. The server must permit this message format.
            client.getNetworkHandler().sendChatCommand("m " + friend + " " + payload);
        }
    }

    public static void tryReceive(String message) {
        int index = message.indexOf(PREFIX);
        if (index < 0) return;
        String payload = message.substring(index + PREFIX.length()).trim();
        String[] parts = payload.split("\\|", 4);
        if (parts.length < 3) return;
        try {
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);
            String target = parts.length >= 4 ? parts[3] : "";
            PINGS.add(new Ping(new Vec3d(x, y, z), target, System.currentTimeMillis()));
        } catch (NumberFormatException ignored) {
        }
    }
}
