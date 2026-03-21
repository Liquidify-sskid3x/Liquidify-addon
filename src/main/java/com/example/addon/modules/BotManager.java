package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.*;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.game.ClientCommand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.io.*;
import java.nio.file.*;
import com.google.gson.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWDropCallback;

public class BotManager extends Module {

    public enum MovementMode { STILL, FOLLOW, ORBIT, ATTACK, ATTACK_FOLLOW }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBots    = settings.createGroup("Bots");
    private final SettingGroup sgMovement = settings.createGroup("Movement");
    private final SettingGroup sgMessages = settings.createGroup("Messages");
    private final SettingGroup sgProxies  = settings.createGroup("Proxies");

    // General
    private final Setting<Integer> joinDelay = sgGeneral.add(new IntSetting.Builder()
        .name("join-delay").description("Ms between each bot joining").defaultValue(1500).min(500).max(10000).build());

    private final Setting<Integer> commandDelay = sgGeneral.add(new IntSetting.Builder()
        .name("command-delay").description("Ms between commands after join").defaultValue(1000).min(200).max(5000).build());

    // Bots
    private final Setting<String> baseUsername = sgBots.add(new StringSetting.Builder()
        .name("username").description("Base username (e.g. 'steve')").defaultValue("steve").build());

    private final Setting<Integer> botCount = sgBots.add(new IntSetting.Builder()
        .name("count").description("Number of bots").defaultValue(1).min(1).max(100).build());

    private final Setting<String> commandList = sgBots.add(new StringSetting.Builder()
        .name("commands").description("Commands to run on join, separated by |  e.g. /register pass pass|/login pass").defaultValue("").build());

    // Messages
    private final Setting<String> messageList = sgMessages.add(new StringSetting.Builder()
        .name("messages").description("Messages to send, separated by |  e.g. hello|how are you").defaultValue("hello world!").build());

    private final Setting<Integer> messageRepeat = sgMessages.add(new IntSetting.Builder()
        .name("repeat").description("How many times to repeat the message list").defaultValue(1).min(1).max(100).build());

    // Movement
    private final Setting<String> movementMode = sgMovement.add(new StringSetting.Builder()
        .name("mode").description("STILL / FOLLOW / ORBIT / ATTACK / ATTACK_FOLLOW").defaultValue("STILL").build());

    private final Setting<String> targetPlayer = sgMovement.add(new StringSetting.Builder()
        .name("target").description("Player name to follow/orbit/attack").defaultValue("").build());

    private final Setting<Boolean> attackSwing = sgMovement.add(new BoolSetting.Builder()
        .name("attack-swing").description("Swing arm when attacking").defaultValue(true).build());

    // Proxies
    private final Setting<Boolean> useProxies = sgProxies.add(new BoolSetting.Builder()
        .name("use-proxies").description("Route bots through proxies").defaultValue(false).build());

    private final Setting<String> proxyFile = sgProxies.add(new StringSetting.Builder()
        .name("proxy-file").description("Full path to proxy file (ip:port per line)").defaultValue("proxies.txt").build());

    private final Setting<Integer> botsPerProxy = sgProxies.add(new IntSetting.Builder()
        .name("bots-per-proxy").description("How many bots share one proxy").defaultValue(4).min(1).max(20).build());

    // Runtime state
    public static class BotEntry {
        public String username, status;
        public ClientSession session;
        public int messagesSent, tickCounter, commandsSent, repeatsDone;
        public boolean joined;
        public double currentX, currentY, currentZ;
        public float orbitAngle;
        public final Random rand = new Random();
        public final Set<Integer> nearbyEntityIds = new HashSet<>();

        public BotEntry(String username) {
            this.username = username;
            this.status = "Idle";
        }
    }

    public final List<BotEntry> bots = new CopyOnWriteArrayList<>();
    private final List<String> validProxies = new CopyOnWriteArrayList<>();

    public BotManager() {
        super(AddonTemplate.CATEGORY, "bot-manager", "Manages multiple bots.");
    }

    private File getConfigFile() {
        File dir = new File(mc.runDirectory, "liquidify");
        dir.mkdirs();
        return new File(dir, "botmanager.json");
    }

    private void saveConfig() {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("username", baseUsername.get());
            obj.addProperty("count", botCount.get());
            obj.addProperty("joinDelay", joinDelay.get());
            obj.addProperty("commandDelay", commandDelay.get());
            obj.addProperty("commands", commandList.get());
            obj.addProperty("messages", messageList.get());
            obj.addProperty("messageRepeat", messageRepeat.get());
            obj.addProperty("movementMode", movementMode.get());
            obj.addProperty("target", targetPlayer.get());
            obj.addProperty("proxyFile", proxyFile.get());
            obj.addProperty("botsPerProxy", botsPerProxy.get());
            Files.writeString(getConfigFile().toPath(),
                new GsonBuilder().setPrettyPrinting().create().toJson(obj));
        } catch (Exception e) {
            ChatUtils.warning("BotManager: failed to save config: " + e.getMessage());
        }
    }

    private void loadConfig() {
        try {
            File f = getConfigFile();
            if (!f.exists()) return;
            JsonObject obj = JsonParser.parseString(Files.readString(f.toPath())).getAsJsonObject();
            if (obj.has("username"))     baseUsername.set(obj.get("username").getAsString());
            if (obj.has("count"))        botCount.set(obj.get("count").getAsInt());
            if (obj.has("joinDelay"))    joinDelay.set(obj.get("joinDelay").getAsInt());
            if (obj.has("commandDelay")) commandDelay.set(obj.get("commandDelay").getAsInt());
            if (obj.has("commands"))     commandList.set(obj.get("commands").getAsString());
            if (obj.has("messages"))     messageList.set(obj.get("messages").getAsString());
            if (obj.has("messageRepeat"))messageRepeat.set(obj.get("messageRepeat").getAsInt());
            if (obj.has("movementMode")) movementMode.set(obj.get("movementMode").getAsString());
            if (obj.has("target"))       targetPlayer.set(obj.get("target").getAsString());
            if (obj.has("proxyFile"))    proxyFile.set(obj.get("proxyFile").getAsString());
            if (obj.has("botsPerProxy")) botsPerProxy.set(obj.get("botsPerProxy").getAsInt());
        } catch (Exception e) {
            ChatUtils.warning("BotManager: failed to load config: " + e.getMessage());
        }
    }

    private void registerDropCallback() {
        long window = mc.getWindow().getHandle();
        GLFW.glfwSetDropCallback(window, (win, count, names) -> {
            for (int i = 0; i < count; i++) {
                String path = GLFWDropCallback.getName(names, i);
                if (path.endsWith(".txt") || path.endsWith(".md")) {
                    proxyFile.set(path);
                    if (useProxies.get()) loadProxies();
                    ChatUtils.info("BotManager: loaded proxy file: " + path);
                    saveConfig();
                    break;
                }
            }
        });
    }

    @Override
    public void onActivate() {
        loadConfig();
        registerDropCallback();
        spawnBots();
    }

    @Override
    public void onDeactivate() {
        saveConfig();
        disconnectAll();
        bots.clear();
    }

    private void loadProxies() {
        validProxies.clear();
        try {
            java.io.File f = new java.io.File(proxyFile.get());
            if (!f.exists()) { ChatUtils.warning("BotManager: proxy file not found: " + proxyFile.get()); return; }
            List<String> lines = java.nio.file.Files.readAllLines(f.toPath());
            int valid = 0;
            for (String line : lines) {
                line = line.trim();
                if (!line.contains(":")) continue;
                String[] p = line.split(":");
                try {
                    // Quick TCP test
                    try (java.net.Socket s = new java.net.Socket()) {
                        s.connect(new InetSocketAddress(p[0].trim(), Integer.parseInt(p[1].trim())), 3000);
                        validProxies.add(line);
                        valid++;
                    }
                } catch (Exception ignored) {}
            }
            ChatUtils.info("BotManager: " + valid + "/" + lines.size() + " proxies valid.");
        } catch (Exception e) {
            ChatUtils.warning("BotManager: failed to load proxies: " + e.getMessage());
        }
    }

    private String getProxyForBot(int idx) {
        if (!useProxies.get() || validProxies.isEmpty()) return null;
        return validProxies.get((idx / botsPerProxy.get()) % validProxies.size());
    }

    private InetSocketAddress getServerAddress() {
        if (mc.getCurrentServerEntry() == null) return new InetSocketAddress("localhost", 25565);
        String addr = mc.getCurrentServerEntry().address;
        if (addr.contains(":")) {
            String[] p = addr.split(":");
            try { return new InetSocketAddress(p[0], Integer.parseInt(p[1])); }
            catch (NumberFormatException ignored) {}
        }
        return new InetSocketAddress(addr, 25565);
    }

    private void spawnBots() {
        saveConfig();
        if (useProxies.get()) loadProxies();
        bots.clear();
        String base = baseUsername.get();
        int count = botCount.get();
        for (int i = 0; i < count; i++) {
            String name = i == 0 ? base : base + "_" + i;
            bots.add(new BotEntry(name));
        }
        new Thread(() -> {
            for (int i = 0; i < bots.size(); i++) {
                connectBot(bots.get(i), i);
                try { Thread.sleep(joinDelay.get()); } catch (InterruptedException ignored) {}
            }
        }, "bot-connect-seq").start();
    }

    public void connectBot(BotEntry bot, int idx) {
        bot.status = "Connecting...";
        bot.joined = false;
        bot.messagesSent = 0;
        bot.commandsSent = 0;
        bot.tickCounter = 0;
        bot.repeatsDone = 0;
        bot.nearbyEntityIds.clear();

        String proxy = getProxyForBot(idx);

        new Thread(() -> {
            try {
                MinecraftProtocol protocol = new MinecraftProtocol(bot.username);
                var factory = ClientNetworkSessionFactory.factory()
                    .setRemoteSocketAddress(getServerAddress())
                    .setProtocol(protocol);
                if (proxy != null) {
                    String[] pp = proxy.split(":");
                    factory = factory.setProxy(new org.geysermc.mcprotocollib.network.ProxyInfo(
                        org.geysermc.mcprotocollib.network.ProxyInfo.Type.SOCKS5,
                        new InetSocketAddress(pp[0], Integer.parseInt(pp[1])),
                        null, null));
                }
                bot.session = factory.create();

                bot.session.addListener(new SessionAdapter() {
                    @Override public void connected(ConnectedEvent e) {
                        bot.status = proxy != null ? "Connected via " + proxy : "Connected";
                    }

                    @Override public void packetReceived(Session s, Packet packet) {
                        if (packet instanceof ClientboundAddEntityPacket p) bot.nearbyEntityIds.add(p.getEntityId());
                        if (packet instanceof ClientboundRemoveEntitiesPacket p) {
                            for (int id : p.getEntityIds()) bot.nearbyEntityIds.remove(id);
                        }
                        if (packet instanceof ClientboundRespawnPacket) {
                            new Thread(() -> {
                                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                                if (bot.session != null && bot.session.isConnected()) {
                                    bot.session.send(new ServerboundClientCommandPacket(ClientCommand.RESPAWN));
                                    bot.status = "Respawning";
                                }
                            }, "bot-respawn-" + bot.username).start();
                        }
                        if (packet instanceof ClientboundLoginPacket) {
                            bot.joined = true;
                            bot.status = "Joined";
                            bot.tickCounter = 0;
                            if (mc.player != null) {
                                bot.currentX = mc.player.getX() + (bot.rand.nextDouble() - 0.5) * 4;
                                bot.currentY = mc.player.getY();
                                bot.currentZ = mc.player.getZ() + (bot.rand.nextDouble() - 0.5) * 4;
                            }
                            // Send commands
                            new Thread(() -> {
                                String[] cmds = commandList.get().split("\\|");
                                for (String cmd : cmds) {
                                    cmd = cmd.trim();
                                    if (cmd.isEmpty()) continue;
                                    try { Thread.sleep(commandDelay.get()); } catch (InterruptedException ignored) {}
                                    if (bot.session == null || !bot.session.isConnected()) break;
                                    String c = cmd.startsWith("/") ? cmd.substring(1) : cmd;
                                    bot.session.send(new ServerboundChatCommandPacket(c));
                                    bot.commandsSent++;
                                }
                                bot.status = "Active";
                            }, "bot-cmds-" + bot.username).start();
                        }
                    }

                    @Override public void disconnected(DisconnectedEvent e) {
                        String reason = e.getReason() != null ? e.getReason().toString() : "unknown";
                        bot.status = "Disconnected";
                        bot.joined = false;
                        bot.session = null;
                        ChatUtils.warning("Bot '%s' disconnected: %s", bot.username, reason);
                        if (e.getCause() != null) ChatUtils.warning("Cause: %s", e.getCause().getMessage());
                    }
                });
                bot.session.connect();
            } catch (Exception e) {
                bot.status = "Error: " + e.getMessage();
                bot.session = null;
            }
        }, "bot-" + bot.username).start();
    }

    public void disconnectAll() {
        for (BotEntry bot : bots) {
            if (bot.session != null) {
                bot.session.disconnect(Component.text("Disconnected"));
                bot.session = null;
                bot.joined = false;
                bot.status = "Disconnected";
            }
        }
    }

    private double[] getTargetPos() {
        String target = targetPlayer.get();
        if (mc.world == null || target.isEmpty()) return null;
        for (var entity : mc.world.getPlayers()) {
            if (entity.getName().getString().equalsIgnoreCase(target))
                return new double[]{entity.getX(), entity.getY(), entity.getZ()};
        }
        return null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        double[] targetPos = getTargetPos();
        String[] msgs = messageList.get().split("\\|");

        for (BotEntry bot : bots) {
            if (!bot.joined || bot.session == null || !bot.session.isConnected()) continue;
            bot.tickCounter++;

            // Messages - after commands done
            if (bot.tickCounter % 20 == 0) {
                String[] cmds = commandList.get().split("\\|");
                int totalCmds = (int) Arrays.stream(cmds).filter(c -> !c.trim().isEmpty()).count();
                if (bot.commandsSent >= totalCmds && bot.repeatsDone < messageRepeat.get()) {
                    if (bot.messagesSent < msgs.length) {
                        String msg = msgs[bot.messagesSent].trim();
                        if (!msg.isEmpty()) {
                            if (msg.startsWith("/")) {
                                bot.session.send(new ServerboundChatCommandPacket(msg.substring(1)));
                            } else {
                                bot.session.send(new ServerboundChatPacket(msg, System.currentTimeMillis(), 0L, null, 0, new BitSet(), 0));
                            }
                        }
                        bot.messagesSent++;
                    } else {
                        bot.messagesSent = 0;
                        bot.repeatsDone++;
                    }
                }
            }

            // Movement every 2 ticks
            if (bot.tickCounter % 2 == 0) {
                MovementMode mode;
                try { mode = MovementMode.valueOf(movementMode.get().toUpperCase()); }
                catch (Exception ex) { mode = MovementMode.STILL; }
                switch (mode) {
                    case STILL -> {
                        if (bot.tickCounter % 40 == 0) {
                            double kx = bot.currentX + (bot.rand.nextDouble() - 0.5) * 0.1;
                            double kz = bot.currentZ + (bot.rand.nextDouble() - 0.5) * 0.1;
                            bot.session.send(new ServerboundMovePlayerPosRotPacket(true, false, kx, bot.currentY, kz, 0f, 0f));
                        }
                    }
                    case FOLLOW -> {
                        if (targetPos != null) {
                            double dx = targetPos[0] - bot.currentX;
                            double dz = targetPos[2] - bot.currentZ;
                            double dist = Math.sqrt(dx*dx + dz*dz);
                            if (dist > 2.5) {
                                bot.currentX += (dx/dist) * 0.25;
                                bot.currentZ += (dz/dist) * 0.25;
                                bot.currentY = targetPos[1];
                                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                                bot.session.send(new ServerboundMovePlayerPosRotPacket(true, false, bot.currentX, bot.currentY, bot.currentZ, yaw, 0f));
                            }
                        }
                    }
                    case ORBIT -> {
                        if (targetPos != null) {
                            bot.orbitAngle += 0.08f;
                            double ox = targetPos[0] + Math.cos(bot.orbitAngle) * 3.0;
                            double oz = targetPos[2] + Math.sin(bot.orbitAngle) * 3.0;
                            bot.currentX = ox; bot.currentY = targetPos[1]; bot.currentZ = oz;
                            float yaw = (float) Math.toDegrees(Math.atan2(-(targetPos[0]-ox), targetPos[2]-oz));
                            bot.session.send(new ServerboundMovePlayerPosRotPacket(true, false, ox, bot.currentY, oz, yaw, 0f));
                        }
                    }
                    case ATTACK -> {
                        if (targetPos != null && bot.tickCounter % 10 == 0 && attackSwing.get()) {
                            bot.session.send(new ServerboundSwingPacket(Hand.MAIN_HAND));
                            float yaw = (float) Math.toDegrees(Math.atan2(-(targetPos[0]-bot.currentX), targetPos[2]-bot.currentZ));
                            bot.session.send(new ServerboundMovePlayerPosRotPacket(true, false, bot.currentX, bot.currentY, bot.currentZ, yaw, 0f));
                        }
                    }
                    case ATTACK_FOLLOW -> {
                        if (targetPos != null) {
                            double dx = targetPos[0] - bot.currentX;
                            double dz = targetPos[2] - bot.currentZ;
                            double dist = Math.sqrt(dx*dx + dz*dz);
                            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                            if (dist > 1.5) {
                                bot.currentX += (dx/dist) * 0.25;
                                bot.currentZ += (dz/dist) * 0.25;
                                bot.currentY = targetPos[1];
                            }
                            bot.session.send(new ServerboundMovePlayerPosRotPacket(true, false, bot.currentX, bot.currentY, bot.currentZ, yaw, 0f));
                            if (dist < 3.5 && bot.tickCounter % 10 == 0 && attackSwing.get())
                                bot.session.send(new ServerboundSwingPacket(Hand.MAIN_HAND));
                        }
                    }
                }
            }
        }
    }
}
