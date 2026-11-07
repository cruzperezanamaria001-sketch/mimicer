package com.mimicer.registry;

import com.mimicer.MimicerMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MimicerMod.MOD_ID);

    /** Grito del aldeano deformado (Mímico): se elige al azar uno de los archivos listados en sounds.json. */
    public static final RegistryObject<SoundEvent> MIMIC_SCREAM = register("aldeano_grito");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MimicerMod.MOD_ID, name)));
    }
}
