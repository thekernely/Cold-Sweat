package com.momosoftworks.coldsweat.fabric.temperature;

import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Small Fabric-side registry for sources that primarily heat the player by
 * radiation rather than by changing the bulk ambient air temperature.
 *
 * Initial vanilla strengths use Homeostatic's MIT-licensed 26.2 radiation data
 * as a calibration reference. The aggregation rule is deliberately not copied
 * blindly: lava uses strongest-source semantics so one spreading source does
 * not become a stack of independent heaters.
 *
 * Soul-fire variants are intentionally excluded for now because legacy Cold
 * Sweat treats them as cooling/magical sources. They remain in the old local
 * modifier path until that behavior gets an explicit design decision.
 */
public final class RadiantHeatRegistry
{
    private static final Map<Block, Source> SOURCES =
            new IdentityHashMap<>();

    static
    {
        register(Blocks.CAMPFIRE, 5550.0, 35.0, false, false, RadiantHeatRegistry::lit);
        register(Blocks.BLAST_FURNACE, 1800.0, 30.0, false, false, RadiantHeatRegistry::lit);
        register(Blocks.LAVA, 7000.0, 60.0, true, true, state -> true);
        register(Blocks.LAVA_CAULDRON, 4000.0, 45.0, false, false, state -> true);
        register(Blocks.FIRE, 1300.0, 25.0, false, false, state -> true);
        register(Blocks.FURNACE, 1300.0, 12.0, false, false, RadiantHeatRegistry::litFurnace);
        register(Blocks.MAGMA_BLOCK, 1200.0, 8.0, false, false, state -> true);
        register(Blocks.SMOKER, 1100.0, 20.0, false, false, RadiantHeatRegistry::litFurnace);
        register(Blocks.TORCH, 350.0, 2.0, false, false, state -> true);
        register(Blocks.WALL_TORCH, 350.0, 2.0, false, false, state -> true);
        register(Blocks.LANTERN, 350.0, 2.0, false, false, state -> true);
    }

    private RadiantHeatRegistry()
    {
    }

    public static Optional<Source> get(BlockState state)
    {
        Source source = SOURCES.get(state.getBlock());
        if (source == null || !source.active().test(state))
        {
            return Optional.empty();
        }
        return Optional.of(source);
    }

    private static void register(
            Block block,
            double maxRadiation,
            double roomHeatPower,
            boolean fluidScaled,
            boolean strongestOnly,
            Predicate<BlockState> active
    )
    {
        SOURCES.put(
                block,
                new Source(
                        maxRadiation,
                        roomHeatPower,
                        fluidScaled,
                        strongestOnly,
                        active
                )
        );
    }

    private static boolean lit(BlockState state)
    {
        return !state.hasProperty(BlockStateProperties.LIT)
                || state.getValue(BlockStateProperties.LIT);
    }

    private static boolean litFurnace(BlockState state)
    {
        return state.hasProperty(AbstractFurnaceBlock.LIT)
                && state.getValue(AbstractFurnaceBlock.LIT);
    }

    public record Source(
            double maxRadiation,
            double roomHeatPower,
            boolean fluidScaled,
            boolean strongestOnly,
            Predicate<BlockState> active
    )
    {
        public Source
        {
            if (maxRadiation < 0.0
                    || roomHeatPower < 0.0)
            {
                throw new IllegalArgumentException(
                        "Heat-source strengths cannot be negative"
                );
            }
        }
    }
}
