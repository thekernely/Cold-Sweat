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
        BlockPos origin,
        long capturedGameTime
)
{
    public EnvironmentSnapshot
    {
        spatial = spatial != null
                ? spatial
                : SpatialState.unavailable();

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
        return new EnvironmentSnapshot(
                ambientClimate,
                afterLocalSources - ambientClimate,
                effectiveTemperature - afterLocalSources,
                effectiveTemperature,
                SpatialState.unavailable(),
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
     * radiantLoad is intentionally not expressed in Celsius. It is reserved as
     * a separate thermal-radiation signal so later body/skin logic does not
     * have to pretend every heat source directly changes air temperature.
     */
    public record SpatialState(
            boolean available,
            boolean sheltered,
            boolean underground,
            double waterVolume,
            double radiantLoad
    )
    {
        private static final SpatialState UNAVAILABLE =
                new SpatialState(
                        false,
                        false,
                        false,
                        0.0,
                        0.0
                );

        public static SpatialState unavailable()
        {
            return UNAVAILABLE;
        }

        public static SpatialState measured(
                boolean sheltered,
                boolean underground,
                double waterVolume,
                double radiantLoad
        )
        {
            return new SpatialState(
                    true,
                    sheltered,
                    underground,
                    clamp01(waterVolume),
                    Math.max(0.0, radiantLoad)
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
