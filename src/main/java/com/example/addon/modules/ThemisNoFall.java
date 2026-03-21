package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class ThemisNoFall extends Module {

    private boolean sending = false;

    public ThemisNoFall() {
        super(AddonTemplate.CATEGORY, "themis-nofall", "what do you think bro");
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (sending) return;
        if (!(event.packet instanceof PlayerMoveC2SPacket packet)) return;
        if (!packet.isOnGround()) return;

        event.cancel();
        sending = true;
        mc.getNetworkHandler().sendPacket(remakePacket(packet));
        sending = false;
    }

    private PlayerMoveC2SPacket remakePacket(PlayerMoveC2SPacket original) {
        if (original instanceof PlayerMoveC2SPacket.Full p) {
            return new PlayerMoveC2SPacket.Full(p.getX(0), p.getY(0), p.getZ(0), p.getYaw(0), p.getPitch(0), false, false);
        } else if (original instanceof PlayerMoveC2SPacket.PositionAndOnGround p) {
            return new PlayerMoveC2SPacket.PositionAndOnGround(p.getX(0), p.getY(0), p.getZ(0), false, false);
        } else if (original instanceof PlayerMoveC2SPacket.OnGroundOnly p) {
            return new PlayerMoveC2SPacket.OnGroundOnly(false, false);
        } else if (original instanceof PlayerMoveC2SPacket.LookAndOnGround p) {
            return new PlayerMoveC2SPacket.LookAndOnGround(p.getYaw(0), p.getPitch(0), false, false);
        }
        return original;
    }
}
