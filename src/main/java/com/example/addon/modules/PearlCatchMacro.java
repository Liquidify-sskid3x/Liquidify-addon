package com.example.addon.modules;

import com.example.addon.AddonTemplate;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

public class PearlCatchMacro extends Module {

    /* ---------------- SETTINGS ---------------- */

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> rotationSpeed = sg.add(new DoubleSetting.Builder()
        .name("rotation-speed")
        .description("Degrees rotated per tick.")
        .defaultValue(25)
        .min(1)
        .sliderMax(90)
        .build());

    /* ---------------- STATE ---------------- */

    private int pearlSlot = -1;
    private int windSlot = -1;

    private boolean rotating = false;
    private float targetPitch;

    private enum Stage {
        WIND_DOWN,
        THROW_PEARL,
        WIND_UP,
        DONE
    }

    private Stage stage;

    public PearlCatchMacro() {
        super(AddonTemplate.CATEGORY,
            "pearl-catch",
            "Smooth rotation pearl catch macro.");
    }

    /* ---------------- ACTIVATE ---------------- */

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) return;

        pearlSlot = -1;
        windSlot = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);

            if (stack.getItem() instanceof EnderPearlItem)
                pearlSlot = i;

            if (stack.getItem().getTranslationKey()
                .toLowerCase().contains("wind_charge"))
                windSlot = i;
        }

        if (pearlSlot == -1 || windSlot == -1) {
            error("Need pearl + wind charge in hotbar.");
            toggle();
            return;
        }

        mc.player.jump();

        stage = Stage.WIND_DOWN;
        startRotation(90f);
    }

    /* ---------------- TICK ---------------- */

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (stage == Stage.DONE) return;

        if (rotating) {
            smoothRotate();

            if (!rotating) {
                executeStage();
            }
        }
    }

    /* ---------------- ROTATION ---------------- */

    private void startRotation(float pitch) {
        targetPitch = pitch;
        rotating = true;
    }

    private void smoothRotate() {
        float current = mc.player.getPitch();
        float speed = rotationSpeed.get().floatValue();

        float newPitch = MathHelper.stepTowards(current, targetPitch, speed);
        mc.player.setPitch(newPitch);

        if (Math.abs(newPitch - targetPitch) < 0.5f) {
            mc.player.setPitch(targetPitch);
            rotating = false;
        }
    }

    /* ---------------- STAGES ---------------- */

    private void executeStage() {
        switch (stage) {

            case WIND_DOWN -> {
                useItem(windSlot);
                stage = Stage.THROW_PEARL;
                startRotation(-90f);
            }

            case THROW_PEARL -> {
                useItem(pearlSlot);
                stage = Stage.WIND_UP;
                startRotation(-90f);
            }

            case WIND_UP -> {
                ItemStack windStack =
                    mc.player.getInventory().getStack(windSlot);

                if (!mc.player.getItemCooldownManager()
                    .isCoolingDown(windStack)) {

                    useItem(windSlot);
                    stage = Stage.DONE;
                    toggle();
                } else {
                    rotating = true; // keep checking
                }
            }
        }
    }

    /* ---------------- USE ITEM ---------------- */

    private void useItem(int slot) {
        InvUtils.swap(slot, false);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
    }
}
