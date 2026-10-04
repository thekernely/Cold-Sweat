package com.momosoftworks.coldsweat.common.capability.temperature;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.config.TemperatureDamageSettings;
import com.momosoftworks.coldsweat.core.init.ModEffects;
import com.momosoftworks.coldsweat.util.registries.ModDamageSources;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
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

        boolean tooHot =
                coreCelsius
                        >= TemperatureDamageSettings.HOT_DAMAGE_START_C;

        boolean tooCold =
                coreCelsius
                        <= TemperatureDamageSettings.COLD_DAMAGE_START_C;

        if (!tooHot && !tooCold)
        {
            return;
        }

        if (tooHot
                && player.hasEffect(MobEffects.FIRE_RESISTANCE)
                && TemperatureDamageSettings.FIRE_RESISTANCE_ENABLED)
        {
            return;
        }

        if (tooCold
                && player.hasEffect(ModEffects.ICE_RESISTANCE)
                && TemperatureDamageSettings.ICE_RESISTANCE_ENABLED)
        {
            return;
        }

        double resistance =
                Temperature.get(
                        player,
                        tooHot
                                ? Temperature.Trait.HEAT_RESISTANCE
                                : Temperature.Trait.COLD_RESISTANCE
                );

        resistance = clamp01(resistance);

        double damage =
                tooCold
                        ? coldDamage(coreCelsius)
                        : hotDamage(coreCelsius);

        damage *= 1.0 - resistance;

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
                                tooHot
                                        ? ModDamageSources.HOT
                                        : ModDamageSources.COLD
                        )
                );

        player.hurtServer(
                player.level(),
                source,
                (float) damage
        );
    }

    /**
     * Locked M7 cold-damage curve.
     *
     * 33 C  -> 0.020 HP/s  (barely measurable)
     * 32.5  -> ~0.032 HP/s
     * 32 C  -> 0.050 HP/s
     * 31 C  -> 0.125 HP/s  (few-minute survival scale)
     * 30 C  -> ~0.313 HP/s
     * 29 C  -> ~0.781 HP/s
     *
     * Damage is exponential rather than a linear cliff. At deep hypothermia
     * the cap prevents pathological values while still making survival rapidly
     * untenable.
     */
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

    /**
     * Heat is intentionally still the M7.12l placeholder curve. We are locking
     * cold first and will rebalance hyperthermia separately next.
     */
    private static double hotDamage(double coreCelsius)
    {
        double excess =
                Math.max(
                        0.0,
                        coreCelsius
                                - TemperatureDamageSettings.HOT_DAMAGE_START_C
                );

        double ramp =
                TemperatureDamageSettings.HOT_DAMAGE_RAMP_C > 0.0
                        ? clamp01(
                                excess
                                        / TemperatureDamageSettings.HOT_DAMAGE_RAMP_C
                        )
                        : 1.0;

        double smoothRamp =
                ramp * ramp * (3.0 - 2.0 * ramp);

        return lerp(
                TemperatureDamageSettings.HOT_MIN_DAMAGE,
                TemperatureDamageSettings.HOT_MAX_DAMAGE,
                smoothRamp
        );
    }

    private static double clamp01(double value)
    {
        return Math.max(
                0.0,
                Math.min(1.0, value)
        );
    }

    private static double lerp(
            double start,
            double end,
            double delta
    )
    {
        return start
                + (end - start)
                * delta;
    }

    private TemperatureDamageRuntime()
    {
    }
}
