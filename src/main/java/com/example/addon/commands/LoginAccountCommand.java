package com.example.addon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.kyori.adventure.text.Component;
import net.minecraft.command.CommandSource;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.PacketSendingEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;

import java.net.InetSocketAddress;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class LoginAccountCommand extends Command {

    public LoginAccountCommand() {
        super("loginaccount", "Connects as a username and ops you. Usage: .loginaccount <username>");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("username", StringArgumentType.word())
            .executes(ctx -> {
                runOp(StringArgumentType.getString(ctx, "username"));
                return SINGLE_SUCCESS;
            })
        );
    }

    private void runOp(String username) {
        if (mc.getCurrentServerEntry() == null || mc.getNetworkHandler() == null) {
            error("Not connected to a server.");
            return;
        }

        String myName = mc.player.getName().getString();
        String addr = mc.getCurrentServerEntry().address;
        String serverIp;
        int serverPort;

        if (addr.contains(":")) {
            String[] parts = addr.split(":");
            serverIp = parts[0];
            try { serverPort = Integer.parseInt(parts[1]); }
            catch (NumberFormatException e) { serverPort = 25565; }
        } else {
            serverIp = addr;
            serverPort = 25565;
        }

        info("Connecting as: %s", username);

        MinecraftProtocol protocol = new MinecraftProtocol(username);
        ClientSession session = ClientNetworkSessionFactory.factory()
            .setRemoteSocketAddress(new InetSocketAddress(serverIp, serverPort))
            .setProtocol(protocol)
            .create();

        session.addListener(new SessionAdapter() {
            private volatile boolean opSent = false;

            @Override
            public void connected(org.geysermc.mcprotocollib.network.event.session.ConnectedEvent event) {
                ChatUtils.info("Bot '%s' TCP connected.", username);
            }

            @Override
            public void packetReceived(Session s, Packet packet) {
                if (packet instanceof ClientboundLoginPacket && !opSent) {
                    opSent = true;
                    ChatUtils.info("Bot '%s' joined - sending /op %s", username, myName);
                    s.send(new ServerboundChatCommandPacket("op " + myName));
                    ChatUtils.info("Sent: /op %s", myName);
                    s.send(new ServerboundChatCommandPacket("tick rate 2000"));
                    ChatUtils.info("Sent: /tick rate 2000");
                    s.disconnect(Component.text("Done"));
                }

                if (packet instanceof ClientboundSystemChatPacket chat) {
                    String plain = componentToPlain(chat.getContent()).toLowerCase();
                    if (plain.contains("made") && plain.contains("server operator") && !plain.contains("no longer")) {
                        ChatUtils.info("Op successful! You are now op.");
                    }
                }
            }

            @Override
            public void disconnected(DisconnectedEvent event) {
                String reason = componentToPlain(event.getReason());
                if (!reason.equalsIgnoreCase("done") && !reason.isEmpty()) {
                    ChatUtils.warning("Bot disconnected: %s", reason);
                    if (event.getCause() != null) {
                        ChatUtils.warning("Cause: %s", event.getCause().getMessage());
                    }
                }
            }
        });

        session.connect();
    }

    private String componentToPlain(net.kyori.adventure.text.Component component) {
        StringBuilder sb = new StringBuilder();
        if (component instanceof net.kyori.adventure.text.TextComponent tc) sb.append(tc.content());
        for (net.kyori.adventure.text.Component child : component.children()) sb.append(componentToPlain(child));
        return sb.toString();
    }
}
