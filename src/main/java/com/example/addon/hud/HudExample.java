package com.example.addon.hud;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.MinecraftClient;

public class HudExample extends HudElement {

    public static final HudElementInfo<HudExample> INFO = new HudElementInfo<>(
        AddonTemplate.HUD_GROUP, "LIQUIDIFY", "Displays Hello (username)!", HudExample::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> prefix = sgGeneral.add(new StringSetting.Builder()
        .name("prefix")
        .description("Text before your username.")
        .defaultValue("Hello ")
        .build()
    );

    private final Setting<String> suffix = sgGeneral.add(new StringSetting.Builder()
        .name("suffix")
        .description("Text after your username.")
        .defaultValue("!")
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Color of the text.")
        .defaultValue(new SettingColor(170, 0, 255, 255))
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Text scale.")
        .defaultValue(2.0)
        .min(0.5)
        .sliderMax(5.0)
        .build()
    );

    public HudExample() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        MinecraftClient mc = MinecraftClient.getInstance();

        String name = mc.getSession().getUsername();

        String text = prefix.get() + name + suffix.get();

        setSize(renderer.textWidth(text, true) * scale.get(), renderer.textHeight(true) * scale.get());
        renderer.text(text, x, y, color.get(), true, scale.get());
    }
}
