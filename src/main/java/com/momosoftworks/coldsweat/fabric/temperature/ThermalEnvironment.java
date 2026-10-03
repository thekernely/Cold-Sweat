package com.momosoftworks.coldsweat.fabric.temperature;

/**
 * Immutable breakdown of the thermal environment acting on one entity.
 *
 * M7.12 deliberately separates the underlying climate from local/exposure
 * contributions instead of treating WORLD as one opaque "air temperature".
 *
 * All values use Cold Sweat's canonical Minecraft temperature unit:
 * - ambientClimate is the smoothed climate baseline before nearby/local loads
 * - localSourceDelta is the net contribution from nearby radiant/local sources
 * - exposureDelta is the net contribution from direct exposure such as wetness
 * - effectiveTemperature is the final apparent/effective environment presented
 *   to the rest of the temperature runtime
 *
 * Later M7.12 slices may add wind/shelter/insulation inputs to the effective
 * calculation without changing the meaning of the stored WORLD trait.
 */
public record ThermalEnvironment(
        double ambientClimate,
        double localSourceDelta,
        double exposureDelta,
        double effectiveTemperature
)
{
    /**
     * Build a breakdown from three ordered runtime stages.
     *
     * @param ambientClimate baseline after climate modifiers
     * @param afterLocalSources temperature after local/radiant source modifiers
     * @param effectiveTemperature final temperature after direct exposure
     */
    public static ThermalEnvironment fromStages(
            double ambientClimate,
            double afterLocalSources,
            double effectiveTemperature
    )
    {
        return new ThermalEnvironment(
                ambientClimate,
                afterLocalSources - ambientClimate,
                effectiveTemperature - afterLocalSources,
                effectiveTemperature
        );
    }

    /**
     * Combined non-climate pressure currently acting on the entity.
     */
    public double totalEnvironmentalDelta()
    {
        return localSourceDelta + exposureDelta;
    }

    /**
     * Recompose the effective environment from its current components.
     * Useful as a cheap invariant/debug check while M7.12 is being integrated.
     */
    public double recomposedTemperature()
    {
        return ambientClimate + totalEnvironmentalDelta();
    }
}
