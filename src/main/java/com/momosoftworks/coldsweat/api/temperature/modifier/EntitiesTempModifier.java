package com.momosoftworks.coldsweat.api.temperature.modifier;

import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Nearby-entity WORLD temperature contribution.
 *
 * This M4 implementation restores Cold Sweat's bundled vanilla entity
 * temperature sources without pulling the full datapack/config codec graph
 * forward:
 * - any burning entity: +15 F, 6 block range, affects itself
 * - fueled furnace minecart: +12 F, 4 block range
 *
 * The generic EntityTempData codec remains a later extensibility/config task.
 */
public class EntitiesTempModifier extends TempModifier
{
    private static final double BURNING_ENTITY_TEMP =
            Temperature.convert(
                    15.0,
                    Temperature.Units.F,
                    Temperature.Units.MC,
                    false
            );

    private static final double FURNACE_MINECART_TEMP =
            Temperature.convert(
                    12.0,
                    Temperature.Units.F,
                    Temperature.Units.MC,
                    false
            );

    @Override
    protected Function<Double, Double> calculate(
            LivingEntity affectedEntity,
            Temperature.Trait trait
    )
    {
        Level level = affectedEntity.level();

        AABB searchBounds =
                affectedEntity.getBoundingBox()
                        .inflate(8.0);

        List<Entity> nearby =
                level.getEntities(
                        (Entity) null,
                        searchBounds,
                        entity -> true
                );

        if (nearby.size() > 10)
        {
            nearby = nearby.subList(0, 10);
        }

        double totalEffect = 0.0;

        for (Entity source : nearby)
        {
            if (source.isOnFire())
            {
                totalEffect += calculateSourceEffect(
                        level,
                        source,
                        affectedEntity,
                        BURNING_ENTITY_TEMP,
                        6.0,
                        true
                );
            }

            if (source instanceof MinecartFurnace furnace
                    && furnace.getDefaultDisplayBlockState()
                            .getValue(FurnaceBlock.LIT))
            {
                totalEffect += calculateSourceEffect(
                        level,
                        source,
                        affectedEntity,
                        FURNACE_MINECART_TEMP,
                        4.0,
                        false
                );
            }
        }

        double finalEffect = totalEffect;
        return temperature -> temperature + finalEffect;
    }

    private static double calculateSourceEffect(
            Level level,
            Entity source,
            LivingEntity affectedEntity,
            double sourceTemperature,
            double range,
            boolean affectsSelf
    )
    {
        if (!affectsSelf && source == affectedEntity)
        {
            return 0.0;
        }

        double distance =
                source.distanceTo(affectedEntity);

        if (distance > range)
        {
            return 0.0;
        }

        double distanceFactor =
                clamp(
                        1.0 - distance / range,
                        0.0,
                        1.0
                );

        double effect =
                sourceTemperature * distanceFactor;

        int solidBlocks =
                countSolidBlocksBetween(
                        level,
                        source.getBoundingBox().getCenter(),
                        affectedEntity.getBoundingBox().getCenter()
                );

        return effect / (solidBlocks + 1.0);
    }

    /**
     * Upstream attenuates entity temperature once for every solid block along
     * the ray. Sample at three points per block, matching the resolution used
     * by its WorldHelper.forBlocksInRay(..., 3) call.
     */
    private static int countSolidBlocksBetween(
            Level level,
            Vec3 start,
            Vec3 end
    )
    {
        double distance = start.distanceTo(end);
        if (distance <= 0.0)
        {
            return 0;
        }

        int steps =
                Math.max(
                        1,
                        (int) Math.ceil(distance * 3.0)
                );

        Set<BlockPos> visited =
                new HashSet<>();

        int solidBlocks = 0;

        for (int step = 1; step < steps; step++)
        {
            double progress =
                    step / (double) steps;

            double x =
                    start.x + (end.x - start.x) * progress;
            double y =
                    start.y + (end.y - start.y) * progress;
            double z =
                    start.z + (end.z - start.z) * progress;

            BlockPos pos =
                    BlockPos.containing(x, y, z);

            if (!visited.add(pos))
            {
                continue;
            }

            if (level.getBlockState(pos).isSolidRender())
            {
                solidBlocks++;
            }
        }

        return solidBlocks;
    }

    private static double clamp(
            double value,
            double min,
            double max
    )
    {
        return Math.max(
                min,
                Math.min(max, value)
        );
    }
}
