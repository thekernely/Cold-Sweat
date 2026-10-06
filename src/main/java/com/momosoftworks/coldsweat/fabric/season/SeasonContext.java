package com.momosoftworks.coldsweat.fabric.season;

/**
 * Loader-safe seasonal metadata exposed to Cold Sweat climate code.
 *
 * <p>The Ecliptic climate value is deliberately preserved as raw metadata.
 * It is NOT Celsius and must not be added directly to Cold Sweat temperatures.
 */
public record SeasonContext(
        Source source,
        boolean active,
        int solarTermIndex,
        String solarTermId,
        int seasonIndex,
        String seasonId,
        float rawClimateChangeScalar
)
{
    public static final SeasonContext NONE = new SeasonContext(
            Source.NONE,
            false,
            -1,
            "NONE",
            -1,
            "NONE",
            0.0f
    );

    public boolean hasSeason()
    {
        return source != Source.NONE
                && active
                && solarTermIndex >= 0
                && seasonIndex >= 0;
    }

    public enum Source
    {
        NONE,
        ECLIPTIC_SEASONS
    }
}
