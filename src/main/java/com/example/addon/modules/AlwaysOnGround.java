package com.example.addon.modules;
import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
public class AlwaysOnGround extends Module {
    public AlwaysOnGround(){super(AddonTemplate.CATEGORY,"Always On Ground","Spoofs is.onground = true (suggestion by @gr33kyogurt_!");}
    @EventHandler private void onPacketSend(PacketEvent.Send event){
        if(!(event.packet instanceof PlayerMoveC2SPacket pkt))return;
        try{
            var f=PlayerMoveC2SPacket.class.getDeclaredField("onGround");
            f.setAccessible(true);
            f.set(pkt,true);
        }catch(Exception ignored){}}
}
