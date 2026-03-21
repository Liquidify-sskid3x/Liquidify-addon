package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.mixin.ExampleMixin;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.item.BlockItem;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Andromenda extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> autoJump = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-jump")
        .description("Automatically jump while moving forward.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> requireSupport = sgGeneral.add(new BoolSetting.Builder()
        .name("require-support")
        .description("Only move if floor and ceiling exist. Turn OFF to build the tunnel.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoSetup = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-setup")
        .description("Automatically builds floor and ceiling support before starting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> blocksForward = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-forward")
        .description("How many blocks ahead to predict and place (1-3).")
        .defaultValue(2)
        .min(1)
        .max(3)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("Max blocks to place per tick (1-2).")
        .defaultValue(2)
        .min(1)
        .max(2)
        .build()
    );

    private boolean setupDone = false;
    private int setupStep = 0;
    private BlockPos towerBase = null;
    private Direction forwardDir = null;

    public Andromenda() {
        super(AddonTemplate.CATEGORY, "andromenda", "rushed shi");
    }

    @Override
    public void onActivate() {
        setupDone = false;
        setupStep = 0;
        towerBase = null;
        forwardDir = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        BlockPos playerPos = mc.player.getBlockPos();
        boolean hasFloor = !mc.world.getBlockState(playerPos.down()).isAir();
        boolean hasCeiling = !mc.world.getBlockState(playerPos.up(2)).isAir();

        if (autoSetup.get() && !setupDone && (!hasFloor || !hasCeiling)) {
            lockToCardinal();
            if (setupStep == 0) {
                forwardDir = getForwardDir(mc.player.getYaw());
                towerBase = playerPos.down();
                setupStep = 1;
            }
            doSetupStep();
            return;
        }

        if (!setupDone) setupDone = true;

        // === NORMAL ANDROMENDA MODE ===
        lockToCardinal();
        mc.player.setSprinting(true);

        if (autoJump.get() && mc.player.isOnGround()) {
            mc.player.jump();
        }

        if (requireSupport.get() && (!hasFloor || !hasCeiling)) return;

        Direction dir = getForwardDir(mc.player.getYaw());

        int placedThisTick = 0;
        for (int dist = 0; dist <= blocksForward.get() && placedThisTick < blocksPerTick.get(); dist++) {
            BlockPos base = (dist == 0) ? playerPos : playerPos.offset(dir, dist);

            BlockPos floorPos = base.down();
            if (mc.world.getBlockState(floorPos).isAir() && placedThisTick < blocksPerTick.get()) {
                placeSilent(floorPos);
                placedThisTick++;
            }

            BlockPos ceilingPos = base.up(2);
            if (mc.world.getBlockState(ceilingPos).isAir() && placedThisTick < blocksPerTick.get()) {
                placeSilent(ceilingPos);
                placedThisTick++;
            }
        }
    }

    private void lockToCardinal() {
        float yaw = mc.player.getYaw();
        double normalized = ((yaw % 360) + 360) % 360;
        float cardinalYaw = (float) (Math.round(normalized / 90.0) * 90.0);
        if (cardinalYaw == 360f) cardinalYaw = 0f;

        mc.player.setYaw(cardinalYaw);
        mc.player.setPitch(0f);
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
            cardinalYaw, 0f, mc.player.isOnGround(), mc.player.horizontalCollision
        ));
    }

    private Direction getForwardDir(float yaw) {
        float cardinal = (float) (Math.round(((yaw % 360) + 360) % 360 / 90.0) * 90.0);
        if (cardinal == 360f) cardinal = 0f;
        return switch ((int) cardinal) {
            case 0   -> Direction.SOUTH;
            case 90  -> Direction.WEST;
            case 180 -> Direction.NORTH;
            case 270 -> Direction.EAST;
            default  -> Direction.SOUTH;
        };
    }

    private void doSetupStep() {
        switch (setupStep) {
            case 1: case 2: case 3:
                mc.player.setVelocity(mc.player.getVelocity().x, 0.42, mc.player.getVelocity().z);
                placeSilent(towerBase.up(setupStep - 1));
                setupStep++;
                break;

            case 4:
                BlockPos side = mc.player.getBlockPos().offset(forwardDir);
                placeSilent(side);
                setupStep++;
                break;

            case 5:
                BlockPos sidePos = mc.player.getBlockPos().offset(forwardDir);
                double newY = mc.player.getY();
                mc.player.setPosition(sidePos.getX() + 0.5, newY, sidePos.getZ() + 0.5);
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    sidePos.getX() + 0.5, newY, sidePos.getZ() + 0.5,
                    mc.player.isOnGround(), mc.player.horizontalCollision
                ));
                setupStep++;
                break;

            case 6: case 7: case 8:
                int extra = setupStep - 5;
                placeSilent(towerBase.up(2 + extra));
                setupStep++;
                break;

            case 9:
                placeSilent(mc.player.getBlockPos().up(2));
                setupStep++;
                break;

            case 10:
                setupDone = true;
                break;
        }
    }

    private int findAnyBlockOnHotbar() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof BlockItem) {
                return i;
            }
        }
        return -1;
    }

    private void placeSilent(BlockPos target) {
        if (target == null || !mc.world.getBlockState(target).isAir()) return;

        int slot = findAnyBlockOnHotbar();
        if (slot == -1) return;

        ExampleMixin inventory = (ExampleMixin) (Object) mc.player.getInventory();
        int prevSlot = inventory.getSelectedSlot();
        if (slot != prevSlot) inventory.setSelectedSlot(slot);

        Direction[] prioritized = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};

        BlockHitResult hitResult = null;
        Vec3d lookAt = null;

        for (Direction dir : prioritized) {
            BlockPos neighbor = target.offset(dir);
            if (!mc.world.getBlockState(neighbor).isAir() && mc.world.getBlockState(neighbor).isSolidBlock(mc.world, neighbor)) {
                Vec3d hitVec = new Vec3d(
                    target.getX() + 0.5 + dir.getOffsetX() * 0.5,
                    target.getY() + 0.5 + dir.getOffsetY() * 0.5,
                    target.getZ() + 0.5 + dir.getOffsetZ() * 0.5
                );
                lookAt = hitVec;
                hitResult = new BlockHitResult(hitVec, dir.getOpposite(), neighbor, false);
                break;
            }
        }

        if (hitResult == null) {
            if (slot != prevSlot) inventory.setSelectedSlot(prevSlot);
            return;
        }

        final BlockHitResult finalHit = hitResult;
        final Vec3d finalLookAt = lookAt;

        double rotYaw = Rotations.getYaw(finalLookAt);
        double rotPitch = Rotations.getPitch(finalLookAt);

        Rotations.rotate(rotYaw, rotPitch, 50, () -> {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, finalHit);
            mc.player.swingHand(Hand.MAIN_HAND);
        });

        if (slot != prevSlot) inventory.setSelectedSlot(prevSlot);
    }
}
