package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.orbit.EventHandler;

public class ThemisSpeed extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("timer sped")
        .defaultValue(1.3)
        .min(1.0)
        .max(3.0)
        .sliderMin(1.0)
        .sliderMax(3.0)
        .build()
    );

    public ThemisSpeed() {
        super(AddonTemplate.CATEGORY, "themis-speed", "boost sped");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        Timer timer = Modules.get().get(Timer.class);
        if (timer != null) timer.setOverride(speed.get());
    }

    @Override
    public void onActivate() {
        Timer timer = Modules.get().get(Timer.class);
        if (timer != null) timer.setOverride(speed.get());
    }

    @Override
    public void onDeactivate() {
        Timer timer = Modules.get().get(Timer.class);
        if (timer != null) timer.setOverride(Timer.OFF);
    }
}
