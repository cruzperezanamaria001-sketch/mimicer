package com.mimicer.registry;

import com.mimicer.MimicerMod;
import com.mimicer.block.WardrobeBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MimicerMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<WardrobeBlockEntity>> WARDROBE = BLOCK_ENTITIES.register("wardrobe",
            () -> BlockEntityType.Builder.of((pos, state) -> new WardrobeBlockEntity(ModBlockEntities.WARDROBE.get(), pos, state),
                    ModBlocks.WARDROBE.get()).build(null));
}
