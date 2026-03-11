package com.example.addon;


import com.example.addon.commands.LoginAccountCommand;
import com.example.addon.hud.HudExample;
import com.example.addon.modules.*;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;

import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.hud.XAnchor;
import meteordevelopment.meteorclient.systems.hud.YAnchor;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Liquidify Addon");
    public static final HudGroup HUD_GROUP = new HudGroup("Example");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Meteor Addon Template");

        Modules.get().add(new MaceTpAura());
        Modules.get().add(new TpToNearest());
        Modules.get().add(new AutoOp());
        Modules.get().add(new NoGravity());
        Modules.get().add(new LongCommandExecutor());
        Commands.add(new LoginAccountCommand());
        Hud.get().register(HudExample.INFO);
        Hud.get().add(HudExample.INFO, 10, 10, XAnchor.Center, YAnchor.Top);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("MeteorDevelopment", "meteor-addon-template");
    }
}
