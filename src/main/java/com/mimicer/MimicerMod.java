package com.mimicer;

import com.mimicer.registry.ModBlocks;
import com.mimicer.registry.ModEntities;
import com.mimicer.registry.ModItems;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.IEventBus;

@Mod(MimicerMod.MOD_ID)
public class MimicerMod {
    public static final String MOD_ID = "mimicer";

    public MimicerMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(bus);
        ModItems.ITEMS.register(bus);
        ModEntities.ENTITIES.register(bus);
        com.mimicer.registry.ModSounds.SOUNDS.register(bus);
        ModItems.TABS.register(bus);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
                net.minecraftforge.fml.config.ModConfig.Type.COMMON, com.mimicer.config.GlitchConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(this);
    }
}
