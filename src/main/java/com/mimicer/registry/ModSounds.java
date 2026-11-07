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

    /** Voz de The Void: "No te acerques". */
    public static final RegistryObject<SoundEvent> VOID_WARNING = register("void_warning");

    public static final RegistryObject<SoundEvent> GLITCH_APPEAR = register("glitch_appear");
    public static final RegistryObject<SoundEvent> GLITCH_JUMPSCARE = register("glitch_jumpscare");
    public static final RegistryObject<SoundEvent> GLITCH_CORRUPT = register("glitch_corrupt");
    public static final RegistryObject<SoundEvent> GLITCH_CHARGE = register("glitch_charge");
    public static final RegistryObject<SoundEvent> GLITCH_BEAM = register("glitch_beam");
    public static final RegistryObject<SoundEvent> GLITCH_STEP = register("glitch_step");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MimicerMod.MOD_ID, name)));
    }
}
