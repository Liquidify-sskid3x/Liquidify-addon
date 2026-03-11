package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.CommandBlockBlockEntity;
import net.minecraft.block.entity.CommandBlockBlockEntity.Type;
import net.minecraft.network.packet.c2s.play.UpdateCommandBlockC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class LongCommandExecutor extends Module {

    private enum State {
        IDLE, GIVE, WAIT_GIVE, PLACE, SET_COMMAND, ACTIVATE, DONE
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> command = sgGeneral.add(new StringSetting.Builder()
        .name("command")
        .description("The long command to execute. Do not add a / ok?")
        .defaultValue("summon falling_block ~1 ~ ~ {BlockState:{Name:\"minecraft:redstone_block\"},Time:1,Passengers:[{id:\"minecraft:falling_block\",BlockState:{Name:\"minecraft:activator_rail\",Properties:{powered:\"true\"}},Time:1,Passengers:[{id:\"minecraft:command_block_minecart\",Command:\"setblock ~3 ~ ~ minecraft:command_block{auto:1b,Command:\\\"/tick rate 3000\\\"}\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a armor.head with minecraft:carved_pumpkin\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a hotbar.0 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a hotbar.1 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a hotbar.2 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a hotbar.3 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a hotbar.4 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a hotbar.5 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a hotbar.6 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a hotbar.7 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a hotbar.8 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.0 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.1 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.2 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.3 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.4 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.5 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.6 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.7 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.8 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.9 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.10 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.11 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.12 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.13 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.14 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.15 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.16 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.17 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.18 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.19 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.20 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.21 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.22 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.23 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.24 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.25 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a inventory.26 with minecraft:barrier[minecraft:custom_name='NUKED BY LIQUIDIFY']\"},{id:\"minecraft:command_block_minecart\",Command:\"gamemode adventure @a\"},{id:\"minecraft:command_block_minecart\",Command:\"scoreboard objectives add nuker dummy \\\"NUKED BY LIQUIDIFY\\\"\"},{id:\"minecraft:command_block_minecart\",Command:\"scoreboard objectives setdisplay sidebar nuker\"},{id:\"minecraft:command_block_minecart\",Command:\"scoreboard players set ADD_ME_ON_DISCORD_Liquidify.net nuker 3\"},{id:\"minecraft:command_block_minecart\",Command:\"scoreboard players set NUKED_BY_LIQUIDIFY nuker 2\"},{id:\"minecraft:command_block_minecart\",Command:\"scoreboard players set NUKER_v1.1 nuker 1\"},{id:\"minecraft:command_block_minecart\",Command:\"title @a title {\\\"text\\\":\\\"NUKED BY LIQUIDIFY\\\",\\\"color\\\":\\\"red\\\",\\\"bold\\\":true}\"},{id:\"minecraft:command_block_minecart\",Command:\"title @a subtitle {\\\"text\\\":\\\"CUSTOM NUKER v1.0\\\",\\\"color\\\":\\\"dark_red\\\"}\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a weapon.offhand with minecraft:totem_of_undying\"},{id:\"minecraft:command_block_minecart\",Command:\"effect give @a minecraft:instant_damage 1 5 true\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a weapon.offhand with minecraft:totem_of_undying\"},{id:\"minecraft:command_block_minecart\",Command:\"effect give @a minecraft:instant_damage 1 5 true\"},{id:\"minecraft:command_block_minecart\",Command:\"item replace entity @a weapon.offhand with minecraft:totem_of_undying\"},{id:\"minecraft:command_block_minecart\",Command:\"effect give @a minecraft:instant_damage 1 5 true\"},{id:\"minecraft:command_block_minecart\",Command:\"tp @a ~ 15000000 ~\"},{id:\"minecraft:command_block_minecart\",Command:\"execute at @a run particle minecraft:elder_guardian ~ ~ ~ 0 0 0 0 1 force\"},{id:\"minecraft:command_block_minecart\",Command:\"playsound minecraft:entity.elder_guardian.curse master @a ~ ~ ~ 1000 1 1\"},{id:\"minecraft:command_block_minecart\",Command:\"playsound minecraft:entity.wither.death master @a ~ ~ ~ 1000 1 1\"},{id:\"minecraft:command_block_minecart\",Command:\"playsound minecraft:entity.ender_dragon.death master @a ~ ~ ~ 1000 1 1\"},{id:\"minecraft:command_block_minecart\",Command:\"effect give @a minecraft:nausea 999999 127 true\"},{id:\"minecraft:command_block_minecart\",Command:\"effect give @a minecraft:wither 999999 127 true\"},{id:\"minecraft:command_block_minecart\",Command:\"effect give @a minecraft:health_boost 999999 127 true\"},{id:\"minecraft:command_block_minecart\",Command:\"effect give @a minecraft:hunger 999999 127 true\"},{id:\"minecraft:command_block_minecart\",Command:\"playsound minecraft:entity.wither.spawn master @a ~ ~ ~ 1000 1 1\"},{id:\"minecraft:command_block_minecart\",Command:\"playsound minecraft:ui.toast.challenge_complete master @a ~ ~ ~ 1000 1 1\"}]}]}")
        .build()
    );

    private State state = State.IDLE;
    private int ticker = 0;
    private BlockPos targetPos;

    public LongCommandExecutor() {
        super(AddonTemplate.CATEGORY, "long-command", "Executes long commands via a command block.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            error("Not in a world.");
            toggle();
            return;
        }
        if (command.get().isBlank()) {
            error("No command set.");
            toggle();
            return;
        }
        state = State.GIVE;
        ticker = 0;
        targetPos = mc.player.getBlockPos().up(2);
        info("Starting long command executor...");
    }

    @Override
    public void onDeactivate() {
        state = State.IDLE;
        ticker = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        ticker++;

        switch (state) {
            case GIVE -> {
                sendCmd("gamemode creative " + mc.player.getName().getString());
                info("Set gamemode to creative.");
                sendCmd("give " + mc.player.getName().getString() + " minecraft:command_block 1");
                info("Gave command block.");
                state = State.WAIT_GIVE;
                ticker = 0;
            }
            case WAIT_GIVE -> {
                if (ticker >= 10) {
                    state = State.PLACE;
                    ticker = 0;
                }
            }
            case PLACE -> {
                sendCmd("setblock " + targetPos.getX() + " " + targetPos.getY() + " " + targetPos.getZ() + " minecraft:command_block");
                info("Placed command block at " + targetPos.toShortString() + ".");
                state = State.SET_COMMAND;
                ticker = 0;
            }
            case SET_COMMAND -> {
                if (ticker >= 10) {
                    if (mc.world.getBlockState(targetPos).getBlock() == Blocks.COMMAND_BLOCK) {
                        mc.getNetworkHandler().sendPacket(new UpdateCommandBlockC2SPacket(
                            targetPos,
                            command.get(),
                            Type.REDSTONE,
                            false,
                            false,
                            true
                        ));
                        info("Pasted command into command block successfully!");
                        state = State.ACTIVATE;
                        ticker = 0;
                    } else if (ticker >= 40) {
                        error("Command block did not appear at " + targetPos.toShortString() + " — are you op?");
                        toggle();
                    }
                }
            }
            case ACTIVATE -> {
                if (ticker >= 10) {
                    BlockPos redstonePos = targetPos.offset(Direction.NORTH);
                    sendCmd("setblock " + redstonePos.getX() + " " + redstonePos.getY() + " " + redstonePos.getZ() + " minecraft:redstone_block");
                    info("Activated command block.");
                    state = State.DONE;
                    ticker = 0;
                }
            }
            case DONE -> {
                if (ticker >= 10) {
                    info("Long command executed successfully!");
                    toggle();
                }
            }
        }
    }

    private void sendCmd(String cmd) {
        if (mc.getNetworkHandler() == null) return;
        mc.getNetworkHandler().sendChatCommand(cmd);
    }
}
