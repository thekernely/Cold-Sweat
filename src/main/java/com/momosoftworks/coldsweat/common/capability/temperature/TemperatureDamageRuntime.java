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
                || player.level().getDifficulty()
                        == Difficulty.PEACEFUL
                || player.isCreative()
                || player.isSpectator()
                || player.hasEffect(ModEffects.GRACE))
        {
            return;
        }

        double bodyTemperature =
                Temperature.get(
                        player,
                        Temperature.Trait.BODY
                );

        boolean tooHot = bodyTemperature >= 100.0;
        boolean tooCold = bodyTemperature <= -100.0;

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

        resistance = Math.max(
                0.0,
                Math.min(1.0, resistance)
        );

        double damage =
                TemperatureDamageSettings.TEMPERATURE_DAMAGE
                        * (1.0 - resistance);

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

    private TemperatureDamageRuntime()
    {
    }
}
