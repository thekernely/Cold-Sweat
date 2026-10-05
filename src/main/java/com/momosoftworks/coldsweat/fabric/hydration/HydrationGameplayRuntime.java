package com.momosoftworks.coldsweat.fabric.hydration;

import com.momosoftworks.coldsweat.api.util.Hydration;
import com.momosoftworks.coldsweat.common.capability.handler.PlayerHydrationManager;
import com.momosoftworks.coldsweat.common.capability.hydration.HydrationData;
import com.momosoftworks.coldsweat.core.init.ModEffects;
import com.momosoftworks.coldsweat.core.init.ModItems;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import com.momosoftworks.coldsweat.fabric.temperature.ThermoregulationRuntime;
import com.momosoftworks.coldsweat.util.registries.ModDamageSources;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
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
 * - vanilla water bottles are raw water (+2, contamination risk)
 * - purified water bottles restore +3 with no contamination roll
 *
 * M8.8 couples hydration back into the physiology model:
 *
 * - a small baseline water cost exists even at rest;
 * - movement and heat multiply that baseline demand;
 * - cold adds hunger exhaustion rather than directly touching food level;
 * - severe dehydration slows the player and, after a zero-hydration grace
 *   period, causes dehydration damage.
 *
 * A full seven-second Thirst episode now creates 16.8 exhaustion: four
 * hydration points of loss plus residual debt when saturation is empty.
 * Because a raw sip restores two points, a contaminated sip is a real gamble
 * rather than an automatically profitable trade.
 */
public final class HydrationGameplayRuntime
{
    public static final double RAW_WATER_HYDRATION = 2.0;
    public static final double PURIFIED_WATER_HYDRATION = 3.0;

    public static final double EXHAUSTION_THRESHOLD = 4.0;
    public static final double THIRST_EXHAUSTION_PER_TICK = 0.12;

    /*
     * Baseline: 0.002 exhaustion/tick means 80 exhaustion (20 hydration
     * points) takes 40,000 ticks when saturation is empty: ~33.3 real minutes,
     * or ~1.67 Minecraft days. Activity and heat multiply this baseline.
     */
    private static final double BASE_HYDRATION_EXHAUSTION_PER_TICK = 0.0020;

    /*
     * M8.8b independent balance baseline.
     *
     * Hydration is the persistent expedition resource; vanilla hunger remains
     * the sharper short-term metabolic cost for sprinting and bunny-hopping.
     *
     * With the 0.002/tick passive baseline and no heat load:
     * - walking   ~18.5 min full -> empty
     * - swimming  ~15.9 min
     * - sprinting ~10.4 min
     *
     * Sprint-jumping uses an explicit per-jump surcharge below rather than
     * treating every airborne sprint tick as a giant multiplier.
     */
    private static final double WALKING_WATER_MULTIPLIER = 1.80;
    private static final double SWIMMING_WATER_MULTIPLIER = 2.10;
    private static final double SPRINTING_WATER_MULTIPLIER = 3.20;

    private static final double NORMAL_JUMP_WATER_EXHAUSTION = 0.010;
    private static final double SPRINT_JUMP_WATER_EXHAUSTION = 0.040;

    /*
     * Heat/activity are no longer multiplied wholesale.
     *
     * total = activity
     *       + heat burden
     *       + 35% of the activity/heat interaction.
     *
     * This preserves meaningful heat stress without runaway dehydration.
     */
    private static final double HEAT_ACTIVITY_INTERACTION = 0.35;

    /*
     * M7 heat demand is the canonical input. ~0.77 demand yields ~1.5x,
     * ~1.54 yields ~2x, and sufficiently extreme heat caps at 3x.
     */
    private static final double HEAT_WATER_MULTIPLIER_PER_DEMAND = 0.65;
    private static final double MAX_HEAT_WATER_MULTIPLIER = 3.0;

    /*
     * Cold consumes metabolic fuel. At coldDemand=1 this adds one vanilla
     * hunger-exhaustion threshold every ~4.2 minutes before ordinary movement
     * and healing costs are considered.
     */
    private static final double COLD_HUNGER_EXHAUSTION_PER_TICK_PER_DEMAND = 0.0008;
    private static final double MAX_COLD_HUNGER_DEMAND = 3.0;

    private static final Identifier DEHYDRATION_MOVEMENT =
            ColdSweatFabric.id("dehydration_movement_speed");

    private static final int DEHYDRATION_DAMAGE_GRACE_TICKS = 30 * 20;
    private static final int DEHYDRATION_DAMAGE_INTERVAL_TICKS = 4 * 20;
    private static final float DEHYDRATION_DAMAGE = 1.0F;

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

    private static final Map<UUID, Long> NEXT_DEHYDRATION_DAMAGE_TICK =
            new HashMap<>();

    /*
     * Tracks the previous grounded state so one real upward ground -> air
     * transition is charged once. Walking off a ledge is rejected by the
     * positive-Y check in tickJumpWaterDemand.
     */
    private static final Map<UUID, Boolean> WAS_ON_GROUND =
            new HashMap<>();

    /*
     * 26.2 no longer exposes vanilla potion completion through an override on
     * PotionItem, so do not mix into PotionItem at all. Instead, remember that
     * a vanilla WATER potion started being used and watch the authoritative
     * vanilla ITEM_USED stat. That stat increments only when the drink really
     * finishes, so cancelling the animation cannot grant hydration.
     */
    private static final Map<UUID, PendingWaterBottleUse> PENDING_WATER_BOTTLES =
            new HashMap<>();

    private record PendingWaterBottleUse(
            int initialPotionUses,
            long startedAtTick
    )
    {
    }

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

        /*
         * Vanilla water bottles remain completely vanilla items. This callback
         * prevents use at 20/20 and records the starting ITEM_USED stat. The
         * server tick below applies raw-water hydration only after vanilla
         * confirms that the potion was actually consumed.
         */
        UseItemCallback.EVENT.register(
                HydrationGameplayRuntime::guardWaterBottleUse
        );

        WaterPurificationRecipes.initialize();

        ServerTickEvents.END_SERVER_TICK.register(server ->
        {
            for (ServerPlayer player :
                    server.getPlayerList().getPlayers())
            {
                tickPendingWaterBottleUse(player);
                tickJumpWaterDemand(player);

                if (player.tickCount
                        % EXHAUSTION_UPDATE_INTERVAL
                        == 0)
                {
                    tickHydrationExhaustion(player);
                    tickColdHungerDemand(player);
                    tickDehydrationConsequences(player);
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

        consumeRawWater(serverPlayer);

        serverPlayer.swing(hand);

        return InteractionResult.SUCCESS;
    }

    public static InteractionResult tryFillFlask(
            Player player,
            Level level,
            InteractionHand hand
    )
    {
        ItemStack stack =
                player.getItemInHand(hand);

        if (!FlaskItem.isFlask(stack)
                || FlaskItem.waterAmount(stack)
                >= FlaskItem.capacity(stack))
        {
            return InteractionResult.PASS;
        }

        BlockPos sourceWaterPos =
                findLookedAtSourceWater(
                        player,
                        level
                );

        if (sourceWaterPos == null)
        {
            return InteractionResult.PASS;
        }

        /*
         * M8.6b source-water refill:
         *
         * - one interaction fills to the current tier's capacity;
         * - an installed filter purifies the ENTIRE flask;
         * - every refill operation consumes exactly one filter charge, even a
         *   19/20 -> 20/20 top-up;
         * - the fifth filtered refill removes the filter (5 -> 4 -> ... -> 0);
         * - without a filter, natural source water is untreated;
         * - latest refill determines whole-flask quality.
         */
        if (level.isClientSide())
        {
            level.playSound(
                    player,
                    sourceWaterPos,
                    SoundEvents.BOTTLE_FILL,
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F
            );

            player.swing(hand);
            return InteractionResult.SUCCESS;
        }

        int filterCharges =
                FlaskItem.filterCharges(stack);

        boolean filtered =
                filterCharges > 0;

        FlaskItem.setWaterAmount(
                stack,
                FlaskItem.capacity(stack)
        );

        FlaskItem.setPurified(
                stack,
                filtered
        );

        if (filtered)
        {
            FlaskItem.setFilterCharges(
                    stack,
                    filterCharges - 1
            );
        }

        level.playSound(
                player,
                sourceWaterPos,
                SoundEvents.BOTTLE_FILL,
                SoundSource.PLAYERS,
                1.0F,
                1.0F
        );

        player.swing(hand);

        return InteractionResult.SUCCESS;
    }

    private static InteractionResult guardWaterBottleUse(
            Player player,
            Level level,
            InteractionHand hand
    )
    {
        ItemStack stack =
                player.getItemInHand(hand);

        if (!isVanillaWaterBottle(stack)
                && !stack.is(ModItems.PURIFIED_WATER_BOTTLE))
        {
            return InteractionResult.PASS;
        }

        if (Hydration.get(player)
                >= Hydration.MAX_HYDRATION - 1.0e-6)
        {
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide()
                && isVanillaWaterBottle(stack)
                && player instanceof ServerPlayer serverPlayer)
        {
            int potionUses =
                    serverPlayer.getStats().getValue(
                            Stats.ITEM_USED.get(
                                    Items.POTION
                            )
                    );

            PENDING_WATER_BOTTLES.put(
                    serverPlayer.getUUID(),
                    new PendingWaterBottleUse(
                            potionUses,
                            level.getGameTime()
                    )
            );
        }

        return InteractionResult.PASS;
    }

    public static boolean isVanillaWaterBottle(
            ItemStack stack
    )
    {
        if (!stack.is(Items.POTION))
        {
            return false;
        }

        PotionContents contents =
                stack.getOrDefault(
                        DataComponents.POTION_CONTENTS,
                        PotionContents.EMPTY
                );

        return contents.is(Potions.WATER);
    }

    private static void tickPendingWaterBottleUse(
            ServerPlayer player
    )
    {
        PendingWaterBottleUse pending =
                PENDING_WATER_BOTTLES.get(
                        player.getUUID()
                );

        if (pending == null)
        {
            return;
        }

        int potionUses =
                player.getStats().getValue(
                        Stats.ITEM_USED.get(
                                Items.POTION
                        )
                );

        if (potionUses > pending.initialPotionUses())
        {
            PENDING_WATER_BOTTLES.remove(
                    player.getUUID()
            );

            consumeRawWater(player);
            return;
        }

        /*
         * Give vanilla a couple of ticks to transition into its using-item
         * state. After that, stopping use without a stat increment means the
         * drink was cancelled and must not grant hydration or contamination.
         */
        if (player.level().getGameTime()
                > pending.startedAtTick() + 2
                && !player.isUsingItem())
        {
            PENDING_WATER_BOTTLES.remove(
                    player.getUUID()
            );
        }
    }

    public static void consumeRawWater(
            ServerPlayer player
    )
    {
        Hydration.add(
                player,
                RAW_WATER_HYDRATION
        );

        if (player.getRandom().nextFloat()
                < RAW_WATER_CONTAMINATION_CHANCE)
        {
            applyContamination(player);
        }
    }

    public static void consumePurifiedWater(
            ServerPlayer player
    )
    {
        Hydration.add(
                player,
                PURIFIED_WATER_HYDRATION
        );
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
        if (player.isCreative()
                || player.isSpectator())
        {
            return;
        }

        PlayerHydrationManager.getHydrationData(player)
                .ifPresent(data ->
                {
                    double exhaustion =
                            data.exhaustion();

                    /*
                     * Base water demand is always present in survival play.
                     * Activity and heat multiply only that baseline. The acute
                     * Thirst effect remains a separate additive debt so
                     * contamination does not become absurdly multiplicative.
                     */
                    exhaustion +=
                            BASE_HYDRATION_EXHAUSTION_PER_TICK
                                    * EXHAUSTION_UPDATE_INTERVAL
                                    * combinedWaterDemandMultiplier(
                                            player
                                    );

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

    private static double activityWaterMultiplier(
            ServerPlayer player
    )
    {
        if (player.isSprinting())
        {
            return SPRINTING_WATER_MULTIPLIER;
        }

        if (player.isSwimming())
        {
            return SWIMMING_WATER_MULTIPLIER;
        }

        if (player.getDeltaMovement()
                .horizontalDistanceSqr() > 1.0e-4)
        {
            return WALKING_WATER_MULTIPLIER;
        }

        return 1.0;
    }

    private static double heatWaterBurden(
            ServerPlayer player
    )
    {
        double heatDemand =
                Math.max(
                        0.0,
                        ThermoregulationRuntime
                                .getState(player)
                                .heatDemand()
                );

        /*
         * Preserve the original M8.8 heat calibration, but return only the
         * burden above baseline. Heat alone may therefore raise passive demand
         * to at most the old 3x total.
         */
        return Math.min(
                MAX_HEAT_WATER_MULTIPLIER - 1.0,
                heatDemand
                        * HEAT_WATER_MULTIPLIER_PER_DEMAND
        );
    }

    private static double combinedWaterDemandMultiplier(
            ServerPlayer player
    )
    {
        double activityMultiplier =
                activityWaterMultiplier(player);

        double activityBurden =
                Math.max(
                        0.0,
                        activityMultiplier - 1.0
                );

        double heatBurden =
                heatWaterBurden(player);

        return 1.0
                + activityBurden
                + heatBurden
                + HEAT_ACTIVITY_INTERACTION
                * activityBurden
                * heatBurden;
    }

    private static void tickJumpWaterDemand(
            ServerPlayer player
    )
    {
        UUID playerId =
                player.getUUID();

        boolean onGround =
                player.onGround();

        boolean wasOnGround =
                WAS_ON_GROUND.getOrDefault(
                        playerId,
                        onGround
                );

        WAS_ON_GROUND.put(
                playerId,
                onGround
        );

        if (player.isCreative()
                || player.isSpectator()
                || onGround
                || !wasOnGround)
        {
            return;
        }

        /*
         * A ground -> air transition can also be walking off a ledge.
         * Positive launch velocity distinguishes an actual jump from falling.
         */
        if (player.getDeltaMovement().y <= 0.10)
        {
            return;
        }

        double jumpExhaustion =
                player.isSprinting()
                        ? SPRINT_JUMP_WATER_EXHAUSTION
                        : NORMAL_JUMP_WATER_EXHAUSTION;

        /*
         * Heat only partially amplifies the discrete jump cost, matching the
         * continuous heat/activity interaction model.
         */
        jumpExhaustion *=
                1.0
                        + HEAT_ACTIVITY_INTERACTION
                        * heatWaterBurden(player);

        Hydration.addExhaustion(
                player,
                jumpExhaustion
        );
    }

    private static void tickColdHungerDemand(
            ServerPlayer player
    )
    {
        if (player.isCreative()
                || player.isSpectator())
        {
            return;
        }

        double coldDemand =
                Math.min(
                        MAX_COLD_HUNGER_DEMAND,
                        Math.max(
                                0.0,
                                ThermoregulationRuntime
                                        .getState(player)
                                        .coldDemand()
                        )
                );

        if (coldDemand <= 0.0)
        {
            return;
        }

        player.getFoodData()
                .addExhaustion(
                        (float) (
                                COLD_HUNGER_EXHAUSTION_PER_TICK_PER_DEMAND
                                        * EXHAUSTION_UPDATE_INTERVAL
                                        * coldDemand
                        )
                );
    }

    private static void tickDehydrationConsequences(
            ServerPlayer player
    )
    {
        AttributeInstance movement =
                player.getAttribute(
                        Attributes.MOVEMENT_SPEED
                );

        if (player.isCreative()
                || player.isSpectator())
        {
            if (movement != null)
            {
                movement.removeModifier(
                        DEHYDRATION_MOVEMENT
                );
            }

            NEXT_DEHYDRATION_DAMAGE_TICK.remove(
                    player.getUUID()
            );
            return;
        }

        double hydration =
                Math.max(
                        0.0,
                        Math.min(
                                Hydration.MAX_HYDRATION,
                                Hydration.get(player)
                        )
                );

        double movementPenalty = 0.0;

        if (hydration <= 1.0)
        {
            movementPenalty = 0.10;
        }
        else if (hydration <= 3.0)
        {
            movementPenalty =
                    0.05
                            + (3.0 - hydration)
                            / 2.0
                            * 0.025;
        }

        if (movement != null)
        {
            if (movementPenalty > 0.0)
            {
                movement.addOrUpdateTransientModifier(
                        new AttributeModifier(
                                DEHYDRATION_MOVEMENT,
                                -movementPenalty,
                                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                        )
                );
            }
            else
            {
                movement.removeModifier(
                        DEHYDRATION_MOVEMENT
                );
            }
        }

        if (hydration > 1.0e-6)
        {
            NEXT_DEHYDRATION_DAMAGE_TICK.remove(
                    player.getUUID()
            );
            return;
        }

        long now =
                player.level()
                        .getGameTime();

        long nextDamageTick =
                NEXT_DEHYDRATION_DAMAGE_TICK.computeIfAbsent(
                        player.getUUID(),
                        ignored ->
                                now
                                        + DEHYDRATION_DAMAGE_GRACE_TICKS
                );

        if (now < nextDamageTick)
        {
            return;
        }

        if (player.level().getDifficulty()
                != Difficulty.PEACEFUL)
        {
            hurtDehydration(player);
        }

        NEXT_DEHYDRATION_DAMAGE_TICK.put(
                player.getUUID(),
                now
                        + DEHYDRATION_DAMAGE_INTERVAL_TICKS
        );
    }

    private static void hurtDehydration(
            ServerPlayer player
    )
    {
        Registry<DamageType> damageTypes =
                player.level()
                        .registryAccess()
                        .lookupOrThrow(
                                Registries.DAMAGE_TYPE
                        );

        DamageSource source =
                new DamageSource(
                        damageTypes.getOrThrow(
                                ModDamageSources.DEHYDRATION
                        )
                );

        player.hurtServer(
                player.level(),
                source,
                DEHYDRATION_DAMAGE
        );
    }

}