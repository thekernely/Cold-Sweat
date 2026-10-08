package com.momosoftworks.coldsweat.fabric.ecology;

import com.momosoftworks.coldsweat.api.temperature.modifier.BiomeTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.ElevationTempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.data.tag.ModBlockTags;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Physical crop-climate sample.
 *
 * <p>Ecliptic Seasons remains the authority for seasonal crop suitability and
 * humidity. Cold Sweat answers a separate question: what physical cold
 * environment is acting on a crop at this location right now?
 *
 * <p>M10.2a consumes this sample to slow/stop natural growth and accumulate
 * frost stress. It still does not kill or replace crops.
 */
public final class CropClimateExposure
{
    private static final double MARGINAL_C = 5.0;
    private static final double FROST_C = 0.0;
    private static final double SEVERE_FROST_C = -8.0;

    private CropClimateExposure()
    {
    }

    public static boolean isAffectedCrop(BlockState state)
    {
        return state != null
                && state.is(ModBlockTags.FROST_AFFECTED_CROPS);
    }

    public static Sample sample(
            ServerLevel level,
            BlockPos cropPos,
            BlockState cropState
    )
    {
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
         * Crop-side room observations wake the shared M9 room reservoir
         * without a player present. Open crops fall back to outdoor air; no
         * room flood fill is performed for each random tick.
         */
        /*
         * Water-rooted crops (notably Farmer's Delight rice) are planted
         * in a water source, whereas our retained room volume is connected
         * passable air. Their root block can sit one Y below the greenhouse
         * RoomKey even though the leaves share its warm air. Probe the air
         * just above the water, but keep outdoor climate and stress at the
         * real root position. Never step through a solid/water ceiling.
         */
        BlockPos roomAirProbe = cropPos;
        if (level.getFluidState(cropPos).is(FluidTags.WATER))
        {
            BlockPos above = cropPos.above();
            if (level.isInWorldBounds(above)
                    && level.hasChunkAt(above)
                    && level.getFluidState(above).isEmpty()
                    && level.getBlockState(above).getCollisionShape(level, above).isEmpty())
            {
                roomAirProbe = above;
            }
        }

        double cachedRoomC =
                CropRoomClimateService.sampleAirC(
                        level,
                        roomAirProbe,
                        outdoorMc
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
                        && !CropRoomClimateService.hasPhysicalOverhead(level, cropPos)
                        && level.canSeeSky(cropPos.above());

        /*
         * Cover blocks direct radiative/frost exposure, but does not invent
         * warmth. A covered crop can therefore avoid the strongest frost-stress
         * accumulation while still refusing to grow if the enclosed air itself
         * is below freezing.
         */
        boolean overheadProtection =
                meaningfulSky
                        && !directSkyExposure;

        boolean directFrostExposure =
                directSkyExposure
                        && localAirC <= FROST_C;

        return new Sample(
                isAffectedCrop(cropState),
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
            boolean affectedCrop,
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
