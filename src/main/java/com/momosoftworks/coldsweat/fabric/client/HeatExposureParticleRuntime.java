package com.momosoftworks.coldsweat.fabric.client;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureRuntime;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

/**
 * Client-only immediate heat-exposure feedback.
 *
 * This intentionally follows the same separation used by the rest of M7/M8:
 * thermal simulation remains authoritative elsewhere, while the client reads
 * already-synchronized temperature traits and renders cheap cosmetic feedback.
 *
 * Particles key off WORLD relative to the player's BURNING_POINT rather than
 * CORE. Severe radiant exposure (lava, concentrated heat sources) therefore
 * becomes visible immediately instead of waiting for internal temperature to
 * rise into heatstroke territory.
 */
public final class HeatExposureParticleRuntime
{
    private static final int SAMPLE_INTERVAL_TICKS = 6;

    /*
     * The visual reaches full density roughly 40 C above the configured
     * burning threshold. This is presentation-only and does not modify any
     * thermal values or damage thresholds.
     */
    private static final double FULL_SEVERITY_EXCESS_C = 40.0;

    private HeatExposureParticleRuntime()
    {
    }

    public static void register()
    {
        ClientTickEvents.END_CLIENT_TICK.register(
                HeatExposureParticleRuntime::tick
        );
    }

    private static void tick(Minecraft minecraft)
    {
        LocalPlayer player = minecraft.player;

        if (player == null
                || minecraft.level == null
                || minecraft.isPaused()
                || player.isSpectator()
                || player.tickCount % SAMPLE_INTERVAL_TICKS != 0)
        {
            return;
        }

        double burningPointMc =
                Temperature.get(
                        player,
                        Temperature.Trait.BURNING_POINT
                );

        if (Math.abs(burningPointMc) < 1.0e-9)
        {
            burningPointMc =
                    TemperatureRuntime.DEFAULT_BURNING_POINT;
        }

        double environmentC =
                Temperature.convert(
                        Temperature.get(
                                player,
                                Temperature.Trait.WORLD
                        ),
                        Temperature.Units.MC,
                        Temperature.Units.C,
                        true
                );

        double burningPointC =
                Temperature.convert(
                        burningPointMc,
                        Temperature.Units.MC,
                        Temperature.Units.C,
                        true
                );

        double excessC =
                environmentC - burningPointC;

        if (excessC <= 0.0)
        {
            return;
        }

        double severity =
                clamp01(
                        excessC / FULL_SEVERITY_EXCESS_C
                );

        RandomSource random =
                player.getRandom();

        /*
         * Mild overheating produces an occasional warning wisp. Extreme
         * radiant heat becomes obvious, but remains far below particle-spam
         * density. At full severity this averages only a few wisps per second.
         */
        double spawnChance =
                0.08 + 0.72 * severity;

        if (random.nextDouble() > spawnChance)
        {
            return;
        }

        spawnHeatWisp(player, random);

        if (severity >= 0.75
                && random.nextDouble() < 0.35 * severity)
        {
            spawnHeatWisp(player, random);
        }
    }

    private static void spawnHeatWisp(
            LocalPlayer player,
            RandomSource random
    )
    {
        double x =
                player.getX()
                        + (random.nextDouble() - 0.5)
                        * player.getBbWidth()
                        * 0.80;

        double y =
                player.getY()
                        + player.getBbHeight()
                        * (0.35 + random.nextDouble() * 0.50);

        double z =
                player.getZ()
                        + (random.nextDouble() - 0.5)
                        * player.getBbWidth()
                        * 0.80;

        double xSpeed =
                (random.nextDouble() - 0.5) * 0.008;

        double ySpeed =
                0.012 + random.nextDouble() * 0.018;

        double zSpeed =
                (random.nextDouble() - 0.5) * 0.008;

        player.level().addParticle(
                ParticleTypes.WHITE_SMOKE,
                x,
                y,
                z,
                xSpeed,
                ySpeed,
                zSpeed
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
