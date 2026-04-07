package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.item.Item;

public class Animations extends Module {

    public static Animations INSTANCE;

    private final SettingGroup sg = settings.getDefaultGroup();

    public final Setting<Boolean> oldBlock = sg.add(
        new BoolSetting.Builder()
            .name("1.7-blocking")
            .description("Enable old 1.7 sword/block animation style.")
            .defaultValue(true)
            .build()
    );

    public final Setting<Double> handHeight = sg.add(
        new DoubleSetting.Builder()
            .name("hand-height")
            .description("Vertical offset for hand position.")
            .defaultValue(0)
            .sliderRange(-1, 1)
            .build()
    );

    public final Setting<Double> handDistance = sg.add(
        new DoubleSetting.Builder()
            .name("hand-distance")
            .description("Forward/backward offset for hand position.")
            .defaultValue(0)
            .sliderRange(-1, 1)
            .build()
    );

    public final Setting<Double> swingSpeed = sg.add(
        new DoubleSetting.Builder()
            .name("swing-speed")
            .description("Speed multiplier for swing animation.")
            .defaultValue(1.0)
            .sliderRange(0.1, 3)
            .build()
    );

    // Tracks the hotbar sword slot
    public int swordSlot = -1;

    public Animations() {
        super(AddonTemplate.CATEGORY,
            "Animations",
            "Restores classic 1.7 hand/block/rod animations on swords.");
        INSTANCE = this;
    }

    public void updateSwordSlot(Item[] hotbar) {
        swordSlot = -1;
        for (int i = 0; i < 9; i++) {
            Item item = hotbar[i];
            if (item == null) continue;
            if (item.getClass().getSimpleName().contains("Sword")) {
                swordSlot = i;
                break;
            }
        }
    }
}
