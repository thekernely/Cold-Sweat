package com.momosoftworks.coldsweat.common.capability.temperature;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.config.TemperatureDamageSettings;
import com.momosoftworks.coldsweat.core.init.ModEffects;
import com.momosoftworks.coldsweat.fabric.temperature.SurfaceTemperatureRuntime;
import com.momosoftworks.coldsweat.util.registries.ModDamageSources;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffects;

public final class TemperatureDamageRuntime
{
    private static boolean initialized;

    public static void initialize()
    {
        if (initialized)
        {
            return;
        }
        initialized = true;

        ServerTickEvents.END_SERVER_TICK.register(server ->
        {
            for (ServerPlayer player
                    : server.getPlayerList().getPlayers())
            {
                tickPlayer(player);
            }
        });
    }

    private static void tickPlayer(ServerPlayer player)
    {
        /*
         * Surface state is still updated on non-damage ticks so the fast layer
         * remains smooth even though actual injury pulses only once per second.
         */
        double surfaceCelsius =
                SurfaceTemperatureRuntime
                        .updateAndGet(player)
                        .surfaceCelsius();

        int interval =
                TemperatureDamageSettings.DAMAGE_INTERVAL;

        if (interval < 1
                || player.tickCount % interval != 0
                || player.level().getDifficulty() == Difficulty.PEACEFUL
                || player.isCreative()
                || player.isSpectator()
                || player.hasEffect(ModEffects.GRACE))
        {
            return;
        }

        double bodyStress =
                Temperature.get(
                        player,
                        Temperature.Trait.BODY
                );

        double coreCelsius =
                TemperatureRuntime.bodyStressToCelsius(
                        bodyStress
                );

        boolean fireResistant =
                player.hasEffect(MobEffects.FIRE_RESISTANCE)
                        && TemperatureDamageSettings.FIRE_RESISTANCE_ENABLED;

        boolean iceResistant =
                player.hasEffect(ModEffects.ICE_RESISTANCE)
                        && TemperatureDamageSettings.ICE_RESISTANCE_ENABLED;

        if (coreCelsius
                <= TemperatureDamageSettings.COLD_DAMAGE_START_C
                && !iceResistant)
        {
            double coldResistance =
                    clamp01(
                            Temperature.get(
                                    player,
                                    Temperature.Trait.COLD_RESISTANCE
                            )
                    );

            hurt(
                    player,
                    ModDamageSources.COLD,
                    coldDamage(coreCelsius)
                            * (1.0 - coldResistance)
            );
        }

        if (!fireResistant)
        {
            double heatDamage = 0.0;

            /*
             * Systemic hyperthermia remains tied to CORE/BODY Celsius and Cold
             * Sweat's HEAT_RESISTANCE trait.
             */
            if (coreCelsius
                    >= TemperatureDamageSettings.HOT_DAMAGE_START_C)
            {
                double heatResistance =
                        clamp01(
                                Temperature.get(
                                        player,
                                        Temperature.Trait.HEAT_RESISTANCE
                                )
                        );

                heatDamage +=
                        hotDamage(coreCelsius)
                                * (1.0 - heatResistance);
            }

            /*
             * Surface/scalding injury is separate. HEAT_RESISTANCE protects
             * systemic thermal stress; it does not make exposed skin immune to
             * an intense radiant source. Fire Resistance suppresses this path.
             *
             * M7 reuses the existing HOT damage type. A dedicated scalding
             * damage type/death message can be added later without changing
             * this physiology.
             */
            heatDamage +=
                    scaldingDamage(
                            surfaceCelsius
                    );

            hurt(
                    player,
                    ModDamageSources.HOT,
                    heatDamage
            );
        }
    }

    private static double coldDamage(double coreCelsius)
    {
        double degreesBelow =
                Math.max(
                        0.0,
                        TemperatureDamageSettings.COLD_DAMAGE_START_C
                                - coreCelsius
                );

        double damage =
                TemperatureDamageSettings.COLD_BASE_DAMAGE
                        * Math.pow(
                                TemperatureDamageSettings.COLD_DAMAGE_MULTIPLIER_PER_C,
                                degreesBelow
                        );

        return Math.min(
                TemperatureDamageSettings.COLD_MAX_DAMAGE,
                damage
        );
    }

    private static double hotDamage(double coreCelsius)
    {
        double degreesAbove =
                Math.max(
                        0.0,
                        coreCelsius
                                - TemperatureDamageSettings.HOT_DAMAGE_START_C
                );

        double damage =
                TemperatureDamageSettings.HOT_BASE_DAMAGE
                        * Math.pow(
                                TemperatureDamageSettings.HOT_DAMAGE_MULTIPLIER_PER_C,
                                degreesAbove
                        );

        return Math.min(
                TemperatureDamageSettings.HOT_MAX_DAMAGE,
                damage
        );
    }

    private static double scaldingDamage(
            double surfaceCelsius
    )
    {
        if (surfaceCelsius
                <= TemperatureDamageSettings.SCALDING_DAMAGE_START_C)
        {
            return 0.0;
        }

        double range =
                TemperatureDamageSettings.SCALDING_DAMAGE_FULL_C
                        - TemperatureDamageSettings.SCALDING_DAMAGE_START_C;

        double normalized =
                range > 0.0
                        ? clamp01(
                                (surfaceCelsius
                                        - TemperatureDamageSettings
                                                .SCALDING_DAMAGE_START_C)
                                        / range
                        )
                        : 1.0;

        double factor =
                normalized
                        * normalized
                        * (3.0 - 2.0 * normalized);

        return TemperatureDamageSettings.SCALDING_MAX_DAMAGE
                * factor;
    }

    private static void hurt(
            ServerPlayer player,
            ResourceKey<DamageType> damageType,
            double damage
    )
    {
        if (damage <= 0.0)
        {
            return;
        }

        Registry<DamageType> damageTypes =
                player.level()
                        .registryAccess()
                        .lookupOrThrow(
                                Registries.DAMAGE_TYPE
                        );

        DamageSource source =
                new DamageSource(
                        damageTypes.getOrThrow(
                                damageType
                        )
                );

        player.hurtServer(
                player.level(),
                source,
                (float) damage
        );
    }

    private static double clamp01(double value)
    {
        return Math.max(
                0.0,
                Math.min(1.0, value)
        );
    }

    private TemperatureDamageRuntime()
    {
    }
}
