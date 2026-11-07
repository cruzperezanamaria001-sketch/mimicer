package com.mimicer.registry;

import com.mimicer.MimicerMod;
import com.mimicer.entity.CorpseEntity;
import com.mimicer.entity.MimicEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MimicerMod.MOD_ID);

    public static final RegistryObject<EntityType<MimicEntity>> MIMIC = ENTITIES.register("mimico",
            () -> EntityType.Builder.of(MimicEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("mimico"));

    public static final RegistryObject<EntityType<CorpseEntity>> CORPSE = ENTITIES.register("cuerpo",
            () -> EntityType.Builder.of(CorpseEntity::new, MobCategory.MISC)
                    .sized(1.4F, 0.4F)
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .build("cuerpo"));
}
