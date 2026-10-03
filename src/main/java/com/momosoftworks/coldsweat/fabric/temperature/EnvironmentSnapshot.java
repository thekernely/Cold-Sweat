package com.momosoftworks.coldsweat.fabric.temperature;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

/**
 * Cached server-side description of the environment currently acting on one
 * temperature-enabled entity.
 *
 * M7.12f-a is a behavior-neutral foundation. The existing thermal pipeline is
 * copied into this object exactly as-is, while the spatial portion is marked
 * unavailable. Later M7.12f slices can populate shelter, underground state,
 * water volume, and radiant load from one unified low-frequency world scan
 * without changing every downstream consumer again.
 *
 * All temperature values use Cold Sweat's canonical Minecraft temperature unit.
 */
public record EnvironmentSnapshot(
        double ambientClimate,
        double localThermalLoad,
        double exposureDelta,
        double effectiveTemperature,
        SpatialState spatial,
        RoomThermalState room,
        BlockPos origin,
        long capturedGameTime
)
{
    public EnvironmentSnapshot
    {
        spatial = spatial != null
                ? spatial
                : SpatialState.unavailable();

        room = room != null
                ? room
                : RoomThermalState.unavailable();

        origin = origin != null
                ? origin.immutable()
                : BlockPos.ZERO;
    }

    /**
     * Build a snapshot from the currently-live M7.12e modifier pipeline.
     *
     * No spatial query is performed here. That is deliberate: this slice only
     * introduces the cache contract so behavior can remain identical.
     */
    public static EnvironmentSnapshot fromCurrentPipeline(
            LivingEntity entity,
            double ambientClimate,
            double afterLocalSources,
            double effectiveTemperature
    )
    {
        return fromCurrentPipeline(
                entity,
                ambientClimate,
                afterLocalSources,
                effectiveTemperature,
                SpatialState.unavailable(),
                RoomThermalState.unavailable()
        );
    }

    public static EnvironmentSnapshot fromCurrentPipeline(
            LivingEntity entity,
            double ambientClimate,
            double afterLocalSources,
            double effectiveTemperature,
            SpatialState spatial
    )
    {
        return fromCurrentPipeline(
                entity,
                ambientClimate,
                afterLocalSources,
                effectiveTemperature,
                spatial,
                RoomThermalState.unavailable()
        );
    }

    public static EnvironmentSnapshot fromCurrentPipeline(
            LivingEntity entity,
            double ambientClimate,
            double afterLocalSources,
            double effectiveTemperature,
            SpatialState spatial,
            RoomThermalState room
    )
    {
        return new EnvironmentSnapshot(
                ambientClimate,
                afterLocalSources - ambientClimate,
                effectiveTemperature - afterLocalSources,
                effectiveTemperature,
                spatial,
                room,
                entity.blockPosition(),
                entity.level().getGameTime()
        );
    }

    /**
     * Compatibility adapter for systems still consuming the M7.12 thermal DTO.
     */
    public ThermalEnvironment thermalEnvironment()
    {
        return new ThermalEnvironment(
                ambientClimate,
                localThermalLoad,
                exposureDelta,
                effectiveTemperature
        );
    }

    public double totalEnvironmentalDelta()
    {
        return localThermalLoad + exposureDelta;
    }

    public double recomposedTemperature()
    {
        return ambientClimate + totalEnvironmentalDelta();
    }

    /**
     * Spatial measurements produced by the upcoming unified environment scan.
     *
     * "available" prevents placeholder false/zero values from being mistaken
     * for real measurements during the migration.
     *
     * radiantLoad is intentionally not an air-temperature delta. M7.12f-c
     * gives it its own radiation-like unit, separate from Cold Sweat's MC/C/F
     * temperature units. Later body/skin logic can therefore react strongly to
     * a nearby fire without pretending that the surrounding air itself jumped
     * by the same number of degrees.
     */
    public record SpatialState(
            boolean available,
            boolean sheltered,
            boolean underground,
            double skyExposure,
            double waterVolume,
            double radiantLoad,
            int scannedBlocks,
            int radiantSourceBlocks
    )
    {
        private static final SpatialState UNAVAILABLE =
                new SpatialState(
                        false,
                        false,
                        false,
                        0.0,
                        0.0,
                        0.0,
                        0,
                        0
                );

        public static SpatialState unavailable()
        {
            return UNAVAILABLE;
        }

        public static SpatialState measured(
                boolean sheltered,
                boolean underground,
                double skyExposure,
                double waterVolume,
                double radiantLoad,
                int scannedBlocks,
                int radiantSourceBlocks
        )
        {
            return new SpatialState(
                    true,
                    sheltered,
                    underground,
                    clamp01(skyExposure),
                    clamp01(waterVolume),
                    Math.max(0.0, radiantLoad),
                    Math.max(0, scannedBlocks),
                    Math.max(0, radiantSourceBlocks)
            );
        }

        private static double clamp01(double value)
        {
            return Math.max(
                    0.0,
                    Math.min(1.0, value)
            );
        }
    }
}
