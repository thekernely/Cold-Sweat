package com.momosoftworks.coldsweat.api.registry;

import com.momosoftworks.coldsweat.api.temperature.block_temp.BlockTemp;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fabric-native logical registry for block temperature sources.
 */
public final class BlockTempRegistry
{
    private static final List<BlockTemp> BLOCK_TEMPS =
            new ArrayList<>();

    private static final Map<Block, List<BlockTemp>> MAPPED_BLOCKS =
            new IdentityHashMap<>();

    public static final BlockTemp DEFAULT_BLOCK_TEMP = new BlockTemp()
    {
        @Override
        public double getTemperature(
                Level level,
                LivingEntity entity,
                BlockState state,
                BlockPos pos,
                double distance
        )
        {
            return 0.0;
        }
    };

    public static synchronized void register(BlockTemp blockTemp)
    {
        register(blockTemp, false);
    }

    public static synchronized void registerFirst(BlockTemp blockTemp)
    {
        register(blockTemp, true);
    }

    private static void register(BlockTemp blockTemp, boolean first)
    {
        if (blockTemp == null)
        {
            throw new IllegalArgumentException(
                    "BlockTemp cannot be null"
            );
        }

        if (first)
        {
            BLOCK_TEMPS.add(0, blockTemp);
        }
        else
        {
            BLOCK_TEMPS.add(blockTemp);
        }

        rebuildMappedBlocks();
    }

    private static void rebuildMappedBlocks()
    {
        MAPPED_BLOCKS.clear();

        for (BlockTemp blockTemp : BLOCK_TEMPS)
        {
            for (Block block : blockTemp.getAffectedBlocks())
            {
                MAPPED_BLOCKS
                        .computeIfAbsent(
                                block,
                                key -> new ArrayList<>()
                        )
                        .add(blockTemp);
            }
        }
    }

    public static synchronized void flush()
    {
        MAPPED_BLOCKS.clear();
        BLOCK_TEMPS.clear();
    }

    public static List<BlockTemp> getEntries()
    {
        return List.copyOf(BLOCK_TEMPS);
    }

    public static Collection<BlockTemp> getBlockTempsFor(BlockState state)
    {
        if (state.isAir())
        {
            return List.of(DEFAULT_BLOCK_TEMP);
        }

        Block block = state.getBlock();

        List<BlockTemp> cached = MAPPED_BLOCKS.get(block);
        if (cached != null && !cached.isEmpty())
        {
            return List.copyOf(cached);
        }

        LinkedHashSet<BlockTemp> matches = new LinkedHashSet<>();

        for (BlockTemp blockTemp : BLOCK_TEMPS)
        {
            if (blockTemp.matches(state))
            {
                matches.add(blockTemp);
            }
        }

        List<BlockTemp> resolved =
                matches.isEmpty()
                        ? List.of(DEFAULT_BLOCK_TEMP)
                        : List.copyOf(matches);

        MAPPED_BLOCKS.put(
                block,
                new ArrayList<>(resolved)
        );

        return resolved;
    }

    public static Optional<BlockTemp> getFirstBlockTempFor(
            BlockState state,
            Level level,
            BlockPos pos
    )
    {
        return getBlockTempsFor(state)
                .stream()
                .filter(temp -> temp.isValid(level, pos, state))
                .findFirst();
    }

    private BlockTempRegistry()
    {
    }
}
