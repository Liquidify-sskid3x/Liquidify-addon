package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.entity.player.DoAttackEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.item.MaceItem;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.hit.HitResult;

public class SpearMace extends Module {

    private int backTimer;
    private boolean swapBack;

    public SpearMace() {
        super(AddonTemplate.CATEGORY,
            "Spear Mace",
            "Swaps spear on miss and mace on hit.");
    }

    // MISS → SPEAR
    @EventHandler
    private void onAttack(DoAttackEvent e) {
        if (mc.crosshairTarget == null) return;

        if (mc.crosshairTarget.getType() != HitResult.Type.ENTITY) {
            int spear = findSpear();
            if (spear != -1) doSwap(spear);
        }
    }

    // HIT → MACE
    @EventHandler
    private void onHit(AttackEntityEvent e) {
        int mace = findMace();
        if (mace != -1) doSwap(mace);
    }

    private void doSwap(int slot) {
        if (slot == mc.player.getInventory().getSelectedSlot()) return;

        if (InvUtils.swap(slot, true)) {
            swapBack = true;
            backTimer = 2; // HT1 feel
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post e) {
        if (!swapBack) return;

        if (--backTimer <= 0) {
            InvUtils.swapBack();
            swapBack = false;
        }
    }

    private int findMace() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof MaceItem)
                return i;
        }
        return -1;
    }

    private int findSpear() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isIn(ItemTags.SPEARS))
                return i;
        }
        return -1;
    }
}
