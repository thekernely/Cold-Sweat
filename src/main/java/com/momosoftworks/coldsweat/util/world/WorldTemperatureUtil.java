package com.momosoftworks.coldsweat.util.world;

import com.momosoftworks.coldsweat.config.WorldTemperatureSettings;
import net.minecraft.world.level.Level;

/**
 * Small 26.2-safe subset of Cold Sweat's world-temperature helpers.
 *
 * The full WorldHelper class is still heavily loader/gameplay coupled, so M4
 * restores only temperature-critical math here and later folds it back into the
 * wider utility layer.
 */
public final class WorldTemperatureUtil
{
    public static final long DAY_LENGTH = 24000L;

    private WorldTemperatureUtil()
    {
    }

    /**
     * Cold Sweat's day/night multiplier:
     * +1 at the hottest point, -1 at the coldest point, with a cosine blend
     * between them. 26.2 replaced dayTime() with world clocks, so the overworld
     * clock is used here.
     */
    public static double getTimeMultiplier(Level level)
    {
        return getTimeMultiplier(
                level,
                WorldTemperatureSettings.getHottestTime(),
                WorldTemperatureSettings.getColdestTime()
        );
    }

    public static double getTimeMultiplier(
            Level level,
            long hottestTime,
            long coldestTime
    )
    {
        if (level.dimensionType().hasCeiling())
        {
            return 0.0;
        }

        /*
         * 26.2's fixed-time dimensions no longer expose the old fixedTime()
         * tick value on DimensionType. Until Cold Sweat's full dimension/config
         * bridge is restored, treat them as thermally time-neutral rather than
         * inventing a moving day/night cycle.
         */
        if (level.dimensionType().hasFixedTime())
        {
            return 0.0;
        }

        long time = Math.floorMod(level.getOverworldClockTime(), DAY_LENGTH);
        hottestTime = Math.floorMod(hottestTime, DAY_LENGTH);
        coldestTime = Math.floorMod(coldestTime, DAY_LENGTH);

        if (hottestTime == coldestTime)
        {
            return 0.0;
        }

        long coolingLength = Math.floorMod(
                coldestTime - hottestTime,
                DAY_LENGTH
        );
        long warmingLength = DAY_LENGTH - coolingLength;
        long fromHottest = Math.floorMod(time - hottestTime, DAY_LENGTH);

        double angle;
        if (fromHottest <= coolingLength)
        {
            double progress = fromHottest / (double) coolingLength;
            angle = progress * Math.PI;
        }
        else
        {
            long fromColdest = fromHottest - coolingLength;
            double progress = fromColdest / (double) warmingLength;
            angle = Math.PI + progress * Math.PI;
        }

        return Math.cos(angle);
    }
}
