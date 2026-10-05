package com.momosoftworks.coldsweat.fabric.hydration;

import com.momosoftworks.coldsweat.api.util.Hydration;
import com.momosoftworks.coldsweat.common.capability.handler.PlayerHydrationManager;
import com.momosoftworks.coldsweat.common.capability.hydration.HydrationData;
import com.momosoftworks.coldsweat.core.init.ModEffects;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * M8.3 hydration gameplay:
 *
 * - crouch + right-click source water with an empty main hand
 * - raw-water sip restores 2 hydration points
 * - each sip has a 40% contamination chance
 * - contamination applies/refreshed a 7-second Thirst effect
 * - Thirst feeds the normal hydration exhaustion pipeline
 *
 * This slice intentionally does not add baseline walking/sprinting/heat water
 * costs yet. Those resource couplings belong to M8.8.
 *
 * A full seven-second Thirst episode now creates 16.8 exhaustion: four
 * hydration points of loss plus residual debt when saturation is empty.
 * Because a raw sip restores two points, a contaminated sip is a real gamble
 * rather than an automatically profitable trade.
 */
public final class HydrationGameplayRuntime
{
    public static final double RAW_WATER_HYDRATION = 2.0;

    public static final double EXHAUSTION_THRESHOLD = 4.0;
    public static final double THIRST_EXHAUSTION_PER_TICK = 0.12;

    public static final int THIRST_DURATION_TICKS = 7 * 20;

    private static final float RAW_WATER_CONTAMINATION_CHANCE = 0.40F;
    private static final double DRINK_REACH = 4.5;
    private static final double WATER_SAMPLE_STEP = 0.20;
    private static final int EXHAUSTION_UPDATE_INTERVAL = 10;
    /*
     * De-duplicate only callbacks produced by the SAME game tick. M8.3
     * registers both UseBlock and UseItem so one physical click can otherwise
     * reach the shared handler twice, but an 8-tick cooldown incorrectly ate
     * legitimate rapid right-clicks. One tick preserves the duplicate guard
     * while allowing a fresh sip on every subsequent input tick.
     */
    private static final int DIRECT_DRINK_COOLDOWN_TICKS = 1;

    private static final Map<UUID, Long> LAST_DIRECT_DRINK_TICK =
            new HashMap<>();

    private HydrationGameplayRuntime()
    {
    }

    public static void initialize()
    {
        ColdSweatFabric.LOGGER.info(
                "Initializing Cold Sweat hydration drinking and exhaustion runtime."
        );

        UseBlockCallback.EVENT.register(
                (player, level, hand, hitResult) ->
                        tryDirectWaterDrink(
                                player,
                                level,
                                hand
                        )
        );

        UseItemCallback.EVENT.register(
                HydrationGameplayRuntime::tryDirectWaterDrink
        );

        ServerTickEvents.END_SERVER_TICK.register(server ->
        {
            for (ServerPlayer player :
                    server.getPlayerList().getPlayers())
            {
                if (player.tickCount
                        % EXHAUSTION_UPDATE_INTERVAL
                        == 0)
                {
                    tickHydrationExhaustion(player);
                }
            }
        });
    }

    private static InteractionResult tryDirectWaterDrink(
            Player player,
            Level level,
            InteractionHand hand
    )
    {
        if (hand != InteractionHand.MAIN_HAND
                || player.isSpectator()
                || player.isCreative()
                || !player.isShiftKeyDown()
                || !player.getItemInHand(hand).isEmpty()
                || Hydration.get(player) >= Hydration.MAX_HYDRATION - 1.0e-6)
        {
            return InteractionResult.PASS;
        }

        BlockPos sourceWaterPos =
                findLookedAtSourceWater(player, level);

        if (sourceWaterPos == null)
        {
            return InteractionResult.PASS;
        }

        /*
         * Match Homeostatic's direct-drinking sound path exactly: the vanilla
         * GENERIC_DRINK event is played locally from the targeted water block
         * at 0.4 volume and fixed 1.0 pitch. Do not stop/restart the previous
         * sample; rapid clicks are allowed to overlap naturally.
         *
         * Gameplay mutation remains server-authoritative below.
         */
        if (level.isClientSide())
        {
            level.playSound(
                    player,
                    sourceWaterPos,
                    SoundEvents.GENERIC_DRINK.value(),
                    SoundSource.PLAYERS,
                    0.4F,
                    1.0F
            );

            player.swing(hand);
            return InteractionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayer serverPlayer))
        {
            return InteractionResult.PASS;
        }

        long gameTime =
                level.getGameTime();

        Long lastDrink =
                LAST_DIRECT_DRINK_TICK.get(
                        serverPlayer.getUUID()
                );

        /*
         * This is only a same-tick de-duplication guard for the paired Fabric
         * callbacks. It is not a gameplay drinking cooldown.
         */
        if (lastDrink != null
                && gameTime - lastDrink
                < DIRECT_DRINK_COOLDOWN_TICKS)
        {
            return InteractionResult.SUCCESS;
        }

        LAST_DIRECT_DRINK_TICK.put(
                serverPlayer.getUUID(),
                gameTime
        );

        Hydration.add(
                serverPlayer,
                RAW_WATER_HYDRATION
        );

        if (serverPlayer.getRandom().nextFloat()
                < RAW_WATER_CONTAMINATION_CHANCE)
        {
            applyContamination(serverPlayer);
        }

        serverPlayer.swing(hand);

        return InteractionResult.SUCCESS;
    }

    /**
     * Empty-hand interaction normally ignores water's fluid shape, so sample
     * the player's sight line directly and stop at the first real collision.
     */
    private static BlockPos findLookedAtSourceWater(
            Player player,
            Level level
    )
    {
        Vec3 eye =
                player.getEyePosition();

        Vec3 look =
                player.getViewVector(1.0F);

        for (double distance = 0.20;
             distance <= DRINK_REACH;
             distance += WATER_SAMPLE_STEP)
        {
            Vec3 sample =
                    eye.add(
                            look.scale(distance)
                    );

            BlockPos pos =
                    BlockPos.containing(sample);

            FluidState fluid =
                    level.getFluidState(pos);

            if (fluid.is(FluidTags.WATER)
                    && fluid.isSource())
            {
                return pos.immutable();
            }

            BlockState state =
                    level.getBlockState(pos);

            if (fluid.isEmpty()
                    && !state.getCollisionShape(
                            level,
                            pos
                    ).isEmpty())
            {
                return null;
            }
        }

        return null;
    }

    private static void applyContamination(
            ServerPlayer player
    )
    {
        /*
         * Contamination is deliberately refresh-only. A fresh bad-water roll
         * always puts Thirst back to seven seconds; it never banks or stacks
         * extra duration from repeated drinking.
         */
        player.addEffect(
                new MobEffectInstance(
                        ModEffects.THIRST,
                        THIRST_DURATION_TICKS,
                        0,
                        false,
                        false,
                        false
                )
        );
    }

    private static void tickHydrationExhaustion(
            ServerPlayer player
    )
    {
        PlayerHydrationManager.getHydrationData(player)
                .ifPresent(data ->
                {
                    double exhaustion =
                            data.exhaustion();

                    if (player.hasEffect(ModEffects.THIRST))
                    {
                        exhaustion +=
                                THIRST_EXHAUSTION_PER_TICK
                                        * EXHAUSTION_UPDATE_INTERVAL;
                    }

                    double saturation =
                            data.saturation();

                    double hydration =
                            data.hydration();

                    while (exhaustion >= EXHAUSTION_THRESHOLD)
                    {
                        exhaustion -= EXHAUSTION_THRESHOLD;

                        if (saturation > 0.0)
                        {
                            saturation =
                                    Math.max(
                                            0.0,
                                            saturation - 1.0
                                    );
                        }
                        else if (hydration > 0.0)
                        {
                            hydration =
                                    Math.max(
                                            0.0,
                                            hydration - 1.0
                                    );
                        }
                    }

                    HydrationData updated =
                            new HydrationData(
                                    hydration,
                                    saturation,
                                    exhaustion
                            );

                    if (Double.compare(
                                updated.hydration(),
                                data.hydration()
                        ) != 0
                            || Double.compare(
                                updated.saturation(),
                                data.saturation()
                        ) != 0
                            || Double.compare(
                                updated.exhaustion(),
                                data.exhaustion()
                        ) != 0)
                    {
                        PlayerHydrationManager.setHydrationData(
                                player,
                                updated
                        );
                    }
                });
    }
}
