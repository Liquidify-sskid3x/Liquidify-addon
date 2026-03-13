package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

import java.lang.reflect.Field;

public class ThemisNoFall extends Module {

    private Field onGroundField;

    public ThemisNoFall() {
        super(AddonTemplate.CATEGORY, "Themis Nofall", "no fall damage like what you expect brodie");
        try {
            onGroundField = PlayerMoveC2SPacket.class.getDeclaredField("onGround");
            onGroundField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            try {
                onGroundField = PlayerMoveC2SPacket.class.getDeclaredField("field_12885");
                onGroundField.setAccessible(true);
            } catch (NoSuchFieldException ignored) {}
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!(event.packet instanceof PlayerMoveC2SPacket packet)) return;
        if (onGroundField == null) return;
        try {
            onGroundField.set(packet, false);
        } catch (IllegalAccessException ignored) {}
    }
}
