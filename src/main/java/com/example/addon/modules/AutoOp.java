package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.kyori.adventure.text.Component;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.world.GameMode;
import org.geysermc.mcprotocollib.auth.SessionService;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class AutoOp extends Module {

    private enum State {
        IDLE, CONNECTING, PROBING, WAITING_PROBE, RUNNING_CLEANUP, RECONNECTING, DONE
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgReconnect = settings.createGroup("Reconnect");

    private final Setting<String> cleanupCommand = sgGeneral.add(new StringSetting.Builder()
        .name("cleanup-command")
        .description("Command after op. {user} = your name, {target} = bot name.")
        .defaultValue("deop {target}")
        .build()
    );
    private final Setting<Boolean> autoReconnect = sgReconnect.add(new BoolSetting.Builder()
        .name("auto-reconnect")
        .description("Reconnect if kicked or disconnected unexpectedly.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> reconnectDelay = sgReconnect.add(new IntSetting.Builder()
        .name("reconnect-delay")
        .description("Seconds to wait before reconnecting.")
        .defaultValue(4)
        .min(1)
        .sliderMax(30)
        .visible(autoReconnect::get)
        .build()
    );

    private volatile State state = State.IDLE;
    private int ticker = 0;

    private ClientSession session;
    private volatile boolean botJoined = false;
    private volatile boolean probeSucceeded = false;
    private volatile boolean unexpectedDisconnect = false;
    private volatile String disconnectReason = "";

    private List<String> queue = new ArrayList<>();
    private String currentBot = "";
    private String myName = "";
    private String serverIp = "";
    private int serverPort = 25565;

    public AutoOp() {
        super(AddonTemplate.CATEGORY, "auto-op", "Probes players with /deop to find a privileged account, then ops you.");
    }

    @Override
    public void onActivate() {
        if (mc.getCurrentServerEntry() == null || mc.getNetworkHandler() == null) {
            info("Not connected to a server.");
            toggle();
            return;
        }

        myName = mc.player.getGameProfile().name();
        String addr = mc.getCurrentServerEntry().address;
        if (addr.contains(":")) {
            String[] parts = addr.split(":");
            serverIp = parts[0];
            try { serverPort = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
        } else {
            serverIp = addr;
            serverPort = 25565;
        }

        queue = new ArrayList<>();
        buildQueue();

        if (queue.isEmpty()) {
            info("No players to try.");
            toggle();
            return;
        }

        state = State.IDLE;
        ticker = 0;
        connectNext();
    }

    @Override
    public void onDeactivate() {
        state = State.IDLE;
        disconnectSession("Disconnected");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // Handle unexpected disconnect flagged by packet thread
        if (unexpectedDisconnect) {
            unexpectedDisconnect = false;
            if (autoReconnect.get() && state != State.DONE && state != State.IDLE) {
                info("Disconnected (" + disconnectReason + ") — reconnecting in " + reconnectDelay.get() + "s...");
                state = State.RECONNECTING;
                ticker = 0;
                return;
            }
        }

        ticker++;

        switch (state) {
            case CONNECTING -> {
                if (botJoined) {
                    state = State.PROBING;
                    ticker = 0;
                }
            }
            case PROBING -> {
                if (ticker >= 40) {
                    info("Probing with: /op " + myName);
                    sendCommand("op " + myName);
                    state = State.WAITING_PROBE;
                    ticker = 0;
                }
            }
            case WAITING_PROBE -> {
                if (probeSucceeded) {
                    info("'" + currentBot + "' has permission — op confirmed!");
                    state = State.RUNNING_CLEANUP;
                    ticker = 0;
                } else if (ticker >= 60) {
                    info("'" + currentBot + "' has no permission — trying next.");
                    disconnectSession("No permission");
                    tryNext();
                }
            }
            case RUNNING_CLEANUP -> {
                if (ticker >= 40) {
                    info("Op sequence complete!");
                    disconnectSession("Done");
                    state = State.DONE;
                    ticker = 0;
                }
            }
            case RECONNECTING -> {
                if (ticker >= reconnectDelay.get() * 20) {
                    reconnect();
                }
            }
            case DONE -> {
                // Disable on next tick cleanly
                toggle();
                state = State.IDLE;
            }
            default -> {}
        }
    }

    private void buildQueue() {
        List<PlayerListEntry> entries = mc.getNetworkHandler().getPlayerList().stream()
            .filter(e -> !e.getProfile().name().equals(myName))
            .toList();

        if (entries.isEmpty()) return;

        info("Players online:");
        for (PlayerListEntry e : entries) {
            GameMode gm = e.getGameMode();
            String label = gm == GameMode.CREATIVE ? "§aCreative" : gm == GameMode.SPECTATOR ? "§bSpectator" : "§7" + gm.name();
            info("  " + e.getProfile().name() + " - " + label);
        }

        boolean anyPrivileged = entries.stream()
            .anyMatch(e -> e.getGameMode() == GameMode.CREATIVE || e.getGameMode() == GameMode.SPECTATOR);

        if (anyPrivileged) {
            entries.stream()
                .filter(e -> e.getGameMode() == GameMode.CREATIVE || e.getGameMode() == GameMode.SPECTATOR)
                .map(e -> e.getProfile().name())
                .forEach(queue::add);
            info("Privileged players queued: " + queue);
        } else {
            entries.stream().map(e -> e.getProfile().name()).forEach(queue::add);
            info("No privileged players — queuing all.");
        }
    }

    private void connectNext() {
        if (queue.isEmpty()) {
            info("All players tried — none had permission.");
            state = State.DONE;
            return;
        }
        currentBot = queue.remove(0);
        botJoined = false;
        probeSucceeded = false;
        unexpectedDisconnect = false;
        ticker = 0;
        info("Connecting as: " + currentBot);
        spawnSession();
    }

    private void reconnect() {
        botJoined = false;
        probeSucceeded = false;
        unexpectedDisconnect = false;
        ticker = 0;
        info("Reconnecting as: " + currentBot);
        spawnSession();
    }

    private void spawnSession() {
        disconnectSession("Switching");

        session = ClientNetworkSessionFactory.factory()
            .setRemoteSocketAddress(new InetSocketAddress(serverIp, serverPort))
            .setProtocol(new MinecraftProtocol(currentBot))
            .create();
        session.setFlag(MinecraftConstants.SESSION_SERVICE_KEY, new SessionService());
        session.addListener(new SessionAdapter() {
            @Override
            public void packetReceived(Session s, Packet packet) {
                if (packet instanceof ClientboundLoginPacket) {
                    botJoined = true;
                    info("Bot '" + currentBot + "' joined.");
                } else if (packet instanceof ClientboundSystemChatPacket chat) {
                    String plain = componentToPlain(chat.getContent()).toLowerCase();
                    if (plain.contains("made") && plain.contains("server operator") && !plain.contains("no longer")) {
                        probeSucceeded = true;
                    }
                }
            }

            @Override
            public void disconnected(DisconnectedEvent event) {
                botJoined = false;
                String reason = componentToPlain(event.getReason()).toLowerCase();
                info("Bot disconnected: [" + reason + "]");
                if (!reason.equals("done") && !reason.equals("switching")
                    && !reason.equals("no permission")) {
                    disconnectReason = reason;
                    unexpectedDisconnect = true;
                }
            }
        });
        session.connect();
        state = State.CONNECTING;
    }

    private void tryNext() {
        probeSucceeded = false;
        botJoined = false;
        ticker = 0;
        connectNext();
    }

    private void disconnectSession(String reason) {
        if (session != null) {
            try { if (session.isConnected()) session.disconnect(Component.text(reason)); } catch (Exception ignored) {}
            session = null;
        }
    }

    private void sendCommand(String cmd) {
        if (session == null || !session.isConnected()) return;
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        session.send(new ServerboundChatCommandPacket(cmd));
        info("Sent: /" + cmd);
    }

    private String componentToPlain(net.kyori.adventure.text.Component component) {
        StringBuilder sb = new StringBuilder();
        if (component instanceof net.kyori.adventure.text.TextComponent tc) sb.append(tc.content());
        for (net.kyori.adventure.text.Component child : component.children()) sb.append(componentToPlain(child));
        return sb.toString();
    }
}
