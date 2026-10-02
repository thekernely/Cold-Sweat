package com.momosoftworks.coldsweat.api.temperature.modifier;

import com.momosoftworks.coldsweat.api.registry.BlockTempRegistry;
import com.momosoftworks.coldsweat.api.temperature.block_temp.BlockTemp;
import com.momosoftworks.coldsweat.api.util.Temperature;
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
 * This restores the core upstream behavior needed by registered BlockTemps:
 * nearby source scanning, distance fading, per-source effect caps, temperature
 * caps, and logarithmic diminishing returns.
 *
 * Upstream's ray-based obstruction attenuation, group caps, chunk/state caches,
 * and advancement hooks are restored in later slices.
 */
public class BlockTempModifier extends TempModifier
{
    private static final double LOG_FACTOR = 0.52;
    private static final int DEFAULT_RANGE = 7;

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
                (distance - 0.5) / Math.max(0.0001, range - 0.5);

        return 1.0 - clamp(progress, 0.0, 1.0);
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

        private double totalEffect;

        private BlockEffectAccumulator(
                double minEffect,
                double maxEffect,
                double minTemperature,
                double maxTemperature,
                boolean logarithmic
        )
        {
            this.minEffect = minEffect;
            this.maxEffect = maxEffect;
            this.minTemperature = minTemperature;
            this.maxTemperature = maxTemperature;
            this.logarithmic = logarithmic;
        }

        private void add(double amount)
        {
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
