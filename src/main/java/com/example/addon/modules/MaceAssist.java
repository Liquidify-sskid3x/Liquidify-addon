package com.example.addon.modules;

import com.example.addon.AddonTemplate;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;

import meteordevelopment.orbit.EventHandler;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.MaceItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

public class MaceAssist extends Module {

    /* ---------------- SETTINGS ---------------- */

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> range = sg.add(new DoubleSetting.Builder()
        .name("range")
        .defaultValue(4.5)
        .min(1)
        .sliderMax(6)
        .build());

    private final Setting<Double> heightAdvantage = sg.add(new DoubleSetting.Builder()
        .name("height-advantage")
        .defaultValue(1.5)
        .sliderMin(0.5)
        .sliderMax(5)
        .build());

    private final Setting<Integer> attackDelay = sg.add(new IntSetting.Builder()
        .name("attack-delay")
        .defaultValue(6)
        .sliderMax(20)
        .build());

    private final Setting<Boolean> swapOnHit = sg.add(new BoolSetting.Builder()
        .name("swap-on-hit")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> swapOnMiss = sg.add(new BoolSetting.Builder()
        .name("swap-on-miss")
        .defaultValue(true)
        .build());

    private final Setting<Integer> swapDelay = sg.add(new IntSetting.Builder()
        .name("swap-delay")
        .description("Human reaction delay before swapping.")
        .defaultValue(2)
        .sliderMax(10)
        .build());

    private final Setting<Boolean> fallbackSwap = sg.add(new BoolSetting.Builder()
        .name("fallback-visible-swap")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> spoofRotation = sg.add(new BoolSetting.Builder()
        .name("rotation-spoof")
        .defaultValue(true)
        .build());

    private final Setting<Double> rotationSpeed = sg.add(new DoubleSetting.Builder()
        .name("rotation-speed")
        .description("Degrees per tick to smoothly rotate toward target.")
        .defaultValue(8.0)
        .sliderMin(1.0)
        .sliderMax(30.0)
        .build());

    /* ---------------- STATE ---------------- */

    private final Random random = new Random();

    private int timer = 0;
    private int swapTimer = -1;
    private int maceSlot = -1;

    private float targetYaw = 0;
    private float targetPitch = 0;
    private boolean rotating = false;

    public MaceAssist() {
        super(AddonTemplate.CATEGORY, "Mace Assist",
            "Human-like mace helper with attribute swapping.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        swapTimer = -1;
        rotating = false;
    }

    /* ---------------- MAIN TICK ---------------- */

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        findMace();

        // Run smooth rotation every tick regardless of attack logic
        if (spoofRotation.get()) smoothRotate();

        /* Detect player attack input */
        if (mc.options.attackKey.isPressed()
            && mc.player.getAttackCooldownProgress(0) > 0.9f
            && swapTimer == -1) {

            boolean hit = isEntityInCrosshair();

            if ((hit && swapOnHit.get()) || (!hit && swapOnMiss.get())) {
                swapTimer = swapDelay.get() + random.nextInt(2);
            }
        }

        /* Execute delayed swap */
        if (swapTimer >= 0 && --swapTimer <= 0) {
            doSwap();
            swapTimer = -1;
        }

        /* Density attack assist */
        timer++;
        if (timer < attackDelay.get()) return;

        if (!(mc.player.getMainHandStack().getItem() instanceof MaceItem))
            return;

        LivingEntity target = findTarget();
        if (target == null) return;

        double heightDiff = mc.player.getY() - target.getY();
        if (heightDiff < heightAdvantage.get()) return;

        timer = 0;

        if (spoofRotation.get()) face(target);

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    /* ---------------- SWAPPING ---------------- */

    private void doSwap() {
        if (maceSlot == -1) return;

        int current = mc.player.getInventory().getSelectedSlot();
        if (current == maceSlot) return;

        InvUtils.swap(maceSlot, false);

        if (fallbackSwap.get()) {
            mc.player.getInventory().setSelectedSlot(maceSlot);
        }
    }

    private void findMace() {
        maceSlot = -1;

        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof MaceItem) {
                maceSlot = i;
                return;
            }
        }
    }

    /* ---------------- TARGETING ---------------- */

    private LivingEntity findTarget() {
        LivingEntity best = null;
        double closest = range.get();

        for (LivingEntity e : mc.world.getEntitiesByClass(
            LivingEntity.class,
            new Box(mc.player.getBlockPos()).expand(range.get()),
            x -> x != mc.player && x.isAlive())) {

            double d = mc.player.distanceTo(e);
            if (d < closest) {
                closest = d;
                best = e;
            }
        }

        return best;
    }

    private boolean isEntityInCrosshair() {
        if (mc.crosshairTarget == null) return false;

        if (mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            return ((EntityHitResult) mc.crosshairTarget).getEntity() instanceof LivingEntity;
        }

        return false;
    }

    /* ---------------- ROTATION ---------------- */

    private void face(LivingEntity t) {
        double dx = t.getX() - mc.player.getX();
        double dy = t.getEyeY() - mc.player.getEyeY();
        double dz = t.getZ() - mc.player.getZ();

        double dist = Math.sqrt(dx * dx + dz * dz);

        targetYaw   = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90F);
        targetPitch = MathHelper.clamp(
            (float) (-Math.toDegrees(Math.atan2(dy, dist))), -90, 90);

        rotating = true;
    }

    private void smoothRotate() {
        if (!rotating) return;

        float speed = rotationSpeed.get().floatValue();

        // Normalize yaw delta to [-180, 180] so it always takes the SHORT path
        float yawDelta = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
        float newYaw = mc.player.getYaw() + Math.signum(yawDelta) * Math.min(speed, Math.abs(yawDelta));

        float newPitch = MathHelper.stepTowards(mc.player.getPitch(), targetPitch, speed);

        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);

        if (Math.abs(yawDelta) < 0.5f && Math.abs(newPitch - targetPitch) < 0.5f) {
            mc.player.setYaw(targetYaw);
            mc.player.setPitch(targetPitch);
            rotating = false;
        }
    }
}
