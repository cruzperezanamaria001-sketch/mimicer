package com.mimicer.registry;

import com.mimicer.MimicerMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MimicerMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MimicerMod.MOD_ID);

    public static final RegistryObject<Item> BOARD = ITEMS.register("tabla",
            () -> new BlockItem(ModBlocks.BOARD.get(), new Item.Properties()));

    public static final RegistryObject<Item> MIMIC_SPAWN_EGG = ITEMS.register("mimico_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.MIMIC,
                    0x8a6f4d, 0x5a0000, new Item.Properties()));

    public static final RegistryObject<Item> VOID_SPAWN_EGG = ITEMS.register("the_void_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.VOID,
                    0x050505, 0xcc0000, new Item.Properties()));

    public static final RegistryObject<Item> GLITCH_BLOCK = ITEMS.register("glitch_block",
            () -> new BlockItem(ModBlocks.GLITCH_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> WARDROBE = ITEMS.register("wardrobe",
            () -> new BlockItem(ModBlocks.WARDROBE.get(), new Item.Properties()));

    public static final RegistryObject<Item> REAPER_SPAWN_EGG = ITEMS.register("the_reaper_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.REAPER,
                    0x0b0b10, 0x8fe3ff, new Item.Properties()));

    public static final RegistryObject<Item> GLITCH_SPAWN_EGG = ITEMS.register("the_glitch_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.GLITCH,
                    0x120018, 0xb400ff, new Item.Properties()));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("the_mimic",
            () -> CreativeModeTab.builder()
                    .title(Component.literal("The Mimic"))
                    .icon(() -> new ItemStack(BOARD.get()))
                    .displayItems((params, output) -> {
                        output.accept(BOARD.get());
                        output.accept(MIMIC_SPAWN_EGG.get());
                        output.accept(VOID_SPAWN_EGG.get());
                        output.accept(GLITCH_SPAWN_EGG.get());
                        output.accept(GLITCH_BLOCK.get());
                        output.accept(REAPER_SPAWN_EGG.get());
                        output.accept(WARDROBE.get());
                    })
                    .build());
}
