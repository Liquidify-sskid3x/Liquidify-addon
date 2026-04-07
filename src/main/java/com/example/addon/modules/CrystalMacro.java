package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.mixin.ExampleMixin;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class CrystalMacro extends Module {

    private enum MacroState { CHECK, PLACE_OBSIDIAN, PLACE_CRYSTAL, BREAK }

    private MacroState state = MacroState.CHECK;

    public CrystalMacro() {
        super(AddonTemplate.CATEGORY, "crystal-macro", "place and break end crystals rapidly");
    }

    @Override
    public void onActivate() {
        state = MacroState.CHECK;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        switch (state) {
            case CHECK          -> tickCheck();
            case PLACE_OBSIDIAN -> tickPlaceObsidian();
            case PLACE_CRYSTAL  -> tickPlaceCrystal();
            case BREAK          -> tickBreak();
        }
    }

    private void tickCheck() {
        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = ((BlockHitResult) hit).getBlockPos();

        // Crystal already sitting on top — just break it
        EndCrystalEntity crystal = findCrystalAt(pos.up());
        if (crystal != null) {
            state = MacroState.BREAK;
            return;
        }

        // Valid base — place crystal directly
        if (isValidBase(pos)) {
            state = MacroState.PLACE_CRYSTAL;
            return;
        }

        // Need to place obsidian first
        state = MacroState.PLACE_OBSIDIAN;
    }

    private void tickPlaceObsidian() {
        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult bhr = (BlockHitResult) hit;
        BlockPos pos = bhr.getBlockPos();

        if (isValidBase(pos)) {
            state = MacroState.PLACE_CRYSTAL;
            return;
        }

        if (!swapToItem(Items.OBSIDIAN)) {
            info("No obsidian in hotbar.");
            toggle();
            return;
        }

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
        mc.player.swingHand(Hand.MAIN_HAND);
        state = MacroState.PLACE_CRYSTAL;
    }

    private void tickPlaceCrystal() {
        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult bhr = (BlockHitResult) hit;
        BlockPos pos = bhr.getBlockPos();

        if (!isValidBase(pos)) {
            state = MacroState.CHECK;
            return;
        }

        // Crystal already there — break it
        EndCrystalEntity crystal = findCrystalAt(pos.up());
        if (crystal != null) {
            state = MacroState.BREAK;
            return;
        }

        if (!mc.world.getBlockState(pos.up()).isAir()) {
            info("Placement blocked.");
            return;
        }

        if (!swapToItem(Items.END_CRYSTAL)) {
            info("No end crystal in hotbar.");
            toggle();
            return;
        }

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
        mc.player.swingHand(Hand.MAIN_HAND);
        state = MacroState.BREAK;
    }

    private void tickBreak() {
        EndCrystalEntity crystal = findNearestCrystal();
        if (crystal == null) {
            state = MacroState.CHECK;
            return;
        }

        mc.interactionManager.attackEntity(mc.player, crystal);
        mc.player.swingHand(Hand.MAIN_HAND);
        state = MacroState.CHECK;
    }

    private EndCrystalEntity findCrystalAt(BlockPos pos) {
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EndCrystalEntity c)) continue;
            if (c.getBlockPos().equals(pos) || c.getBlockPos().equals(pos.down())) return c;
        }
        return null;
    }

    private EndCrystalEntity findNearestCrystal() {
        EndCrystalEntity nearest = null;
        double closest = Double.MAX_VALUE;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EndCrystalEntity c)) continue;
            double d = mc.player.distanceTo(c);
            if (d < 6.0 && d < closest) { closest = d; nearest = c; }
        }
        return nearest;
    }

    private boolean isValidBase(BlockPos pos) {
        var block = mc.world.getBlockState(pos).getBlock();
        return block == Blocks.OBSIDIAN || block == Blocks.BEDROCK;
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
