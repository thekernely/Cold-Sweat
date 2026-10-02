package com.momosoftworks.coldsweat.config;

/**
 * Fabric-side defaults for insulation behavior.
 *
 * This mirrors the upstream Item Settings default until the file-backed
 * configuration bridge is restored.
 */
public final class InsulationSettings
{
    /** Multiplier applied to armor insulation's RATE protection. */
    public static final double INSULATION_STRENGTH = 1.0;

    private InsulationSettings()
    {
    }
}
