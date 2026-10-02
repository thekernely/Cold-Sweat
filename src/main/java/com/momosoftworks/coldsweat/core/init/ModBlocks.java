package com.momosoftworks.coldsweat.core.init;

import com.momosoftworks.coldsweat.common.block.BoilerBlock;
import com.momosoftworks.coldsweat.common.block.HearthBottomBlock;
import com.momosoftworks.coldsweat.common.block.HearthTopBlock;
import com.momosoftworks.coldsweat.common.block.IceboxBlock;
import com.momosoftworks.coldsweat.common.block.SmokestackBlock;
import com.momosoftworks.coldsweat.common.block.SlushLiquidBlock;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Function;

/**
 * M6 thermal-machine block registry.
 *
 * The full upstream block behavior is restored incrementally on top of these
 * stable 26.2 registrations. Keeping IDs identical preserves datapack/model
 * compatibility while the block entities gain their gameplay systems.
 */
public final class ModBlocks
{
    public static final HearthBottomBlock HEARTH_BOTTOM = register(
            "hearth_bottom",
            HearthBottomBlock::new,
            BlockBehaviour.Properties.of()
                    .sound(SoundType.STONE)
                    .strength(2.0F, 10.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
    );

    public static final HearthTopBlock HEARTH_TOP = register(
            "hearth_top",
            HearthTopBlock::new,
            BlockBehaviour.Properties.of()
                    .sound(SoundType.STONE)
                    .strength(2.0F, 10.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
    );

    public static final BoilerBlock BOILER = register(
            "boiler",
            BoilerBlock::new,
            BlockBehaviour.Properties.of()
                    .sound(SoundType.STONE)
                    .strength(2.0F, 10.0F)
                    .requiresCorrectToolForDrops()
    );

    public static final IceboxBlock ICEBOX = register(
            "icebox",
            IceboxBlock::new,
            BlockBehaviour.Properties.of()
                    .sound(SoundType.WOOD)
                    .strength(2.0F, 5.0F)
                    .noOcclusion()
    );

    public static final SmokestackBlock SMOKESTACK = register(
            "smokestack",
            SmokestackBlock::new,
            BlockBehaviour.Properties.of()
                    .sound(SoundType.STONE)
                    .strength(2.0F, 10.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
    );


    public static final SlushLiquidBlock SLUSH = register(
            "slush",
            properties -> new SlushLiquidBlock(
                    ModFluids.SLUSH,
                    properties
            ),
            BlockBehaviour.Properties.of()
                    .replaceable()
                    .noCollision()
                    .strength(100.0F)
                    .noLootTable()
                    .liquid()
                    .sound(SoundType.EMPTY)
    );

    private static <T extends Block> T register(
            String path,
            Function<BlockBehaviour.Properties, T> factory,
            BlockBehaviour.Properties properties
    )
    {
        Identifier id = ColdSweatFabric.id(path);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        T block = factory.apply(properties.setId(key));
        return Registry.register(BuiltInRegistries.BLOCK, key, block);
    }

    public static void initialize()
    {
        ColdSweatFabric.LOGGER.info("Registering Cold Sweat thermal machines, Smokestack, and Slush block.");
    }

    private ModBlocks()
    {
    }
}
