package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

public class AutoScoreboard extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTitle   = settings.createGroup("Title Options");
    private final SettingGroup sgContent = settings.createGroup("Content Options");

    private final Setting<String> title = sgTitle.add(new StringSetting.Builder()
        .name("title")
        .description("Title shown at the top of the scoreboard.")
        .defaultValue("Liquidify")
        .wide()
        .build()
    );

    private final Setting<List<String>> content = sgContent.add(new StringListSetting.Builder()
        .name("content")
        .description("Lines of the scoreboard. {player} = your name.")
        .defaultValue(Arrays.asList(
            " ",
            "-      ---   ---   -   -  ---  ---    ---  ----  -   -",
            "-       -   -   -  -   -   -   -  -    -   -     -   -",
            "-       -   -   -  -   -   -   -   -   -   -      - -",
            "-       -   -   -  -   -   -   -   -   -   ---     -",
            "-       -   - - -  -   -   -   -   -   -   -       -",
            "-       -   -  --  -   -   -   -  -    -   -       -",
            "----   ---   ----   ---   ---  ---    ---  -       -",
            " ",
            "liquidify.xyz",
            "pwned by {player}"
        ))
        .build()
    );

    private final Setting<Boolean> useDelay = sgGeneral.add(new BoolSetting.Builder()
        .name("use-delay")
        .description("Add delay between commands to avoid kicks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> commandDelay = sgGeneral.add(new IntSetting.Builder()
        .name("command-delay")
        .description("Ticks between each command.")
        .defaultValue(3)
        .min(1)
        .sliderMax(20)
        .visible(useDelay::get)
        .build()
    );

    private int tickCounter = 0;
    private final Queue<String> commandQueue = new LinkedList<>();

    public AutoScoreboard() {
        super(AddonTemplate.CATEGORY, "auto-scoreboard", "Creates a scoreboard sidebar. Requires operator access.");
    }

    @Override
    public void onActivate() {
        commandQueue.clear();
        tickCounter = 0;

        if (mc.player == null) { toggle(); return; }

        String playerName = mc.player.getGameProfile().name();
        String sbName = "sb" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        commandQueue.add("scoreboard objectives add " + sbName + " dummy " + title.get());
        commandQueue.add("scoreboard objectives setdisplay sidebar " + sbName);

        List<String> lines = content.get();
        int score = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i).replace("{player}", playerName);
            // Team name: short alphanumeric
            String teamName = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            // Fake player name: just the index number, no spaces, no special chars
            String fakeName = "p" + i;

            commandQueue.add("team add " + teamName);
            commandQueue.add("team modify " + teamName + " prefix {\"text\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
            // Suffix pads with spaces to push the fake name off the right edge of the scoreboard
            commandQueue.add("team modify " + teamName + " suffix {\"text\":\"                                        \"}");
            commandQueue.add("team join " + teamName + " " + fakeName);
            commandQueue.add("scoreboard players set " + fakeName + " " + sbName + " " + score);
            score--;
        }
    }

    @Override
    public void onDeactivate() {
        commandQueue.clear();
        tickCounter = 0;
    }

    @EventHandler
    public void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        int delay = useDelay.get() ? commandDelay.get() : 0;

        if (!commandQueue.isEmpty()) {
            if (tickCounter >= delay) {
                String cmd = commandQueue.poll();
                if (cmd != null) mc.getNetworkHandler().sendChatCommand(cmd);
                tickCounter = 0;
            } else {
                tickCounter++;
            }
        } else {
            info("Scoreboard created.");
            toggle();
        }
    }
}
