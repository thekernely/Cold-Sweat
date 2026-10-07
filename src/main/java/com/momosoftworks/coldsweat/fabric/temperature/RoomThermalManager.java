package com.momosoftworks.coldsweat.fabric.temperature;

import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Runtime thermal reservoir for enclosed connected rooms.
 *
 * dT/dt = exchange * (Tout - Troom) + sourcePower / roomVolume
 *
 * M7.12f-e.1 separates two kinds of heat loss:
 * - slow envelope exchange for genuinely sealed rooms
 * - much faster ventilation exchange through doors/holes/openings
 *
 * M9.3b feeds sky-exposed greenhouse glazing into this same reservoir as
 * solar source power. It is not a flat room-temperature bonus: glazing area,
 * room volume, leakage, time of day, and weather all affect the result.
 *
 * Room state is also fuzzy-matched across small bounding-box changes so
 * opening a door or removing a furnace does not create a brand-new thermal
 * reservoir.
 */
public final class RoomThermalManager
{
    /*
     * Sealed-room exchange is intentionally much slower than the first f-e
     * pass. The minimum rate gives a temperature-difference half-life of about
     * 4.4 minutes instead of ~87 seconds.
     */
    private static final double BASE_EXCHANGE_PER_SECOND = 0.0012;
    private static final double SURFACE_EXCHANGE_SCALE = 0.0018;
    private static final double MIN_SEALED_EXCHANGE_PER_SECOND = 0.0026;
    private static final double MAX_SEALED_EXCHANGE_PER_SECOND = 0.012;

    /*
     * Exterior opening area is measured in exposed air faces. Dividing by room
     * volume naturally makes the same doorway ventilate a small room more
     * aggressively than a large hall. A normal two-block doorway in a ~500
     * cell room lands near 0.02-0.03 / second before the sealed contribution.
     */
    private static final double VENTILATION_SCALE = 6.0;
    private static final double MAX_TOTAL_EXCHANGE_PER_SECOND = 0.25;

    /*
     * Prevent tiny sealed spaces packed with heat sources from converging to
     * absurd temperatures before later material/ventilation systems exist.
     */
    private static final double MAX_SOURCE_DELTA_C = 30.0;

    private static final long NORMAL_SCAN_GAP_TICKS = 32L;
    private static final long PRUNE_AFTER_TICKS = 20L * 60L * 20L;

    /*
     * Small geometry edits are allowed to re-identify an existing room when
     * the old and new bounding boxes overlap strongly. This is what keeps a
     * warm room continuous when a doorway opens/closes or an internal furnace
     * block is removed.
     */
    private static final double MIN_FUZZY_OVERLAP = 0.70;

    private static final Map<
            ServerLevel,
            Map<EnvironmentSnapshotScanner.RoomKey, MutableState>
    > STATES = new WeakHashMap<>();

    private RoomThermalManager()
    {
    }

    public static RoomThermalState update(
            ServerLevel level,
            EnvironmentSnapshotScanner.RoomSample sample,
            double outdoorAmbientMc
    )
    {
        if (sample == null
                || !sample.available()
                || !sample.enclosed()
                || sample.volume() <= 0
                || sample.key() == null)
        {
            return RoomThermalState.unavailable();
        }

        double outdoorC =
                Temperature.convert(
                        outdoorAmbientMc,
                        Temperature.Units.MC,
                        Temperature.Units.C,
                        true
                );

        double leakageRate =
                calculateLeakageRate(sample);

        double greenhouseSolarPower =
                GreenhouseSolarModel.calculateHeatPower(
                        level,
                        sample
                );

        double totalSourcePower =
                sample.heatPower()
                        + greenhouseSolarPower;

        Map<EnvironmentSnapshotScanner.RoomKey, MutableState> levelStates =
                STATES.computeIfAbsent(
                        level,
                        ignored -> new HashMap<>()
                );

        long now = level.getGameTime();

        MutableState state =
                resolveState(
                        levelStates,
                        sample.key(),
                        outdoorC,
                        now
                );

        long elapsedTicks =
                Math.max(
                        0L,
                        now - state.lastUpdateTick
                );

        /*
         * If nobody observed this room for a while, bleed retained heat toward
         * ambient for the unseen interval. We cannot know that historical heat
         * sources stayed active, so the conservative unseen assumption remains
         * zero source input. The current room's present leakage state is used,
         * which means returning to a room with a door left open correctly finds
         * a much cooler reservoir.
         */
        if (elapsedTicks > NORMAL_SCAN_GAP_TICKS)
        {
            double passiveSeconds =
                    Math.max(
                            0.0,
                            (elapsedTicks - 16L) / 20.0
                    );

            state.airTemperatureC =
                    evolve(
                            state.airTemperatureC,
                            outdoorC,
                            0.0,
                            leakageRate,
                            passiveSeconds
                    );

            elapsedTicks = 16L;
        }

        double elapsedSeconds =
                elapsedTicks / 20.0;

        if (elapsedSeconds > 0.0)
        {
            double sourceRateCPerSecond =
                    totalSourcePower
                            / Math.max(
                                    1.0,
                                    sample.volume()
                            );

            sourceRateCPerSecond =
                    Math.min(
                            sourceRateCPerSecond,
                            MAX_SOURCE_DELTA_C
                                    * leakageRate
                    );

            state.airTemperatureC =
                    evolve(
                            state.airTemperatureC,
                            outdoorC,
                            sourceRateCPerSecond,
                            leakageRate,
                            elapsedSeconds
                    );
        }

        state.lastUpdateTick = now;
        state.lastTouchedTick = now;

        if (levelStates.size() > 256)
        {
            prune(levelStates, now);
        }

        return new RoomThermalState(
                true,
                true,
                sample.volume(),
                sample.boundaryFaces(),
                sample.heatSourceBlocks(),
                totalSourcePower,
                leakageRate,
                state.airTemperatureC,
                outdoorC
        );
    }

    private static MutableState resolveState(
            Map<EnvironmentSnapshotScanner.RoomKey, MutableState> states,
            EnvironmentSnapshotScanner.RoomKey key,
            double outdoorC,
            long now
    )
    {
        MutableState exact = states.get(key);
        if (exact != null)
        {
            return exact;
        }

        EnvironmentSnapshotScanner.RoomKey bestKey = null;
        MutableState bestState = null;
        double bestScore = 0.0;

        for (Map.Entry<EnvironmentSnapshotScanner.RoomKey, MutableState> entry
                : states.entrySet())
        {
            MutableState candidate = entry.getValue();

            if (now - candidate.lastTouchedTick > PRUNE_AFTER_TICKS)
            {
                continue;
            }

            double score =
                    overlapScore(
                            key,
                            entry.getKey()
                    );

            if (score >= MIN_FUZZY_OVERLAP
                    && score > bestScore)
            {
                bestScore = score;
                bestKey = entry.getKey();
                bestState = candidate;
            }
        }

        if (bestState != null)
        {
            /*
             * Re-key the same reservoir to the newly measured geometry. The
             * thermal state itself is intentionally untouched.
             */
            states.remove(bestKey);
            states.put(key, bestState);
            return bestState;
        }

        MutableState created =
                new MutableState(
                        outdoorC,
                        now
                );

        states.put(key, created);
        return created;
    }

    private static double overlapScore(
            EnvironmentSnapshotScanner.RoomKey a,
            EnvironmentSnapshotScanner.RoomKey b
    )
    {
        long overlapX =
                Math.max(
                        0,
                        Math.min(a.maxX(), b.maxX())
                                - Math.max(a.minX(), b.minX())
                                + 1
                );

        long overlapY =
                Math.max(
                        0,
                        Math.min(a.maxY(), b.maxY())
                                - Math.max(a.minY(), b.minY())
                                + 1
                );

        long overlapZ =
                Math.max(
                        0,
                        Math.min(a.maxZ(), b.maxZ())
                                - Math.max(a.minZ(), b.minZ())
                                + 1
                );

        if (overlapX == 0
                || overlapY == 0
                || overlapZ == 0)
        {
            return 0.0;
        }

        double overlap =
                overlapX
                        * (double) overlapY
                        * overlapZ;

        double aVolume = boxVolume(a);
        double bVolume = boxVolume(b);

        if (aVolume <= 0.0
                || bVolume <= 0.0)
        {
            return 0.0;
        }

        /*
         * Requiring strong coverage of both boxes avoids accidentally merging
         * adjacent rooms merely because a large new bounding box contains one
         * of them.
         */
        return Math.min(
                overlap / aVolume,
                overlap / bVolume
        );
    }

    private static double boxVolume(
            EnvironmentSnapshotScanner.RoomKey key
    )
    {
        long x =
                Math.max(
                        0,
                        key.maxX() - key.minX() + 1L
                );
        long y =
                Math.max(
                        0,
                        key.maxY() - key.minY() + 1L
                );
        long z =
                Math.max(
                        0,
                        key.maxZ() - key.minZ() + 1L
                );

        return x * (double) y * z;
    }

    private static double calculateLeakageRate(
            EnvironmentSnapshotScanner.RoomSample sample
    )
    {
        double surfaceToVolume =
                sample.boundaryFaces()
                        / (double) Math.max(
                                1,
                                sample.volume()
                        );

        double sealedExchange =
                clamp(
                        BASE_EXCHANGE_PER_SECOND
                                + surfaceToVolume
                                * SURFACE_EXCHANGE_SCALE,
                        MIN_SEALED_EXCHANGE_PER_SECOND,
                        MAX_SEALED_EXCHANGE_PER_SECOND
                );

        double ventilationExchange =
                sample.exteriorOpeningFaces()
                        * VENTILATION_SCALE
                        / Math.max(
                                1.0,
                                sample.volume()
                        );

        return clamp(
                sealedExchange
                        + ventilationExchange,
                MIN_SEALED_EXCHANGE_PER_SECOND,
                MAX_TOTAL_EXCHANGE_PER_SECOND
        );
    }

    private static double evolve(
            double currentC,
            double outdoorC,
            double sourceRateCPerSecond,
            double leakageRatePerSecond,
            double elapsedSeconds
    )
    {
        if (elapsedSeconds <= 0.0)
        {
            return currentC;
        }

        if (leakageRatePerSecond <= 1.0e-9)
        {
            return currentC
                    + sourceRateCPerSecond
                    * elapsedSeconds;
        }

        double targetC =
                outdoorC
                        + sourceRateCPerSecond
                        / leakageRatePerSecond;

        double response =
                Math.exp(
                        -leakageRatePerSecond
                                * elapsedSeconds
                );

        return targetC
                + (currentC - targetC)
                * response;
    }

    private static void prune(
            Map<EnvironmentSnapshotScanner.RoomKey, MutableState> states,
            long now
    )
    {
        Iterator<
                Map.Entry<
                        EnvironmentSnapshotScanner.RoomKey,
                        MutableState
                >
        > iterator = states.entrySet().iterator();

        while (iterator.hasNext())
        {
            MutableState state =
                    iterator.next().getValue();

            if (now - state.lastTouchedTick
                    > PRUNE_AFTER_TICKS)
            {
                iterator.remove();
            }
        }
    }

    private static double clamp(
            double value,
            double min,
            double max
    )
    {
        return Math.max(
                min,
                Math.min(max, value)
        );
    }

    private static final class MutableState
    {
        private double airTemperatureC;
        private long lastUpdateTick;
        private long lastTouchedTick;

        private MutableState(
                double airTemperatureC,
                long tick
        )
        {
            this.airTemperatureC = airTemperatureC;
            this.lastUpdateTick = tick;
            this.lastTouchedTick = tick;
        }
    }
}
