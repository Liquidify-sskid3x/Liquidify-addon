package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.mixin.ExampleMixin;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class AnchorMacro extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> searchRadius = sgGeneral.add(new DoubleSetting.Builder()
        .name("search-radius")
        .defaultValue(2.0)
        .min(0.5)
        .max(5.0)
        .sliderMin(0.5)
        .sliderMax(5.0)
        .build()
    );

    private final Setting<Integer> clicksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("clicks-per-tick")
        .description("Clicks sent per tick. Lower = more legit.")
        .defaultValue(2)
        .min(1)
        .max(5)
        .sliderMin(1)
        .sliderMax(5)
        .build()
    );

    private enum State { PLACE_ANCHOR, CLICK, DONE }

    private State    state;
    private BlockPos anchorPos;
    private int      clicksDone;

    private static final int TOTAL_CLICKS = 5;

    public AnchorMacro() {
        super(AddonTemplate.CATEGORY, "anchor-macro", "places and charges a respawn anchor then detonates it");
    }

    @Override
    public void onActivate() {
        state      = State.PLACE_ANCHOR;
        anchorPos  = null;
        clicksDone = 0;

        if (mc.player == null || mc.world == null) return;

        BlockPos nearby = findNearbyAnchor();
        if (nearby != null) {
            anchorPos  = nearby;
            clicksDone = 0;
            state      = State.CLICK;
            return;
        }

        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            BlockPos looked = ((BlockHitResult) mc.crosshairTarget).getBlockPos();
            if (mc.world.getBlockState(looked).getBlock() == Blocks.RESPAWN_ANCHOR) {
                anchorPos  = looked;
                clicksDone = 0;
                state      = State.CLICK;
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        switch (state) {
            case PLACE_ANCHOR -> tickPlaceAnchor();
            case CLICK        -> tickClick();
            case DONE         -> toggle();
        }
    }

    private void tickPlaceAnchor() {
        BlockPos nearby = findNearbyAnchor();
        if (nearby != null) {
            anchorPos  = nearby;
            clicksDone = 0;
            state      = State.CLICK;
            return;
        }

        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult bhr      = (BlockHitResult) hit;
        BlockPos       pos      = bhr.getBlockPos();
        BlockPos       placePos = pos.offset(bhr.getSide());

        if (mc.world.getBlockState(placePos).getBlock() == Blocks.RESPAWN_ANCHOR) {
            anchorPos  = placePos;
            clicksDone = 0;
            state      = State.CLICK;
            return;
        }


        if (!swapToItem(Items.RESPAWN_ANCHOR)) {
            info("No respawn anchor in hotbar.");
            toggle();
            return;
        }

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
            new BlockHitResult(bhr.getPos(), bhr.getSide(), pos, false));
        mc.player.swingHand(Hand.MAIN_HAND);

        anchorPos  = placePos;
        clicksDone = 0;
        state      = State.CLICK;
    }

    private void tickClick() {
        if (clicksDone >= TOTAL_CLICKS) {
            state = State.DONE;
            return;
        }

        Vec3d          center         = Vec3d.ofCenter(anchorPos);
        Direction side = mc.player.getHorizontalFacing().getOpposite();

        BlockHitResult bhr = new BlockHitResult(
            Vec3d.ofCenter(anchorPos),
            side,
            anchorPos,
            false
        );        int            toSendThisTick = Math.min(clicksPerTick.get(), TOTAL_CLICKS - clicksDone);

        for (int i = 0; i < toSendThisTick; i++) {
            if (clicksDone < 4) {
                if (!swapToItem(Items.GLOWSTONE)) {
                    info("No glowstone in hotbar.");
                    state = State.DONE;
                    return;
                }
            }
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
            mc.player.swingHand(Hand.MAIN_HAND);
            clicksDone++;
        }
    }

    private BlockPos findNearbyAnchor() {
        Vec3d  eye    = mc.player.getEyePos();
        Vec3d  look   = mc.player.getRotationVec(1f);
        double radius = searchRadius.get();
        Vec3d  center = eye.add(look.multiply(3.0));

        BlockPos centerBlock = BlockPos.ofFloored(center);
        int      r           = (int) Math.ceil(radius);

        BlockPos best     = null;
        double   bestDist = Double.MAX_VALUE;

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos check = centerBlock.add(dx, dy, dz);
                    if (mc.world.getBlockState(check).getBlock() != Blocks.RESPAWN_ANCHOR) continue;
                    double dist = Vec3d.ofCenter(check).squaredDistanceTo(center);
                    if (dist < radius * radius && dist < bestDist) {
                        bestDist = dist;
                        best     = check;
                    }
                }
            }
        }

        return best;
    }

    private boolean swapToItem(net.minecraft.item.Item item) {
        if (mc.player.getMainHandStack().getItem() == item) return true;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) {
                ExampleMixin inv = (ExampleMixin)(Object) mc.player.getInventory();
                inv.setSelectedSlot(i);
                return true;
            }
        }
        return false;
    }
}
