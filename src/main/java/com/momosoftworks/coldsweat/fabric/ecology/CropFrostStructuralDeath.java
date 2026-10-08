package com.momosoftworks.coldsweat.fabric.ecology;

import com.momosoftworks.coldsweat.core.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-only M10.3b-b structural crop death. Hard dependency on Farmer's
 * Delight is intentionally avoided: its cultivated block IDs are optional.
 * Do not widen the death boundary to wild plants or column vegetation.
 */
public final class CropFrostStructuralDeath
{
    private static final Identifier BUDDING_TOMATO =
            Identifier.parse("farmersdelight:budding_tomatoes");
    private static final Identifier MATURE_TOMATO =
            Identifier.parse("farmersdelight:tomatoes");
    private static final Identifier ROPE_TOMATO =
            Identifier.parse("farmersdelight:tomatoes_on_rope");
    private static final Identifier CULTIVATED_RICE =
            Identifier.parse("farmersdelight:rice");
    private static final Identifier RICE_PANICLES =
            Identifier.parse("farmersdelight:rice_panicles");

    private static final int MAX_TOMATO_ROPE_SEGMENTS = 3;

    private CropFrostStructuralDeath() {}

    public static boolean isBuddingToMatureTomato(Block oldBlock, Block newBlock)
    {
        return BuiltInRegistries.BLOCK.getKey(oldBlock).equals(BUDDING_TOMATO)
                && BuiltInRegistries.BLOCK.getKey(newBlock).equals(MATURE_TOMATO);
    }

    /**
     * Cold Sweat already checks its frost-affected crop tag before calling us.
     * Structural parts (pitcher upper halves and rope tomato sections) never
     * independently turn into withered bushes.
     */
    public static boolean isEligibleRoot(ServerLevel level, BlockPos pos, BlockState crop)
    {
        if (isRopeTomato(crop)) return false;
        // Submerged cultivated rice is not a CropBlock and can grow on soil
        // beneath a source water block. Wild rice has a DIFFERENT ID and
        // remains excluded from Cold Sweat's frost tag/death semantics.
        if (isCultivatedRice(crop))
        {
            return level.getFluidState(pos).isSource();
        }
        if (crop.is(Blocks.PITCHER_CROP)
                && crop.hasProperty(DoublePlantBlock.HALF)
                && crop.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER)
        {
            return false;
        }

        BlockState ground = level.getBlockState(pos.below());
        if (!ground.is(Blocks.FARMLAND) && !ground.is(BlockTags.SUPPORTS_CROPS))
        {
            return false;
        }

        return crop.is(Blocks.PITCHER_CROP)
                || crop.getBlock() instanceof CropBlock
                || isBuddingTomato(crop);
    }

    /**
     * No destroyBlock(drop=true), no bonus seeds. Above-root cleanup precedes
     * death, preventing scheduled tomato-support cleanup from dropping fruit.
     * All affected positions share the same x/z chunk (no force loading).
     */
    public static boolean wither(ServerLevel level, BlockPos root, BlockState crop)
    {
        if (!isEligibleRoot(level, root, crop)) return false;
        if (level.getChunkSource().getChunkNow(root.getX() >> 4, root.getZ() >> 4) == null)
        {
            return false;
        }
        if (!level.getBlockState(root).is(crop.getBlock())) return false;

        if (isCultivatedRice(crop))
        {
            BlockPos above = root.above();
            BlockState panicles = level.getBlockState(above);
            if (isRicePanicles(panicles))
            {
                // Suppress intermediary shape updates so the panicles don't
                // break the submerged root or drop a harvest midway through.
                int flags = Block.UPDATE_CLIENTS
                        | Block.UPDATE_KNOWN_SHAPE
                        | Block.UPDATE_SUPPRESS_DROPS;
                if (!level.setBlock(above, Blocks.AIR.defaultBlockState(), flags)) return false;
                CropClimateRuntime.clearStress(level, above);
            }
            // The rice root contains a WATER source. Never replace it with
            // a dry bush or remove the water from the paddy.
            return level.setBlock(root, Blocks.WATER.defaultBlockState(),
                    Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        if (crop.is(Blocks.PITCHER_CROP))
        {
            BlockPos above = root.above();
            BlockState top = level.getBlockState(above);
            if (top.is(Blocks.PITCHER_CROP)
                    && top.hasProperty(DoublePlantBlock.HALF)
                    && top.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER)
            {
                // Removing the upper half with UPDATE_ALL (3) immediately
                // notifies the lower half. DoublePlantBlock.updateShape can
                // then destroy the lower half before it is withered, sometimes
                // dropping a pitcher item. Temporarily omit neighbour/shape
                // updates until the root has been replaced deliberately.
                int upperRemovalFlags = Block.UPDATE_CLIENTS
                        | Block.UPDATE_KNOWN_SHAPE
                        | Block.UPDATE_SUPPRESS_DROPS;
                if (!level.setBlock(above, Blocks.AIR.defaultBlockState(), upperRemovalFlags)) return false;
                CropClimateRuntime.clearStress(level, above);
            }
        }
        else if (isMatureTomato(crop) || isBuddingTomato(crop))
        {
            List<BlockPos> vines = new ArrayList<>();
            for (int i = 1; i <= MAX_TOMATO_ROPE_SEGMENTS; i++)
            {
                BlockPos above = root.above(i);
                if (!isRopeTomato(level.getBlockState(above))) break;
                vines.add(above);
            }
            for (int i = vines.size() - 1; i >= 0; i--)
            {
                BlockPos vinePos = vines.get(i);
                if (isRopeTomato(level.getBlockState(vinePos)))
                {
                    // Farmer's Delight 26.2 restores support rope through its
                    // Level.removeBlock hook when rope permanence is enabled.
                    // setBlock(AIR) would bypass that compatibility hook.
                    level.removeBlock(vinePos, false);
                    if (isRopeTomato(level.getBlockState(vinePos))) return false;
                }
                CropClimateRuntime.clearStress(level, vinePos);
            }
        }

        // The root must remain present until the deliberate conversion.
        // All ordinary neighbour updates are safe once the upper half is gone.
        if (!level.getBlockState(root).is(crop.getBlock())) return false;

        // No produce or seeds may be emitted by the frost-death transition.
        return level.setBlock(root, ModBlocks.WITHERED_CROP.defaultBlockState(),
                Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
    }

    private static boolean isCultivatedRice(BlockState state)
    {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(CULTIVATED_RICE);
    }

    private static boolean isRicePanicles(BlockState state)
    {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(RICE_PANICLES);
    }

    private static boolean isBuddingTomato(BlockState state)
    {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(BUDDING_TOMATO);
    }

    private static boolean isMatureTomato(BlockState state)
    {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(MATURE_TOMATO);
    }

    private static boolean isRopeTomato(BlockState state)
    {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(ROPE_TOMATO);
    }
}
