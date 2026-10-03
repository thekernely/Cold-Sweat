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
         * Vanilla world.toml defaults, with one deliberate M7.12 correction:
         *
         * Lava is a continuous radiant field rather than "one heater per
         * fluid voxel". It therefore uses strongest-source aggregation.
         *
         * +75 F relative is ~+41.7 C at the source before distance/obstruction
         * attenuation. This is an initial playtest value, not permanent
         * balance.
         *
         * Boiler and icebox entries intentionally wait for M6 because those
         * blocks do not exist in the Fabric port yet.
         */
        BlockTempRegistry.register(
                StaticBlockTemp.forBlockFahrenheitStrongest(
                        Blocks.LAVA,
                        75, 7, 200, 1000.0, true
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

        /*
         * M7.12e: ordinary ice surfaces are not local "cold radiators".
         *
         * Homeostatic models cold primarily through broad climate, water,
         * wetness/submersion, and body heat transfer. It does not register
         * vanilla ice/packed ice/blue ice as block-radiation sources.
         *
         * This avoids the physically odd case where stepping from regular ice
         * onto packed ice changes the surrounding air temperature by several
         * degrees. Powder snow/freezing, water exposure, future wind/shelter,
         * and deliberately active cooling machines remain valid cold-pressure
         * mechanisms.
         */

        ColdSweatFabric.LOGGER.info(
                "Registered {} Cold Sweat block temperature source type(s).",
                BlockTempRegistry.getEntries().size()
        );
    }

    private BlockTempInit()
    {
    }
}
