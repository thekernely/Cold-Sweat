package com.momosoftworks.coldsweat.fabric.ecology;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
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

    private static boolean initialized;

    public static synchronized void initialize()
    {
        if (initialized) return;
        initialized = true;
        CropActiveTickClock.initialize();
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) ->
        {
            if (world instanceof ServerLevel level
                    && CropClimateExposure.isAffectedCrop(state))
            {
                clearStress(level, pos);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server ->
        {
            STRESS.clear();
            CropActiveTickClock.clearAll();
        });
    }

    private static Map<Long, StressState> restoreStress(ServerLevel level)
    {
        Map<Long, StressState> restored = new HashMap<>();
        for (CropFrostSavedData.Entry row : CropFrostSavedData.get(level).snapshot())
        {
            try
            {
                Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(row.blockId()));
                if (block == null) continue;
                restored.put(row.position(), new StressState(
                        block, row.stress(), level.getGameTime(), -1L, row.age()));
            }
            catch (IllegalArgumentException ignored)
            {
                // One removed modded block must not invalidate the entire save.
            }
        }
        return restored;
    }

    private static void clearStress(ServerLevel level, BlockPos pos)
    {
        Map<Long, StressState> existing = STRESS.get(level);
        if (existing != null) existing.remove(pos.asLong());
        CropFrostSavedData.get(level).remove(pos.asLong());
    }

    private static int cropAge(BlockState state)
    {
        for (var property : state.getProperties())
        {
            if (property instanceof IntegerProperty age && "age".equals(age.getName()))
            {
                return state.getValue(age);
            }
        }
        return -1;
    }

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

    /**
     * Read-only, bounded visual snapshot. Never drives climate or loads chunks.
     * Sorted by distance so the nearest 256 frosted crops are shown first.
     */
    public static Map<Long, Byte> nearbyVisualTiers(
            ServerLevel level, BlockPos center, int radius, int limit)
    {
        Map<Long, StressState> data = STRESS.computeIfAbsent(level, CropClimateRuntime::restoreStress);
        if (data == null || data.isEmpty()) return Map.of();

        double radiusSq = (double) radius * radius;
        java.util.List<VisualCandidate> candidates = new java.util.ArrayList<>();
        java.util.List<Long> invalid = new java.util.ArrayList<>();
        for (Map.Entry<Long, StressState> entry : data.entrySet())
        {
            byte tier = visualTier(entry.getValue().stress);
            if (tier == 0) continue;
            BlockPos pos = BlockPos.of(entry.getKey());
            double dx = (pos.getX() + 0.5) - center.getX();
            double dy = (pos.getY() + 0.5) - center.getY();
            double dz = (pos.getZ() + 0.5) - center.getZ();
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance > radiusSq) continue;

            // Never load a chunk just to draw frost on it.
            if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null)
                continue;
            BlockState current = level.getBlockState(pos);
            if (current.getBlock() != entry.getValue().block
                    || !CropClimateExposure.isAffectedCrop(current))
            {
                invalid.add(entry.getKey());
                continue;
            }

            candidates.add(new VisualCandidate(entry.getKey(), tier, distance));
        }
        for (Long removed : invalid)
        {
            data.remove(removed);
            CropFrostSavedData.get(level).remove(removed);
        }
        candidates.sort(Comparator.comparingDouble(VisualCandidate::distanceSq));
        Map<Long, Byte> result = new java.util.LinkedHashMap<>();
        for (VisualCandidate candidate : candidates)
        {
            if (result.size() >= limit) break;
            result.put(candidate.packedPos(), candidate.tier());
        }
        return result;
    }

    private static byte visualTier(double stress)
    {
        if (stress < 0.10) return 0;
        if (stress < 0.35) return 1;
        if (stress < 0.70) return 2;
        return 3;
    }

    private record VisualCandidate(long packedPos, byte tier, double distanceSq) { }
    public static double getStress(
            ServerLevel level,
            BlockPos pos
    )
    {
        Map<Long, StressState> levelStress = STRESS.computeIfAbsent(level, CropClimateRuntime::restoreStress);

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
                STRESS.computeIfAbsent(level, CropClimateRuntime::restoreStress);
        long packed = pos.asLong();
        long now = level.getGameTime();
        long activeNow = CropActiveTickClock.activeTicks(level, pos);
        int age = cropAge(blockState);
        StressState state = levelStress.get(packed);

        // Harvest/replant, including the same crop type at a younger age,
        // cannot inherit an old plant's frost stress.
        if (state == null
                || state.block != blockState.getBlock()
                || (age >= 0 && state.age >= 0 && age < state.age))
        {
            if (state != null) CropFrostSavedData.get(level).remove(packed);
            state = new StressState(blockState.getBlock(), 0.0, now, activeNow, age);
            levelStress.put(packed, state);
            pruneIfNeeded(level, levelStress, now);
            return state.stress;
        }

        state.lastSeenTick = now;
        state.age = age;

        // A restored crop gets a NEW observation clock, not elapsed wall time
        // or elapsed global world time since the save.
        if (activeNow < 0L || state.lastActiveTick < 0L
                || activeNow < state.lastActiveTick)
        {
            state.lastActiveTick = activeNow;
            if (state.stress > 0.0)
                CropFrostSavedData.get(level).update(packed, state.block, state.stress, age);
            return state.stress;
        }

        long elapsed = Math.max(0L,
                Math.min(MAX_ELAPSED_TICKS_PER_SAMPLE, activeNow - state.lastActiveTick));
        state.lastActiveTick = activeNow;
        if (elapsed <= 0L)
        {
            if (state.stress > 0.0)
                CropFrostSavedData.get(level).update(packed, state.block, state.stress, age);
            return state.stress;
        }

        double delta = switch (exposure.thermalBand())
        {
            case COMFORTABLE -> -COMFORTABLE_RECOVERY_PER_TICK * elapsed;
            case MARGINAL -> -MARGINAL_RECOVERY_PER_TICK * elapsed;
            case FROST -> FROST_STRESS_PER_TICK * elapsed
                    * (exposure.directFrostExposure() ? 1.0 : COVERED_FROST_MULTIPLIER);
            case SEVERE_FROST -> SEVERE_FROST_STRESS_PER_TICK * elapsed
                    * (exposure.directFrostExposure() ? 1.0 : COVERED_SEVERE_MULTIPLIER);
        };
        state.stress = clamp01(state.stress + delta);
        CropFrostSavedData.get(level).update(packed, state.block, state.stress, age);
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

        // Emission remains attached to the existing crop random-tick path:
        // more visible feedback, without new scans or per-tick particle loops.
        double chance =
                0.52
                        + stress * 0.37;

        if (random.nextDouble() >= chance)
        {
            return;
        }

        int count =
                stress >= 0.80
                        ? 3
                        : stress >= 0.35
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
            ServerLevel level, Map<Long, StressState> levelStress, long now)
    {
        int excess = levelStress.size() - MAX_RETAINED_ENTRIES_PER_LEVEL;
        if (excess <= 0) return;
        java.util.List<Long> toRemove = levelStress.entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> entry.getValue().lastSeenTick))
                .limit(excess)
                .map(Map.Entry::getKey)
                .toList();
        for (Long packed : toRemove)
        {
            levelStress.remove(packed);
            CropFrostSavedData.get(level).remove(packed);
        }
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
        private long lastActiveTick;
        private int age;

        private StressState(Block block, double stress, long lastSeenTick,
                            long lastActiveTick, int age)
        {
            this.block = block;
            this.stress = stress;
            this.lastSeenTick = lastSeenTick;
            this.lastActiveTick = lastActiveTick;
            this.age = age;
        }
    }
}
