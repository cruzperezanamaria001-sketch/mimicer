package com.mimicer.registry;

import com.mimicer.MimicerMod;
import com.mimicer.block.BoardBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MimicerMod.MOD_ID);

    public static final RegistryObject<Block> BOARD = BLOCKS.register("tabla",
            () -> new BoardBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.0F, 3.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));

    public static final RegistryObject<Block> GLITCH_BLOCK = BLOCKS.register("glitch_block",
            () -> new com.mimicer.block.GlitchBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 4)));

    public static final RegistryObject<Block> BLOOD_STAIN = BLOCKS.register("blood_stain",
            () -> new com.mimicer.block.BloodStainBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .noCollission()
                    .noOcclusion()
                    .replaceable()
                    .noLootTable()
                    .instabreak()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));

    public static final RegistryObject<Block> GLITCHED_BARRICADE = BLOCKS.register("glitched_barricade",
            () -> new com.mimicer.block.GlitchBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(1.0F, 3.0F)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 6)));
}
