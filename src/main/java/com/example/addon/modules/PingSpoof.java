package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;

import java.util.ArrayDeque;
import java.util.Deque;

public class PingSpoof extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> pingMin = sgGeneral.add(new IntSetting.Builder()
        .name("ping-min")
        .description("Minimum spoofed ping in milliseconds.")
        .defaultValue(100)
        .min(0)
        .max(10000)
        .sliderMin(0)
        .sliderMax(10000)
        .build()
    );

    private final Setting<Integer> pingMax = sgGeneral.add(new IntSetting.Builder()
        .name("ping-max")
        .description("Maximum spoofed ping in milliseconds.")
        .defaultValue(200)
        .min(0)
        .max(10000)
        .sliderMin(0)
        .sliderMax(10000)
        .build()
    );

    private final Deque<DelayedPacket> queue = new ArrayDeque<>();
    private final java.util.Random random = new java.util.Random();

    public PingSpoof() {
        super(AddonTemplate.CATEGORY, "ping-spoof", "Spoofs your ping by delaying keep alive packets.");
    }

    @Override
    public void onDeactivate() {
        synchronized (queue) {
            for (DelayedPacket p : queue) {
                mc.getNetworkHandler().sendPacket(new KeepAliveC2SPacket(p.id));
            }
            queue.clear();
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof KeepAliveS2CPacket packet)) return;
        event.cancel();

        long id = packet.getId();
        int min = pingMin.get();
        int max = pingMax.get();
        if (min > max) { int tmp = min; min = max; max = tmp; }
        long pingDelay = min == max ? min : min + random.nextInt(max - min);
        long sendAt = System.currentTimeMillis() + pingDelay;

        synchronized (queue) {
            queue.add(new DelayedPacket(id, sendAt));
        }

        new Thread(() -> {
            long delay = sendAt - System.currentTimeMillis();
            if (delay > 0) {
                try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
            }
            if (!isActive()) return;
            synchronized (queue) {
                queue.removeIf(p -> {
                    if (p.id == id) {
                        if (mc.getNetworkHandler() != null) {
                            mc.getNetworkHandler().sendPacket(new KeepAliveC2SPacket(p.id));
                        }
                        return true;
                    }
                    return false;
                });
            }
        }, "pingspoof-" + id).start();
    }

    private record DelayedPacket(long id, long sendAt) {}
}
