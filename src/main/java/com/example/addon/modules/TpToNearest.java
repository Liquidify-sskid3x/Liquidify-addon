package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.mixin.ExampleMixin;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

public class TpToNearest extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("How to teleport to the target.")
        .defaultValue(Mode.Pearl)
        .build()
    );
    private final Setting<Boolean> targetPlayers = sgGeneral.add(new BoolSetting.Builder()
        .name("target-players")
        .description("Teleport to players.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> targetMobs = sgGeneral.add(new BoolSetting.Builder()
        .name("target-mobs")
        .description("Teleport to mobs.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> autoDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-disable")
        .description("Disable after teleporting.")
        .defaultValue(true)
        .build()
    );

    public TpToNearest() {
        super(AddonTemplate.CATEGORY, "tp-to-nearest", "Teleports to the nearest player using a pearl or packet.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) return;
        LivingEntity target = findTarget();
        if (target == null) {
            info("No target found.");
            toggle();
            return;
        }
        if (mode.get() == Mode.Packet) packetTp(target);
        else pearlTp(target);
        if (autoDisable.get()) toggle();
    }

    private void packetTp(LivingEntity target) {
        double x = target.getX();
        double y = target.getY();
        double z = target.getZ();
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, true, mc.player.horizontalCollision));
        mc.player.setPosition(x, y, z);
    }

    private void pearlTp(LivingEntity target) {
        int pearlSlot = findPearlSlot();
        if (pearlSlot == -1) { info("No ender pearl found in hotbar."); return; }
        ItemStack pearlStack = mc.player.getInventory().getStack(pearlSlot);
        if (mc.player.getItemCooldownManager().isCoolingDown(pearlStack)) { info("Ender pearl is on cooldown."); return; }
        Vec3d eyePos = mc.player.getEyePos();
        double dx = target.getX() - eyePos.x;
        double dy = target.getY() - eyePos.y;
        double dz = target.getZ() - eyePos.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        float bestPitch = calculateBallisticPitch(horizontalDist, dy);
        float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        ExampleMixin inv = (ExampleMixin)(Object) mc.player.getInventory();
        int originalSlot = inv.getSelectedSlot();
        inv.setSelectedSlot(pearlSlot);
        mc.player.setYaw(yaw);
        mc.player.setPitch(bestPitch);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
        inv.setSelectedSlot(originalSlot);
    }

    private float calculateBallisticPitch(double horizontalDist, double dy) {
        final double speed = 1.5;
        final double drag = 0.99;
        final double gravity = 0.03;
        final int maxTicks = 1000;
        float bestPitch = 0;
        double bestError = Double.MAX_VALUE;
        for (int deg = -90; deg <= 90; deg++) {
            double pitchRad = Math.toRadians(deg);
            double velH = Math.cos(pitchRad) * speed;
            double velV = -Math.sin(pitchRad) * speed;
            double simH = 0;
            double simY = 0;
            for (int t = 0; t < maxTicks; t++) {
                simH += velH;
                simY += velV;
                velH *= drag;
                velV *= drag;
                velV -= gravity;
                if (simH >= horizontalDist) {
                    double error = Math.abs(simY - dy);
                    if (error < bestError) { bestError = error; bestPitch = deg; }
                    break;
                }
            }
        }
        return bestPitch;
    }

    private LivingEntity findTarget() {
        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;
        for (LivingEntity entity : mc.world.getEntitiesByClass(LivingEntity.class, mc.player.getBoundingBox().expand(4096), e -> true)) {
            if (entity == mc.player) continue;
            if (entity.isDead() || !entity.isAlive()) continue;
            if (entity instanceof PlayerEntity && !targetPlayers.get()) continue;
            if (entity instanceof ArmorStandEntity) continue;
            if (entity instanceof MobEntity && !targetMobs.get()) continue;
            if (!(entity instanceof PlayerEntity) && !(entity instanceof ArmorStandEntity) && !(entity instanceof MobEntity)) continue;
            double dist = mc.player.distanceTo(entity);
            if (dist < closestDist) { closestDist = dist; closest = entity; }
        }
        return closest;
    }

    private int findPearlSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.ENDER_PEARL)) return i;
        }
        return -1;
    }

    public enum Mode { Pearl, Packet }
}
