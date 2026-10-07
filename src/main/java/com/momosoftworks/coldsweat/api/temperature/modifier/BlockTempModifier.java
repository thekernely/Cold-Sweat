package com.momosoftworks.coldsweat.api.temperature.modifier;

import com.momosoftworks.coldsweat.api.registry.BlockTempRegistry;
import com.momosoftworks.coldsweat.api.temperature.block_temp.BlockTemp;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.fabric.temperature.RadiantHeatRegistry;
import com.momosoftworks.coldsweat.core.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Fabric block-temperature scanner.
 *
 * M7.12 keeps the existing correctness-first cube scan but improves the
 * physical behavior of sources without adding a second scan:
 * - distance fading uses a gentler quadratic falloff
 * - only actual registered temperature-source candidates receive a short
 *   obstruction ray
 * - obstruction checks stop after a small number of blockers
 *
 * Broader chunk/state caching still belongs to the performance work that
 * naturally follows once the corrected thermal model is stable.
 */
public class BlockTempModifier extends TempModifier
{
    private static final double LOG_FACTOR = 0.52;
    private static final int DEFAULT_RANGE = 7;

    /*
     * A source at seven blocks never needs an excessively fine ray. Two
     * samples per block is sufficient to identify ordinary Minecraft walls,
     * while the blocker cap prevents pathological rays from doing pointless
     * work through thick structures.
     */
    private static final double OBSTRUCTION_STEPS_PER_BLOCK = 2.0;
    private static final int MAX_OBSTRUCTION_BLOCKS = 4;

    private final int rangeOverride;

    public BlockTempModifier()
    {
        this(-1);
    }

    public BlockTempModifier(int range)
    {
        this.rangeOverride = range;
    }

    @Override
    protected Function<Double, Double> calculate(
            LivingEntity entity,
            Temperature.Trait trait
    )
    {
        Level level = entity.level();
        BlockPos center = entity.blockPosition();

        int range = rangeOverride > 0
                ? rangeOverride
                : DEFAULT_RANGE;

        Map<BlockTemp, BlockEffectAccumulator> totals =
                new LinkedHashMap<>();

        Vec3 entityCenter = entity.getBoundingBox().getCenter();

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = -range; x <= range; x++)
        {
            for (int y = -range; y <= range; y++)
            {
                for (int z = -range; z <= range; z++)
                {
                    cursor.set(
                            center.getX() + x,
                            center.getY() + y,
                            center.getZ() + z
                    );

                    if (!level.isInWorldBounds(cursor))
                    {
                        continue;
                    }

                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir())
                    {
                        continue;
                    }

                    /*
                     * Normal radiant heat is owned by the unified environment
                     * snapshot from M7.12f-d onward. Do not also convert these
                     * same blocks into direct WORLD-temperature deltas here.
                     *
                     * M9.3c extends that ownership rule to Cold Sweat thermal
                     * machines. Boiler/Hearth/Icebox now heat or cool retained
                     * room air through RoomThermalManager; treating the machine
                     * block itself as a legacy local source would double-count
                     * it and make the displayed temperature snap instantly when
                     * the machine toggles.
                     *
                     * Non-radiant/magical sources such as soul fire and the
                     * dimension-dependent Nether portal continue through the
                     * legacy block-temperature path for now.
                     */
                    if (RadiantHeatRegistry.get(state).isPresent()
                            || state.is(ModBlocks.BOILER)
                            || state.is(ModBlocks.ICEBOX)
                            || state.is(ModBlocks.HEARTH_BOTTOM)
                            || state.is(ModBlocks.HEARTH_TOP))
                    {
                        continue;
                    }

                    Collection<BlockTemp> blockTemps =
                            BlockTempRegistry.getBlockTempsFor(state);

                    if (blockTemps.size() == 1
                            && blockTemps.contains(
                                    BlockTempRegistry.DEFAULT_BLOCK_TEMP
                            ))
                    {
                        continue;
                    }

                    Vec3 blockCenter = Vec3.atCenterOf(cursor);
                    double distance = entityCenter.distanceTo(blockCenter);

                    for (BlockTemp blockTemp : blockTemps)
                    {
                        if (!blockTemp.isValid(level, cursor, state))
                        {
                            continue;
                        }

                        double sourceRange =
                                blockTemp.getRange(
                                        entity,
                                        level,
                                        cursor,
                                        state
                                );

                        if (distance > sourceRange)
                        {
                            continue;
                        }

                        double sourceTemperature =
                                blockTemp.getTemperature(
                                        level,
                                        entity,
                                        state,
                                        cursor,
                                        distance
                                );

                        if (Double.compare(sourceTemperature, 0.0) == 0)
                        {
                            continue;
                        }

                        if (blockTemp.fades(
                                entity,
                                level,
                                cursor,
                                state
                        ))
                        {
                            sourceTemperature *= fadeFactor(
                                    distance,
                                    sourceRange
                            );
                        }

                        /*
                         * The source has already passed all cheap validity and
                         * range checks. Only now pay for an obstruction ray.
                         */
                        sourceTemperature *= obstructionFactor(
                                level,
                                entityCenter,
                                blockCenter,
                                cursor
                        );

                        if (Math.abs(sourceTemperature) < 1.0e-12)
                        {
                            continue;
                        }

                        BlockEffectAccumulator accumulator =
                                totals.computeIfAbsent(
                                        blockTemp,
                                        key -> new BlockEffectAccumulator(
                                                key.getMinEffect(
                                                        entity,
                                                        level,
                                                        cursor,
                                                        state
                                                ),
                                                key.getMaxEffect(
                                                        entity,
                                                        level,
                                                        cursor,
                                                        state
                                                ),
                                                key.getMinTemp(
                                                        entity,
                                                        level,
                                                        cursor,
                                                        state
                                                ),
                                                key.getMaxTemp(
                                                        entity,
                                                        level,
                                                        cursor,
                                                        state
                                                ),
                                                key.isLogarithmic(
                                                        entity,
                                                        level,
                                                        cursor,
                                                        state
                                                ),
                                                key.usesStrongestSource(
                                                        entity,
                                                        level,
                                                        cursor,
                                                        state
                                                )
                                        )
                                );

                        accumulator.add(sourceTemperature);
                    }
                }
            }
        }

        return temperature ->
        {
            double result = temperature;

            for (BlockEffectAccumulator accumulator : totals.values())
            {
                double minTemperature = Math.min(
                        accumulator.minTemperature,
                        accumulator.maxTemperature
                );
                double maxTemperature = Math.max(
                        accumulator.minTemperature,
                        accumulator.maxTemperature
                );

                if (result < minTemperature
                        || result > maxTemperature)
                {
                    continue;
                }

                result = clamp(
                        result + accumulator.totalEffect,
                        minTemperature,
                        maxTemperature
                );
            }

            return result;
        };
    }

    /**
     * Quadratic falloff preserves full strength at the source while making
     * medium-distance radiant influence noticeably less dominant than the old
     * linear interpolation. The contribution still reaches exactly zero at
     * the configured source range.
     */
    private static double fadeFactor(double distance, double range)
    {
        if (range <= 0.0)
        {
            return distance <= 0.5 ? 1.0 : 0.0;
        }

        if (distance <= 0.5)
        {
            return 1.0;
        }

        double progress =
                (distance - 0.5)
                        / Math.max(0.0001, range - 0.5);

        double remaining =
                1.0 - clamp(progress, 0.0, 1.0);

        return remaining * remaining;
    }

    /**
     * Approximate radiant obstruction between the entity and one real thermal
     * source. One ordinary solid wall halves the contribution, two reduce it
     * to one third, and so on. We cap blocker counting because after several
     * walls the remaining contribution is already small and extra ray work
     * would not improve gameplay meaningfully.
     */
    private static double obstructionFactor(
            Level level,
            Vec3 start,
            Vec3 end,
            BlockPos sourcePos
    )
    {
        double distance = start.distanceTo(end);
        if (distance <= 0.5)
        {
            return 1.0;
        }

        int steps = Math.max(
                1,
                (int) Math.ceil(
                        distance * OBSTRUCTION_STEPS_PER_BLOCK
                )
        );

        BlockPos.MutableBlockPos rayPos =
                new BlockPos.MutableBlockPos();

        long lastPos = Long.MIN_VALUE;
        int blockers = 0;

        for (int step = 1; step < steps; step++)
        {
            double progress = step / (double) steps;

            double x = start.x + (end.x - start.x) * progress;
            double y = start.y + (end.y - start.y) * progress;
            double z = start.z + (end.z - start.z) * progress;

            rayPos.set(
                    (int) Math.floor(x),
                    (int) Math.floor(y),
                    (int) Math.floor(z)
            );

            if (rayPos.getX() == sourcePos.getX()
                    && rayPos.getY() == sourcePos.getY()
                    && rayPos.getZ() == sourcePos.getZ())
            {
                continue;
            }

            long packed = rayPos.asLong();
            if (packed == lastPos)
            {
                continue;
            }
            lastPos = packed;

            if (level.getBlockState(rayPos).isSolidRender())
            {
                blockers++;
                if (blockers >= MAX_OBSTRUCTION_BLOCKS)
                {
                    break;
                }
            }
        }

        return 1.0 / (blockers + 1.0);
    }

    private static double clamp(
            double value,
            double min,
            double max
    )
    {
        /*
         * Match Cold Sweat's CSMath range semantics: callers are allowed to
         * provide reversed bounds and they are normalized before comparison.
         */
        if (min > max)
        {
            double swap = min;
            min = max;
            max = swap;
        }

        return Math.max(min, Math.min(max, value));
    }

    private static final class BlockEffectAccumulator
    {
        private final double minEffect;
        private final double maxEffect;
        private final double minTemperature;
        private final double maxTemperature;
        private final boolean logarithmic;
        private final boolean strongestSource;

        private double totalEffect;

        private BlockEffectAccumulator(
                double minEffect,
                double maxEffect,
                double minTemperature,
                double maxTemperature,
                boolean logarithmic,
                boolean strongestSource
        )
        {
            this.minEffect = minEffect;
            this.maxEffect = maxEffect;
            this.minTemperature = minTemperature;
            this.maxTemperature = maxTemperature;
            this.logarithmic = logarithmic;
            this.strongestSource = strongestSource;
        }

        private void add(double amount)
        {
            if (strongestSource)
            {
                if (Math.abs(amount) > Math.abs(totalEffect))
                {
                    totalEffect = clamp(
                            amount,
                            minEffect,
                            maxEffect
                    );
                }
                return;
            }

            if (!logarithmic)
            {
                totalEffect = clamp(
                        totalEffect + amount,
                        minEffect,
                        maxEffect
                );
                return;
            }

            if (amount > 0.0)
            {
                double positive =
                        Math.max(0.0, totalEffect);

                double newPositive =
                        Math.pow(
                                Math.pow(
                                        positive,
                                        1.0 / LOG_FACTOR
                                ) + amount,
                                LOG_FACTOR
                        );

                totalEffect = clamp(
                        newPositive,
                        minEffect,
                        maxEffect
                );
            }
            else if (amount < 0.0)
            {
                double negativeMagnitude =
                        Math.max(0.0, -totalEffect);

                double newMagnitude =
                        Math.pow(
                                Math.pow(
                                        negativeMagnitude,
                                        1.0 / LOG_FACTOR
                                ) + Math.abs(amount),
                                LOG_FACTOR
                        );

                totalEffect = clamp(
                        -newMagnitude,
                        minEffect,
                        maxEffect
                );
            }
        }
    }
}
