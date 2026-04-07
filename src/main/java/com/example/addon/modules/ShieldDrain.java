package com.example.addon.modules;
import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import java.util.Random;
public class ShieldDrain extends Module {
    private final SettingGroup sg=settings.getDefaultGroup();
    public enum TargetMode{Players,PlayersAndMobs}
    private final Setting<TargetMode> targetMode=sg.add(new EnumSetting.Builder<TargetMode>().name("target-mode").defaultValue(TargetMode.Players).build());
    private final Setting<Double> range=sg.add(new DoubleSetting.Builder().name("range").defaultValue(4.0).min(1.0).max(6.0).build());
    private final Setting<Double> heightAdvantage=sg.add(new DoubleSetting.Builder().name("height-advantage").defaultValue(2.0).min(0.5).max(5.0).build());
    private final Setting<Integer> cps=sg.add(new IntSetting.Builder().name("cps").defaultValue(20).min(1).max(45).sliderMin(1).sliderMax(45).build());
    private final Setting<Integer> delay=sg.add(new IntSetting.Builder().name("delay-ticks").defaultValue(3).min(0).max(20).build());
    private final Setting<Boolean> requireCrosshair=sg.add(new BoolSetting.Builder().name("require-crosshair").defaultValue(false).build());
    private final Setting<Boolean> spoofRotations=sg.add(new BoolSetting.Builder().name("spoof-rotations").defaultValue(true).build());
    private int tickTimer=0;
    private double currentCps=20;
    private double targetCps=20;
    private int cpsUpdateTimer=0;
    private final Random rng=new Random();
    public ShieldDrain(){super(AddonTemplate.CATEGORY,"shield-drain","Rapid mace attacks from above to drain shields.");}
    @Override public void onActivate(){tickTimer=0;currentCps=cps.get();targetCps=cps.get();}
    @EventHandler private void onTick(TickEvent.Pre event){
        if(mc.player==null||mc.world==null||mc.interactionManager==null)return;
        // Update target CPS every 20 ticks with random variation of +-5
        cpsUpdateTimer++;
        if(cpsUpdateTimer>=20){
            cpsUpdateTimer=0;
            double base=cps.get();
            targetCps=Math.max(1,Math.min(45,base+(rng.nextDouble()*10-5)));}
        // Smoothly move currentCps toward targetCps by 0.5 per tick
        if(currentCps<targetCps)currentCps=Math.min(targetCps,currentCps+0.5);
        else if(currentCps>targetCps)currentCps=Math.max(targetCps,currentCps-0.5);
        tickTimer++;
        int cpsDelay=Math.max(1,(int)(20.0/currentCps));
        int finalDelay=Math.max(delay.get(),cpsDelay);
        if(tickTimer<finalDelay)return;
        LivingEntity target=findTarget();
        if(target==null)return;
        if(requireCrosshair.get()&&!isInCrosshair(target))return;
        if(mc.player.distanceTo(target)>range.get())return;
        double heightDiff=mc.player.getEyeY()-target.getEyeY();
        if(heightDiff<heightAdvantage.get())return;
        if(mc.player.getAttackCooldownProgress(0.5f)<1f)return;
        int maceSlot=findMaceSlot();
        if(maceSlot==-1)return;
        int prevSlot= mc.player.getInventory().getSelectedSlot();
        if(spoofRotations.get()){
            Rotations.rotate(Rotations.getYaw(target),Rotations.getPitch(target),()->attack(prevSlot,maceSlot,target));
        }else{attack(prevSlot,maceSlot,target);}
        tickTimer=0;}
    private void attack(int prevSlot,int maceSlot,LivingEntity target){
        mc.player.getInventory().setSelectedSlot(maceSlot);
        mc.interactionManager.attackEntity(mc.player,target);
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.getInventory().setSelectedSlot(prevSlot);}
    private int findMaceSlot(){
        for(int i=0;i<9;i++){if(mc.player.getInventory().getStack(i).getItem()==Items.MACE)return i;}
        return -1;}
    private LivingEntity findTarget(){
        double r=range.get();
        LivingEntity best=null;double bestDist=Double.MAX_VALUE;
        for(PlayerEntity p:mc.world.getPlayers()){
            if(p==mc.player||p.isDead()||!p.isBlocking())continue;
            double d=mc.player.distanceTo(p);
            if(d<=r&&d<bestDist){bestDist=d;best=p;}}
        if(targetMode.get()==TargetMode.PlayersAndMobs){
            for(Entity e:mc.world.getEntities()){
                if(!(e instanceof MobEntity mob)||mob.isDead())continue;
                double d=mc.player.distanceTo(mob);
                if(d<=r&&d<bestDist){bestDist=d;best=mob;}}}
        return best;}
    private boolean isInCrosshair(Entity target){
        if(mc.crosshairTarget==null)return false;
        if(mc.crosshairTarget.getType()==HitResult.Type.ENTITY)
            return((EntityHitResult)mc.crosshairTarget).getEntity()==target;
        return false;}
}
