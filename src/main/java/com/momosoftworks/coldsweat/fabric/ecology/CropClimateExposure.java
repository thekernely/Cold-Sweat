package com.momosoftworks.coldsweat.fabric.ecology;

import com.momosoftworks.coldsweat.api.temperature.modifier.BiomeTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.ElevationTempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.fabric.temperature.RoomThermalManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * M10.1 physical crop-climate foundation.
 *
 * <p>Ecliptic Seasons remains the authority for seasonal crop suitability and
 * humidity. This class answers a different question: what physical cold
 * environment is acting on a crop at this location right now?
 *
 * <p>No growth, stress, damage, or crop replacement happens here. M10.2+ will
 * consume this immutable sample so frost behavior can be layered on top of
 * Ecliptic's existing growth decision rather than replacing it.
 *
 * <p>The minecraft:crops block tag is the generic compatibility boundary.
 * Farmer's Delight Refabricated 26.2 already contributes its cultivated crops
 * (cabbage, onion, rice panicles, tomato, budding tomato, rope tomato) to that
 * tag, so this foundation requires no hard Farmer's Delight dependency.
 */
public final class CropClimateExposure
{
    private static final TagKey<Block> CROPS =
            TagKey.create(
                    Registries.BLOCK,
                    Identifier.withDefaultNamespace("crops")
            );

    /*
     * These bands are deliberately descriptive in M10.1. They do not alter
     * gameplay yet. Later growth/stress tuning can use them without repeatedly
     * rediscovering Celsius thresholds throughout the crop runtime.
     */
    private static final double MARGINAL_C = 5.0;
    private static final double FROST_C = 0.0;
    private static final double SEVERE_FROST_C = -8.0;

    private CropClimateExposure()
    {
    }

    public static Sample sample(
            ServerLevel level,
            BlockPos cropPos,
            BlockState cropState
    )
    {
        /*
         * Crop climate is intentionally point-local rather than the player's
         * broad 7x7 biome average. It still uses the exact M9 seasonal biome
         * envelope and day/night phase through BiomeTempModifier.
         */
        double outdoorMc =
                BiomeTempModifier.sampleLocalClimateAt(
                        level,
                        cropPos
                )
                        + ElevationTempModifier.getAltitudeOffset(
                                level,
                                cropPos
                        );

        double outdoorC =
                Temperature.convert(
                        outdoorMc,
                        Temperature.Units.MC,
                        Temperature.Units.C,
                        true
                );

        /*
         * If M9 already has a retained-room reservoir covering this position,
         * use its actual air temperature. M10.1 does not trigger a new flood
         * fill from every crop: that would be the wrong performance boundary.
         *
         * M10.2 will decide how crop checks populate/refresh room samples
         * independently of nearby players before this value drives gameplay.
         */
        double cachedRoomC =
                RoomThermalManager.getCachedRoomTemperatureC(
                        level,
                        cropPos
                );

        boolean retainedRoom =
                Double.isFinite(cachedRoomC);

        double localAirC =
                retainedRoom
                        ? cachedRoomC
                        : outdoorC;

        boolean meaningfulSky =
                level.dimensionType().hasSkyLight()
                        && !level.dimensionType().hasCeiling();

        boolean directSkyExposure =
                meaningfulSky
                        && level.canSeeSky(cropPos.above());

        /*
         * Roof/glass cover blocks direct frost exposure without pretending it
         * makes the air warm. A covered greenhouse can therefore still be cold
         * enough to stop growth; active room heat remains a separate mechanism.
         */
        boolean overheadProtection =
                meaningfulSky
                        && !directSkyExposure;

        boolean directFrostExposure =
                directSkyExposure
                        && localAirC <= FROST_C;

        return new Sample(
                cropState != null
                        && cropState.is(CROPS),
                outdoorC,
                localAirC,
                retainedRoom,
                directSkyExposure,
                overheadProtection,
                directFrostExposure,
                classify(localAirC)
        );
    }

    private static ThermalBand classify(double temperatureC)
    {
        if (temperatureC <= SEVERE_FROST_C)
        {
            return ThermalBand.SEVERE_FROST;
        }
        if (temperatureC <= FROST_C)
        {
            return ThermalBand.FROST;
        }
        if (temperatureC < MARGINAL_C)
        {
            return ThermalBand.MARGINAL;
        }
        return ThermalBand.COMFORTABLE;
    }

    public enum ThermalBand
    {
        COMFORTABLE,
        MARGINAL,
        FROST,
        SEVERE_FROST
    }

    public record Sample(
            boolean taggedCrop,
            double outdoorTemperatureC,
            double localAirTemperatureC,
            boolean retainedRoomTemperatureAvailable,
            boolean directSkyExposure,
            boolean overheadProtection,
            boolean directFrostExposure,
            ThermalBand thermalBand
    )
    {
    }
}
