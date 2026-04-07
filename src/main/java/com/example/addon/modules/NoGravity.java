package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class NoGravity extends Module {

    private static final double BPS_TO_TICKS = 1.0 / 20.0;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> gravity = sgGeneral.add(new DoubleSetting.Builder()
        .name("gravity")
        .description("Vertical speed in blocks per second. 0 = float. Negative = rise. Positive = fall/slingshot.")
        .defaultValue(0.0)
        .min(-100.0)
        .max(100.0)
        .sliderMin(-20.0)
        .sliderMax(20.0)
        .build()
    );

    private final Setting<Boolean> disableShifting = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-shifting")
        .description("Prevents sneaking/shift from moving you downward while floating.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> sameY = sgGeneral.add(new BoolSetting.Builder()
        .name("same-y")
        .description("Jump works normally but you always return to your original Y level. Shift updates the locked Y.")
        .defaultValue(false)
        .build()
    );

    private boolean wasOnGround = true;
    private double lockedY = 0.0;
    private boolean jumping = false;

    public NoGravity() {
        super(AddonTemplate.CATEGORY, "no-gravity", "Configurable gravity in blocks/sec. 0 = float, negative = rise, positive = fall/slingshot.");
    }

    @Override
    public void onActivate() {
        wasOnGround = mc.player != null && mc.player.isOnGround();
        lockedY = mc.player != null ? mc.player.getY() : 0.0;
        jumping = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
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
}
