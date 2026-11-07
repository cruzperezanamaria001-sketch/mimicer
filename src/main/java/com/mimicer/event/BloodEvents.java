package com.mimicer.event;

import com.mimicer.MimicerMod;
import com.mimicer.blood.BloodSystem;
import com.mimicer.config.BloodConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Enlaza el Dynamic Blood System con los eventos de daño, muerte y tick (solo servidor). */
@Mod.EventBusSubscriber(modid = MimicerMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BloodEvents {
    private BloodEvents() {}

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || !BloodConfig.ENABLED.get()
                || !BloodSystem.canBleed(victim) || event.getAmount() < 1.0F
                || !BloodSystem.bloodySource(event.getSource())) {
            return;
        }
        BloodSystem.onHit(level, victim, event.getSource(), event.getAmount());
    }

    /** Antes que GameEvents: aún se conservan las extremidades perdidas de la víctima. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level() instanceof ServerLevel level && BloodSystem.canBleed(victim)) {
            BloodSystem.onDeath(level, victim);
        }
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (e.tickCount % 10 == 0 && e.level() instanceof ServerLevel level) {
            BloodSystem.tickWound(level, e);
        }
    }
}
