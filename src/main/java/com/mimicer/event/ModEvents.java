package com.mimicer.event;

import com.mimicer.MimicerMod;
import com.mimicer.entity.MimicEntity;
import com.mimicer.net.NetworkHandler;
import com.mimicer.registry.ModEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod.EventBusSubscriber(modid = MimicerMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEvents {
    private ModEvents() {}

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.MIMIC.get(), MimicEntity.createAttributes().build());
        event.put(ModEntities.GLITCH.get(), com.mimicer.entity.GlitchEntity.createAttributes().build());
        event.put(ModEntities.VOID.get(), com.mimicer.entity.VoidEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onSpawnPlacement(net.minecraftforge.event.entity.SpawnPlacementRegisterEvent event) {
        event.register(ModEntities.VOID.get(),
                net.minecraft.world.entity.SpawnPlacements.Type.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.world.entity.monster.Monster::checkMonsterSpawnRules,
                net.minecraftforge.event.entity.SpawnPlacementRegisterEvent.Operation.OR);
        event.register(ModEntities.GLITCH.get(),
                net.minecraft.world.entity.SpawnPlacements.Type.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.mimicer.entity.GlitchEntity::checkGlitchSpawn,
                net.minecraftforge.event.entity.SpawnPlacementRegisterEvent.Operation.OR);
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(NetworkHandler::register);
    }
}
