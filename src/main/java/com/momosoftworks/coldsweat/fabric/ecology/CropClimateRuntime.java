package com.momosoftworks.coldsweat.fabric.ecology;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Low-frequency crop frost runtime driven by vanilla random-tick attempts.
 *
 * <p>This deliberately composes with Ecliptic rather than replacing it:
 * Cold Sweat can deny a natural growth attempt for physical cold, while an
 * allowed attempt continues down the normal random-tick path where Ecliptic
 * may independently deny it for season/humidity.
 *
 * <p>M10.2a has no crop-death path. Frost stress is retained only as a
 * normalized 0..1 runtime value so later slices can add durable stress/death
 * semantics without one-tick failure.
 */
public final class CropClimateRuntime
{
    /*
     * Natural growth envelope:
     *  >= 5 C : no Cold Sweat growth penalty
     *   0..5 C: linearly slower
     *  <= 0 C : natural growth stops
     */
    private static final double FULL_GROWTH_C = 5.0;

    /*
     * Stress is time-weighted between crop checks instead of adding a fixed
     * amount per random tick. This keeps the result reasonably stable if the
     * server's randomTickSpeed is changed.
     *
     * Direct ordinary frost reaches full stress in about one Minecraft day.
     * Severe direct frost reaches it in about five real minutes. Cover slows
     * accumulation substantially but cannot make deeply sub-zero air harmless.
     */
    private static final double FROST_STRESS_PER_TICK = 1.0 / 24_000.0;
    private static final double SEVERE_FROST_STRESS_PER_TICK = 1.0 / 6_000.0;
    private static final double COVERED_FROST_MULTIPLIER = 0.25;
    private static final double COVERED_SEVERE_MULTIPLIER = 0.40;

    /*
     * Healthy warmth clears stress faster than marginal near-freezing air.
     */
    private static final double COMFORTABLE_RECOVERY_PER_TICK = 1.0 / 12_000.0;
    private static final double MARGINAL_RECOVERY_PER_TICK = 1.0 / 24_000.0;

    /*
     * Do not backfill huge unloaded gaps into one update. The crop must be
     * actively ticking in loaded simulation to accumulate meaningful stress.
     */
    private static final long MAX_ELAPSED_TICKS_PER_SAMPLE = 2_400L;

    private static final int MAX_RETAINED_ENTRIES_PER_LEVEL = 16_384;

    private static final Map<
            ServerLevel,
            Map<Long, StressState>
    > STRESS = new WeakHashMap<>();

    private CropClimateRuntime()
    {
    }

    /**
     * Called at the head of BlockState randomTick.
     *
     * @return true when Cold Sweat permits the block's normal random tick to
     * continue. Ecliptic and vanilla may still reject/skip growth afterward.
     */
    public static boolean allowNaturalGrowthTick(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            RandomSource random
    )
    {
        if (!CropClimateExposure.isAffectedCrop(state))
        {
            return true;
        }

        CropClimateExposure.Sample exposure =
                CropClimateExposure.sample(
                        level,
                        pos,
                        state
                );

        double stress =
                updateStress(
                        level,
                        pos,
                        state,
                        exposure
                );

        emitFrostFeedback(
                level,
                pos,
                exposure,
                stress,
                random
        );

        return switch (exposure.thermalBand())
        {
            case COMFORTABLE -> true;
            case MARGINAL ->
            {
                double growthChance =
                        clamp01(
                                exposure.localAirTemperatureC()
                                        / FULL_GROWTH_C
                        );

                yield random.nextDouble() < growthChance;
            }
            case FROST, SEVERE_FROST -> false;
        };
    }

    public static double getStress(
            ServerLevel level,
            BlockPos pos
    )
    {
        Map<Long, StressState> levelStress =
                STRESS.get(level);

        if (levelStress == null)
        {
            return 0.0;
        }

        StressState state =
                levelStress.get(pos.asLong());

        return state == null
                ? 0.0
                : state.stress;
    }

    private static double updateStress(
            ServerLevel level,
            BlockPos pos,
            BlockState blockState,
            CropClimateExposure.Sample exposure
    )
    {
        Map<Long, StressState> levelStress =
                STRESS.computeIfAbsent(
                        level,
                        ignored -> new HashMap<>()
                );

        long packed = pos.asLong();
        long now = level.getGameTime();

        StressState state =
                levelStress.get(packed);

        if (state == null
                || state.block != blockState.getBlock())
        {
            state =
                    new StressState(
                            blockState.getBlock(),
                            0.0,
                            now
                    );

            levelStress.put(
                    packed,
                    state
            );

            pruneIfNeeded(
                    levelStress,
                    now
            );

            return state.stress;
        }

        long elapsed =
                Math.max(
                        0L,
                        Math.min(
                                MAX_ELAPSED_TICKS_PER_SAMPLE,
                                now - state.lastSeenTick
                        )
                );

        state.lastSeenTick = now;

        if (elapsed <= 0L)
        {
            return state.stress;
        }

        double delta =
                switch (exposure.thermalBand())
                {
                    case COMFORTABLE ->
                            -COMFORTABLE_RECOVERY_PER_TICK
                                    * elapsed;

                    case MARGINAL ->
                            -MARGINAL_RECOVERY_PER_TICK
                                    * elapsed;

                    case FROST ->
                            FROST_STRESS_PER_TICK
                                    * elapsed
                                    * (exposure.directFrostExposure()
                                            ? 1.0
                                            : COVERED_FROST_MULTIPLIER);

                    case SEVERE_FROST ->
                            SEVERE_FROST_STRESS_PER_TICK
                                    * elapsed
                                    * (exposure.directFrostExposure()
                                            ? 1.0
                                            : COVERED_SEVERE_MULTIPLIER);
                };

        state.stress =
                clamp01(
                        state.stress + delta
                );

        return state.stress;
    }

    /**
     * Server-driven visual feedback avoids model/texture surgery on every
     * vanilla or third-party crop. Particle emission only happens on the
     * already-sparse crop random-tick path.
     */
    private static void emitFrostFeedback(
            ServerLevel level,
            BlockPos pos,
            CropClimateExposure.Sample exposure,
            double stress,
            RandomSource random
    )
    {
        if (stress < 0.12
                || exposure.localAirTemperatureC() > 0.0)
        {
            return;
        }

        double chance =
                0.15
                        + stress * 0.45;

        if (random.nextDouble() >= chance)
        {
            return;
        }

        int count =
                stress >= 0.75
                        ? 2
                        : 1;

        level.sendParticles(
                ParticleTypes.SNOWFLAKE,
                pos.getX() + 0.5,
                pos.getY() + 0.55,
                pos.getZ() + 0.5,
                count,
                0.28,
                0.20,
                0.28,
                0.003
        );
    }

    private static void pruneIfNeeded(
            Map<Long, StressState> levelStress,
            long now
    )
    {
        if (levelStress.size()
                <= MAX_RETAINED_ENTRIES_PER_LEVEL)
        {
            return;
        }

        long staleBefore =
                now - 24_000L;

        levelStress.entrySet()
                .removeIf(entry ->
                        entry.getValue().lastSeenTick
                                < staleBefore);

        if (levelStress.size()
                <= MAX_RETAINED_ENTRIES_PER_LEVEL)
        {
            return;
        }

        int removeCount =
                levelStress.size()
                        - MAX_RETAINED_ENTRIES_PER_LEVEL;

        levelStress.entrySet()
                .stream()
                .sorted(
                        Comparator.comparingLong(
                                entry ->
                                        entry.getValue()
                                                .lastSeenTick
                        )
                )
                .limit(removeCount)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(levelStress::remove);
    }

    private static double clamp01(double value)
    {
        return Math.max(
                0.0,
                Math.min(1.0, value)
        );
    }

    private static final class StressState
    {
        private final Block block;
        private double stress;
        private long lastSeenTick;

        private StressState(
                Block block,
                double stress,
                long lastSeenTick
        )
        {
            this.block = block;
            this.stress = stress;
            this.lastSeenTick = lastSeenTick;
        }
    }
}
