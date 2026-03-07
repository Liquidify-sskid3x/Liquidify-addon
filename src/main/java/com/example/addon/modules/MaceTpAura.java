package com.example.addon.modules;

import com.example.addon.mixin.ExampleMixin;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IPlayerMoveC2SPacket;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import com.example.addon.AddonTemplate;

public class MaceTpAura extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgOrbit   = settings.createGroup("Orbit");
    private final SettingGroup sgMace    = settings.createGroup("Mace Smash");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Attack range in blocks.")
        .defaultValue(6.0)
        .min(1.0)
        .sliderMax(10.0)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between each attack.")
        .defaultValue(10)
        .min(0)
        .sliderMax(40)
        .build()
    );

    private final Setting<Boolean> targetPlayers = sgGeneral.add(new BoolSetting.Builder()
        .name("target-players")
        .description("Attack other players.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> targetMobs = sgGeneral.add(new BoolSetting.Builder()
        .name("target-mobs")
        .description("Attack mobs.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> orbitRadius = sgOrbit.add(new DoubleSetting.Builder()
        .name("orbit-radius")
        .description("How far from the target to orbit in blocks.")
        .defaultValue(1.0)
        .min(0.5)
        .sliderMax(4.0)
        .build()
    );

    private final Setting<Double> orbitSpeed = sgOrbit.add(new DoubleSetting.Builder()
        .name("orbit-speed")
        .description("How fast to spin around the target in degrees per tick.")
        .defaultValue(15.0)
        .min(1.0)
        .sliderMax(90.0)
        .build()
    );

    private final Setting<Boolean> smashAttack = sgMace.add(new BoolSetting.Builder()
        .name("smash-attack")
        .description("Spoof movement packets to always trigger the mace smash attack bonus.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> extraHeight = sgMace.add(new DoubleSetting.Builder()
        .name("additional-height")
        .description("Extra height to spoof on top of the base smash height. More = more damage.")
        .defaultValue(0.0)
        .min(0)
        .sliderRange(0, 100)
        .visible(smashAttack::get)
        .build()
    );

    private int tickTimer  = 0;
    private double orbitAngle = 0.0;

    public MaceTpAura() {
        super(AddonTemplate.CATEGORY, "mace-tp-aura",
            "Orbits around nearby entities and attacks them with a mace smash.");
    }

    @Override
    public void onActivate() {
        tickTimer  = 0;
        orbitAngle = 0.0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        LivingEntity target = findTarget();

        if (target != null) {
            orbitAroundTarget(target);
        }

        if (tickTimer > 0) {
            tickTimer--;
            return;
        }

        if (target == null) return;

        int maceSlot = findMaceOnHotbar();
        if (maceSlot == -1) return;

        ExampleMixin inventory = (ExampleMixin) (Object) mc.player.getInventory();
        int originalSlot = inventory.getSelectedSlot();

        if (maceSlot != originalSlot) inventory.setSelectedSlot(maceSlot);

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        if (maceSlot != originalSlot) inventory.setSelectedSlot(originalSlot);

        tickTimer = delay.get();
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (!smashAttack.get()) return;
        if (mc.player == null) return;
        if (!mc.player.getMainHandStack().isOf(Items.MACE)) return;

        if (event.packet instanceof PlayerInteractEntityC2SPacket) {
            if (mc.player.isGliding()) return;

            sendHeightPacket(0);
            sendHeightPacket(1.501 + extraHeight.get());
            sendHeightPacket(0);
        }
    }

    private void orbitAroundTarget(LivingEntity target) {
        orbitAngle = (orbitAngle + orbitSpeed.get()) % 360.0;

        double rad = Math.toRadians(orbitAngle);
        double r   = orbitRadius.get();

        double tpX = target.getX() + Math.cos(rad) * r;
        double tpZ = target.getZ() + Math.sin(rad) * r;
        double tpY = target.getY();

        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            tpX, tpY, tpZ, mc.player.isOnGround(), mc.player.horizontalCollision
        ));
        mc.player.setPosition(tpX, tpY, tpZ);
    }

    private void sendHeightPacket(double height) {
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();

        PlayerMoveC2SPacket packet = new PlayerMoveC2SPacket.PositionAndOnGround(
            x, y + height, z, false, mc.player.horizontalCollision
        );
        ((IPlayerMoveC2SPacket) packet).meteor$setTag(1337);
        mc.player.networkHandler.sendPacket(packet);
    }

    private int findMaceOnHotbar() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.MACE)) return i;
        }
        return -1;
    }

    private LivingEntity findTarget() {
        LivingEntity closest     = null;
        double       closestDist = Double.MAX_VALUE;
        double       r           = range.get();

        for (LivingEntity entity : mc.world.getEntitiesByClass(
            LivingEntity.class,
            mc.player.getBoundingBox().expand(r),
            e -> true)) {

            if (entity == mc.player) continue;
            if (entity.isDead() || !entity.isAlive()) continue;

            if (entity instanceof PlayerEntity && !targetPlayers.get()) continue;
            if (entity instanceof MobEntity && !targetMobs.get()) continue;
            if (!(entity instanceof PlayerEntity) && !(entity instanceof MobEntity) && !targetMobs.get()) continue;

            double dist = mc.player.distanceTo(entity);
            if (dist <= r && dist < closestDist) {
                closestDist = dist;
                closest     = entity;
            }
        }

        return closest;
    }
}
