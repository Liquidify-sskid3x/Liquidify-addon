package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

public class ThemisFly extends Module {

    private static final double BPS_TO_TICKS = 1.0 / 20.0;

    private final SettingGroup sgBlink = settings.createGroup("Blink");
    private final SettingGroup sgTimer = settings.createGroup("Timer");
    private final SettingGroup sgGravity = settings.createGroup("Gravity");

    private final Setting<Boolean> renderOriginal = sgBlink.add(new BoolSetting.Builder()
        .name("render-original")
        .description("Renders your player model at the original position.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> pulseDelay = sgBlink.add(new IntSetting.Builder()
        .name("pulse-delay")
        .description("After this many ticks, send all packets and start blinking again. 0 to disable.")
        .defaultValue(50)
        .min(0)
        .sliderMax(60)
        .build()
    );

    private final Setting<Double> timerMultiplier = sgTimer.add(new DoubleSetting.Builder()
        .name("multiplier")
        .description("Timer multiplier.")
        .defaultValue(2.0)
        .min(0.1)
        .sliderMin(0.1)
        .sliderMax(10.0)
        .build()
    );

    private final Setting<Double> gravity = sgGravity.add(new DoubleSetting.Builder()
        .name("gravity")
        .description("Vertical speed in blocks per second. 0 = float. Negative = rise. Positive = fall.")
        .defaultValue(0.0)
        .min(-100.0)
        .max(100.0)
        .sliderMin(-20.0)
        .sliderMax(20.0)
        .build()
    );

    private final Setting<Boolean> disableShifting = sgGravity.add(new BoolSetting.Builder()
        .name("disable-shifting")
        .description("Prevents sneaking from moving you downward while floating.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> sameY = sgGravity.add(new BoolSetting.Builder()
        .name("same-y")
        .description("Jump works normally but you always return to your original Y level.")
        .defaultValue(false)
        .build()
    );

    private final List<PlayerMoveC2SPacket> packets = new ArrayList<>();
    private FakePlayerEntity model;
    private final Vector3d start = new Vector3d();
    private boolean sending;
    private int blinkTimer = 0;

    private boolean wasOnGround = true;
    private double lockedY = 0.0;
    private boolean jumping = false;

    public ThemisFly() {
        super(AddonTemplate.CATEGORY, "themis-fly", "Blink + Timer + NoGravity combined.");
    }

    @Override
    public void onActivate() {
        if (!Utils.canUpdate()) return;

        if (renderOriginal.get()) {
            model = new FakePlayerEntity(mc.player, mc.player.getGameProfile().name(), 20, true);
            model.doNotPush = true;
            model.hideWhenInsideCamera = true;
            model.noHit = true;
            model.spawn();
        }

        Utils.set(start, mc.player.getEntityPos());

        wasOnGround = mc.player.isOnGround();
        lockedY = mc.player.getY();
        jumping = false;
        blinkTimer = 0;

        Timer timer = Modules.get().get(Timer.class);
        if (timer != null) timer.setOverride(timerMultiplier.get());
    }

    @Override
    public void onDeactivate() {
        if (!Utils.canUpdate()) return;

        dumpPackets(true);

        Timer timer = Modules.get().get(Timer.class);
        if (timer != null) timer.setOverride(Timer.OFF);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!Utils.canUpdate()) return;

        blinkTimer++;

        if (pulseDelay.get() != 0 && blinkTimer >= pulseDelay.get()) {
            dumpPackets(true);
            if (renderOriginal.get()) {
                model = new FakePlayerEntity(mc.player, mc.player.getGameProfile().name(), 20, true);
                model.doNotPush = true;
                model.hideWhenInsideCamera = true;
                model.noHit = true;
                model.spawn();
            }
            Utils.set(start, mc.player.getEntityPos());
        }
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null) return;

        double bps = gravity.get();
        double speedPerTick = bps * BPS_TO_TICKS;
        boolean onGround = mc.player.isOnGround();
        double currentY = mc.player.getVelocity().y;

        if (sameY.get()) {
            double playerY = mc.player.getY();
            if (mc.options.sneakKey.isPressed()) {
                mc.player.setVelocity(mc.player.getVelocity().x, -2.0 * BPS_TO_TICKS, mc.player.getVelocity().z);
                lockedY = playerY;
            } else if (playerY > lockedY + 0.01) {
                double diff = playerY - lockedY;
                mc.player.setVelocity(mc.player.getVelocity().x, -Math.min(diff * 0.3 + 0.05, 0.5), mc.player.getVelocity().z);
            } else if (playerY < lockedY - 0.01) {
                double diff = lockedY - playerY;
                mc.player.setVelocity(mc.player.getVelocity().x, Math.min(diff * 0.3 + 0.05, 0.5), mc.player.getVelocity().z);
            } else {
                mc.player.setVelocity(mc.player.getVelocity().x, 0.0, mc.player.getVelocity().z);
                mc.player.setOnGround(true);
            }
            mc.player.fallDistance = 0;
            wasOnGround = onGround;
            return;
        }

        if (bps == 0.0) {
            double newY = 0.0;
            if (mc.options.sneakKey.isPressed() && !disableShifting.get()) {
                newY = -2.0 * BPS_TO_TICKS;
                jumping = false;
            } else if (!jumping && mc.options.jumpKey.isPressed()) {
                jumping = true;
                newY = 0.42;
            } else if (jumping && currentY > 0) {
                newY = currentY - 0.08;
            } else {
                jumping = false;
                newY = 0.0;
            }
            mc.player.setVelocity(mc.player.getVelocity().x, newY, mc.player.getVelocity().z);
            mc.player.setOnGround(!jumping);
            mc.player.fallDistance = 0;

        } else if (bps < 0.0) {
            double risePerTick = Math.min(-speedPerTick, 5.0);
            if (mc.options.sneakKey.isPressed() && !disableShifting.get()) {
                risePerTick = -2.0 * BPS_TO_TICKS;
            }
            mc.player.setVelocity(mc.player.getVelocity().x, risePerTick, mc.player.getVelocity().z);
            mc.player.fallDistance = 0;

        } else {
            double fallPerTick = speedPerTick;
            if (bps <= 40.0) {
                mc.player.setVelocity(mc.player.getVelocity().x, -fallPerTick, mc.player.getVelocity().z);
            } else {
                if (wasOnGround && !onGround) {
                    mc.player.setVelocity(mc.player.getVelocity().x, Math.min(fallPerTick * 2.0, 20.0), mc.player.getVelocity().z);
                } else if (!onGround) {
                    mc.player.setVelocity(mc.player.getVelocity().x, Math.max(currentY - fallPerTick, -5.0), mc.player.getVelocity().z);
                }
            }
            if (currentY < 0) mc.player.fallDistance = 0;
        }

        wasOnGround = onGround;
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (!Utils.canUpdate()) return;
        if (sending) return;
        if (!(event.packet instanceof PlayerMoveC2SPacket p)) return;
        event.cancel();

        PlayerMoveC2SPacket prev = packets.isEmpty() ? null : packets.getLast();
        if (prev != null &&
            p.isOnGround() == prev.isOnGround() &&
            p.getYaw(-1) == prev.getYaw(-1) &&
            p.getPitch(-1) == prev.getPitch(-1) &&
            p.getX(-1) == prev.getX(-1) &&
            p.getY(-1) == prev.getY(-1) &&
            p.getZ(-1) == prev.getZ(-1)
        ) return;

        synchronized (packets) { packets.add(p); }
    }

    private void dumpPackets(boolean send) {
        sending = true;
        synchronized (packets) {
            if (send) packets.forEach(mc.player.networkHandler::sendPacket);
            packets.clear();
        }
        sending = false;

        if (model != null) {
            model.despawn();
            model = null;
        }

        blinkTimer = 0;
    }
}
