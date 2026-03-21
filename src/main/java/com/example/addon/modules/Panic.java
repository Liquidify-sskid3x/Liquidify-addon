
package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class Panic extends Module {
    private final List<Module> disabledModules = new ArrayList<>();
    private final List<HudElement> disabledHudElements = new ArrayList<>();
    private int tickCount = 0;
    private boolean pendingClear = false;

    public Panic() {
        super(AddonTemplate.CATEGORY, "panic", "Disables all active modules and HUD elements and clears recent client messages.");
    }

    @Override
    public void onActivate() {
        disabledModules.clear();
        disabledHudElements.clear();
        tickCount = 0;
        pendingClear = true;

        // Disable all active modules silently
        List<Module> toDisable = new ArrayList<>();
        for (Module module : Modules.get().getAll()) {
            if (module != this && module.isActive()) {
                toDisable.add(module);
            }
        }
        for (Module module : toDisable) {
            disabledModules.add(module);
            boolean prev = module.chatFeedback;
            module.chatFeedback = false;
            module.toggle();
            module.chatFeedback = prev;
        }

        // Disable all active HUD elements
        List<HudElement> toDisableHud = new ArrayList<>();
        for (HudElement element : Hud.get()) {
            if (element.isActive()) {
                toDisableHud.add(element);
            }
        }
        for (HudElement element : toDisableHud) {
            disabledHudElements.add(element);
            element.toggle();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!pendingClear) return;

        tickCount++;
        if (tickCount >= 1) {
            clearLastClientMessages(100);
            pendingClear = false;
        }
    }

    @Override
    public void onDeactivate() {
        pendingClear = false;
        tickCount = 0;

        // Re-enable all previously active modules silently
        for (Module module : disabledModules) {
            if (!module.isActive()) {
                boolean prev = module.chatFeedback;
                module.chatFeedback = false;
                module.toggle();
                module.chatFeedback = prev;
            }
        }

        // Re-enable all previously active HUD elements
        for (HudElement element : disabledHudElements) {
            if (!element.isActive()) element.toggle();
        }

        disabledModules.clear();
        disabledHudElements.clear();
    }

    private void clearLastClientMessages(int count) {
        ChatHud chatHud = MinecraftClient.getInstance().inGameHud.getChatHud();

        try {
            Field messagesField = ChatHud.class.getDeclaredField("messages");
            messagesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<ChatHudLine.Visible> messages = (List<ChatHudLine.Visible>) messagesField.get(chatHud);

            int removed = 0;
            for (int i = messages.size() - 1; i >= 0 && removed < count; i--) {
                messages.remove(i);
                removed++;
            }

            Field visibleField = ChatHud.class.getDeclaredField("visibleMessages");
            visibleField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<ChatHudLine> visible = (List<ChatHudLine>) visibleField.get(chatHud);

            int removed2 = 0;
            for (int i = visible.size() - 1; i >= 0 && removed2 < count; i--) {
                visible.remove(i);
                removed2++;
            }

        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
