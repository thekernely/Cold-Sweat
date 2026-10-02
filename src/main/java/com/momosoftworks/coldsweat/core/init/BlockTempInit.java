package com.momosoftworks.coldsweat.core.init;

import com.momosoftworks.coldsweat.api.registry.BlockTempRegistry;
import com.momosoftworks.coldsweat.api.temperature.block_temp.FurnaceBlockTemp;
import com.momosoftworks.coldsweat.api.temperature.block_temp.NetherPortalBlockTemp;
import com.momosoftworks.coldsweat.api.temperature.block_temp.StaticBlockTemp;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Fabric-native block-temperature source bootstrap.
 */
public final class BlockTempInit
{
    public static void initialize()
    {
        BlockTempRegistry.flush();

        // Java-defined upstream sources.
        BlockTempRegistry.register(
                new FurnaceBlockTemp()
        );
        BlockTempRegistry.register(
                new NetherPortalBlockTemp()
        );

        /*
         * Vanilla world.toml defaults.
         *
         * Boiler and icebox entries intentionally wait for M6 because those
         * blocks do not exist in the Fabric port yet.
         */
        BlockTempRegistry.register(
                StaticBlockTemp.forBlockFahrenheit(
                        Blocks.LAVA,
                        30, 7, 200, 1000.0, true
                )
        );

        BlockTempRegistry.register(
                StaticBlockTemp.forTagFahrenheit(
                        BlockTags.FIRE,
                        25, 7, 50, 400.0,
                        state -> true,
                        true
                )
        );

        BlockTempRegistry.register(
                StaticBlockTemp.forTagFahrenheit(
                        BlockTags.CAMPFIRES,
                        25, 7, 50, 400.0,
                        state -> state.hasProperty(BlockStateProperties.LIT)
                                && state.getValue(BlockStateProperties.LIT),
                        true
                )
        );

        BlockTempRegistry.register(
                StaticBlockTemp.forBlockFahrenheit(
                        Blocks.MAGMA_BLOCK,
                        20, 3, 48, null, false
                )
        );

        BlockTempRegistry.register(
                StaticBlockTemp.forBlockFahrenheit(
                        Blocks.LAVA_CAULDRON,
                        30, 7, 200, 1000.0, true
                )
        );

        BlockTempRegistry.register(
                StaticBlockTemp.forBlockFahrenheit(
                        Blocks.ICE,
                        -10, 4, 24, 33.0, false
                )
        );

        BlockTempRegistry.register(
                StaticBlockTemp.forBlockFahrenheit(
                        Blocks.PACKED_ICE,
                        -15, 4, 48, 16.0, false
                )
        );

        BlockTempRegistry.register(
                StaticBlockTemp.forBlockFahrenheit(
                        Blocks.BLUE_ICE,
                        -20, 4, 64, 0.0, false
                )
        );

        BlockTempRegistry.register(
                StaticBlockTemp.forTagFahrenheit(
                        BlockTags.ICE,
                        -6, 4, 27, 33.0,
                        state -> true,
                        false
                )
        );

        ColdSweatFabric.LOGGER.info(
                "Registered {} Cold Sweat block temperature source type(s).",
                BlockTempRegistry.getEntries().size()
        );
    }

    private BlockTempInit()
    {
    }
}
