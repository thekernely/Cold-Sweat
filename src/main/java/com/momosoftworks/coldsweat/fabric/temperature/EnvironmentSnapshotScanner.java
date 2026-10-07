package com.momosoftworks.coldsweat.fabric.temperature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Low-frequency spatial environment pass inspired by Homeostatic's unified
 * environment scan.
 *
 * M7.12f-e extends the snapshot with a capped connected-air flood fill for
 * enclosed-room thermals. This still refreshes every 16 ticks rather than
 * continuously.
 *
 * M9.3a hardens the same bounded scan for shelter/greenhouse semantics:
 * porous barriers do not magically seal air, and rooms with large exterior
 * openings no longer qualify as enclosed thermal reservoirs.
 *
 * M9.3b also measures sky-exposed roof glazing as room geometry. The thermal
 * manager owns the actual time/weather-dependent solar heat calculation.
 */
public final class EnvironmentSnapshotScanner
{
    public static final int HORIZONTAL_RADIUS = 12;
    public static final int VERTICAL_BELOW = 3;
    public static final int VERTICAL_ABOVE = 11;

    private static final int SHELTER_RADIUS = 2;
    private static final int WATER_RADIUS = 5;
    private static final int WATER_MAX_Y_OFFSET = 5;
    private static final int RADIANT_MAX_Y_OFFSET = 3;

    private static final double RAY_STEPS_PER_BLOCK = 2.0;
    private static final double OPAQUE_RADIATION_TRANSMISSION = 0.20;

    /*
     * A single bridge/floor block directly over lava blocks line-of-sight
     * radiation, but it does not make standing over a lava pool thermally
     * equivalent to standing on ordinary ground. This close vertical case
     * approximates conduction + hot convection through/around the floor while
     * ordinary walls still use the normal 20% opaque transmission.
     */
    private static final double LAVA_UNDERFOOT_TRANSMISSION = 0.75;

    private static final int ROOM_CELL_CAP = 8192;

    /*
     * A room may have small intentional openings, such as a normal doorway,
     * while still acting as a thermal reservoir with strong ventilation.
     * Large missing wall sections, roof-only awnings, and porous pens should
     * not qualify as enclosed rooms at all.
     *
     * Coverage alone is insufficient for large rooms: a 3x3 breach in a
     * ~400-face envelope is still ~97.75% sealed. Keep the proportional check
     * for small rooms, but also cap the absolute exterior opening area. Four
     * faces permits a normal double doorway; a 3x3 breach exposes nine.
     */
    private static final double MIN_ROOM_ENCLOSURE_COVERAGE = 0.94;
    private static final int MAX_ROOM_EXTERIOR_OPENING_FACES = 4;

    private EnvironmentSnapshotScanner()
    {
    }

    public static ScanResult scan(
            LivingEntity entity
    )
    {
        if (!(entity.level() instanceof ServerLevel level))
        {
            return new ScanResult(
                    EnvironmentSnapshot.SpatialState.unavailable(),
                    RoomSample.unavailable()
            );
        }

        BlockPos origin = entity.blockPosition();
        BlockPos eyePos = BlockPos.containing(entity.getEyePosition());
        Vec3 entityCenter = entity.getBoundingBox().getCenter();

        return scanAt(
                level,
                origin,
                eyePos,
                entityCenter,
                entity.getBoundingBox()
        );
    }

    /**
     * On-demand spatial sample centered on an arbitrary nearby position.
     *
     * This is intentionally not cached here: the thermometer calls it only on
     * explicit player interaction, while the normal entity runtime continues
     * using its existing low-frequency cached scan.
     */
    public static ScanResult scanAt(
            ServerLevel level,
            BlockPos origin
    )
    {
        return scanAt(
                level,
                origin.immutable(),
                origin.immutable(),
                Vec3.atCenterOf(origin),
                null
        );
    }

    private static ScanResult scanAt(
            ServerLevel level,
            BlockPos origin,
            BlockPos eyePos,
            Vec3 entityCenter,
            AABB roomProbeBounds
    )
    {

        Map<Long, LevelChunk> chunkCache = new HashMap<>();
        Map<RadiantHeatRegistry.Source, Double> strongestSources =
                new IdentityHashMap<>();

        BlockPos.MutableBlockPos cursor =
                new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos skyCursor =
                new BlockPos.MutableBlockPos();

        int scannedBlocks = 0;

        int localSkySamples = 0;
        int localSkyVisible = 0;
        int wideSkySamples = 0;
        int wideSkyVisible = 0;

        int waterSamples = 0;
        int waterBlocks = 0;

        int radiantSourceBlocks = 0;
        double additiveRadiantLoad = 0.0;

        boolean meaningfulSky =
                level.dimensionType().hasSkyLight()
                        && !level.dimensionType().hasCeiling();

        for (int x = -HORIZONTAL_RADIUS;
             x <= HORIZONTAL_RADIUS;
             x++)
        {
            for (int z = -HORIZONTAL_RADIUS;
                 z <= HORIZONTAL_RADIUS;
                 z++)
            {
                int worldX = origin.getX() + x;
                int worldZ = origin.getZ() + z;

                LevelChunk chunk = getLoadedChunk(
                        level,
                        worldX >> 4,
                        worldZ >> 4,
                        chunkCache
                );

                if (chunk == null)
                {
                    continue;
                }

                if (meaningfulSky)
                {
                    skyCursor.set(
                            worldX,
                            eyePos.getY() + 1,
                            worldZ
                    );

                    if (level.isInWorldBounds(skyCursor))
                    {
                        boolean visible =
                                level.canSeeSky(skyCursor);

                        wideSkySamples++;
                        if (visible)
                        {
                            wideSkyVisible++;
                        }

                        if (Math.abs(x) <= SHELTER_RADIUS
                                && Math.abs(z) <= SHELTER_RADIUS)
                        {
                            localSkySamples++;
                            if (visible)
                            {
                                localSkyVisible++;
                            }
                        }
                    }
                }

                for (int y = -VERTICAL_BELOW;
                     y <= VERTICAL_ABOVE;
                     y++)
                {
                    cursor.set(
                            worldX,
                            origin.getY() + y,
                            worldZ
                    );

                    if (!level.isInWorldBounds(cursor))
                    {
                        continue;
                    }

                    BlockState state =
                            chunk.getBlockState(cursor);
                    scannedBlocks++;

                    if (Math.abs(x) <= WATER_RADIUS
                            && Math.abs(z) <= WATER_RADIUS
                            && y <= WATER_MAX_Y_OFFSET)
                    {
                        waterSamples++;

                        if (state.getFluidState()
                                .is(FluidTags.WATER))
                        {
                            waterBlocks++;
                        }
                    }

                    if (y > RADIANT_MAX_Y_OFFSET
                            || state.isAir())
                    {
                        continue;
                    }

                    RadiantHeatRegistry.Source source =
                            RadiantHeatRegistry.get(state)
                                    .orElse(null);

                    if (source == null)
                    {
                        continue;
                    }

                    Vec3 blockCenter =
                            Vec3.atCenterOf(cursor);
                    double distance =
                            entityCenter.distanceTo(blockCenter);

                    double radiation =
                            distance <= 1.0
                                    ? source.maxRadiation()
                                    : source.maxRadiation()
                                            / distance;

                    if (source.fluidScaled())
                    {
                        double amount =
                                state.getFluidState().isEmpty()
                                        ? 1.0
                                        : state.getFluidState().getAmount()
                                                / 8.0;
                        radiation *= amount;
                    }

                    if (y > 0 && y < 5)
                    {
                        radiation *=
                                (4 - y) * 0.25;
                    }

                    if (radiation <= 0.0)
                    {
                        continue;
                    }

                    if (isObscured(
                            level,
                            entityCenter,
                            blockCenter,
                            cursor
                    ))
                    {
                        double transmission =
                                OPAQUE_RADIATION_TRANSMISSION;

                        if (state.is(Blocks.LAVA)
                                && isCloseLavaUnderfoot(
                                        entityCenter,
                                        blockCenter,
                                        distance
                                ))
                        {
                            transmission =
                                    LAVA_UNDERFOOT_TRANSMISSION;
                        }

                        radiation *= transmission;
                    }

                    radiantSourceBlocks++;

                    if (source.strongestOnly())
                    {
                        strongestSources.merge(
                                source,
                                radiation,
                                Math::max
                        );
                    }
                    else
                    {
                        additiveRadiantLoad += radiation;
                    }
                }
            }
        }

        double radiantLoad = additiveRadiantLoad;
        for (double strongest : strongestSources.values())
        {
            radiantLoad += strongest;
        }

        double skyExposure =
                wideSkySamples > 0
                        ? wideSkyVisible
                                / (double) wideSkySamples
                        : 0.0;

        double waterVolume =
                waterSamples > 0
                        ? waterBlocks
                                / (double) waterSamples
                        : 0.0;

        boolean sheltered =
                meaningfulSky
                        && localSkySamples > 0
                        && localSkyVisible == 0;

        boolean underground =
                meaningfulSky
                        && wideSkySamples > 0
                        && wideSkyVisible == 0;

        EnvironmentSnapshot.SpatialState spatial =
                EnvironmentSnapshot.SpatialState.measured(
                        sheltered,
                        underground,
                        skyExposure,
                        waterVolume,
                        radiantLoad,
                        scannedBlocks,
                        radiantSourceBlocks
                );

        return new ScanResult(
                spatial,
                scanRoom(
                        level,
                        origin,
                        roomProbeBounds
                )
        );
    }

    /**
     * Connected passable-air flood fill around the player.
     *
     * M7.12f-e.1 deliberately keeps a partially open room as a room. Air that
     * is directly exposed to the sky becomes a ventilation boundary instead
     * of causing the flood fill to escape into the entire outdoor world. This
     * lets an open door rapidly exchange heat without thermally teleporting the
     * player outside or discarding the room reservoir.
     *
     * M9.3a adds enclosure semantics to that same scan. Small openings remain
     * ventilation boundaries; porous barriers communicate with the air beyond
     * them; and a space must have a sufficiently complete envelope before it
     * can retain room heat.
     *
     * A player who starts in directly sky-exposed air is still outdoors, and a
     * very large connected space that reaches the safety cap is still treated
     * as open/unbounded.
     */
    private static RoomSample scanRoom(
            ServerLevel level,
            BlockPos start,
            AABB probeBounds
    )
    {
        BlockPos actualStart =
                resolveRoomStart(
                        level,
                        start,
                        probeBounds
                );

        if (actualStart == null)
        {
            return RoomSample.unavailable();
        }

        boolean meaningfulSky =
                level.dimensionType().hasSkyLight()
                        && !level.dimensionType().hasCeiling();

        if (meaningfulSky
                && isExteriorSkyOpening(level, actualStart))
        {
            return RoomSample.open(1);
        }

        ArrayDeque<BlockPos> queue =
                new ArrayDeque<>();

        Set<Long> visited =
                new HashSet<>();

        Set<Long> sourcePositions =
                new HashSet<>();

        queue.add(actualStart.immutable());
        visited.add(actualStart.asLong());

        int volume = 0;
        int boundaryFaces = 0;
        int exteriorOpeningFaces = 0;
        int solarGlazingFaces = 0;
        int heatSourceBlocks = 0;
        double heatPower = 0.0;

        int minX = actualStart.getX();
        int minY = actualStart.getY();
        int minZ = actualStart.getZ();
        int maxX = minX;
        int maxY = minY;
        int maxZ = minZ;

        while (!queue.isEmpty())
        {
            BlockPos pos = queue.removeFirst();
            BlockState state =
                    level.getBlockState(pos);

            volume++;

            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());

            RadiantHeatRegistry.Source currentSource =
                    RadiantHeatRegistry.get(state)
                            .orElse(null);

            if (currentSource != null
                    && sourcePositions.add(pos.asLong()))
            {
                heatSourceBlocks++;
                heatPower +=
                        scaledRoomHeatPower(
                                currentSource,
                                state
                        );
            }

            if (volume >= ROOM_CELL_CAP)
            {
                return RoomSample.capped(volume);
            }

            for (Direction direction :
                    Direction.values())
            {
                BlockPos neighbor =
                        pos.relative(direction);

                if (!level.isInWorldBounds(neighbor)
                        || !level.hasChunkAt(neighbor))
                {
                    return RoomSample.open(volume);
                }

                BlockState neighborState =
                        level.getBlockState(neighbor);

                if (isRoomAir(
                        level,
                        neighbor,
                        neighborState
                ))
                {
                    /*
                     * Directly exposed outdoor air is a vent boundary. Do not
                     * flood into it, otherwise opening one door turns the whole
                     * connected outdoor world into the player's "room".
                     */
                    if (meaningfulSky
                            && isExteriorSkyOpening(level, neighbor))
                    {
                        exteriorOpeningFaces++;
                        continue;
                    }

                    long packed =
                            neighbor.asLong();

                    if (visited.add(packed))
                    {
                        queue.addLast(
                                neighbor.immutable()
                        );
                    }

                    continue;
                }

                /*
                 * M9.3b treats only upward-facing, sky-exposed glass as a
                 * solar aperture. The scan records geometry only; actual solar
                 * strength remains a time/weather-dependent thermal concern.
                 *
                 * Stained glass is accepted, while tinted glass is not: it
                 * deliberately blocks skylight and should not heat a room.
                 */
                if (direction == Direction.UP
                        && isSolarRoofGlazing(neighborState)
                        && meaningfulSky
                        && level.canSeeSky(neighbor.above()))
                {
                    solarGlazingFaces++;
                }

                /*
                 * Fences, walls, closed fence gates, and iron bars obstruct
                 * movement but not air strongly enough to define a thermal
                 * envelope. Look through them by one block rather than counting
                 * them as a sealed boundary. Glass panes intentionally do NOT
                 * take this path because they are valid greenhouse glazing.
                 */
                if (isThermallyPorousBoundary(neighborState))
                {
                    BlockPos beyond =
                            neighbor.relative(direction);

                    if (!level.isInWorldBounds(beyond)
                            || !level.hasChunkAt(beyond))
                    {
                        exteriorOpeningFaces++;
                        continue;
                    }

                    BlockState beyondState =
                            level.getBlockState(beyond);

                    if (isRoomAir(
                            level,
                            beyond,
                            beyondState
                    ))
                    {
                        if (meaningfulSky
                                && isExteriorSkyOpening(level, beyond))
                        {
                            exteriorOpeningFaces++;
                            continue;
                        }

                        long packed =
                                beyond.asLong();

                        if (visited.add(packed))
                        {
                            queue.addLast(
                                    beyond.immutable()
                            );
                        }

                        continue;
                    }

                    /*
                     * A porous block backed immediately by a real solid
                     * envelope still has a sealing layer behind it.
                     */
                    boundaryFaces++;
                    continue;
                }

                boundaryFaces++;

                RadiantHeatRegistry.Source source =
                        RadiantHeatRegistry.get(
                                neighborState
                        ).orElse(null);

                if (source != null
                        && sourcePositions.add(
                                neighbor.asLong()
                        ))
                {
                    heatSourceBlocks++;
                    heatPower +=
                            scaledRoomHeatPower(
                                    source,
                                    neighborState
                            );
                }
            }
        }

        int knownEnvelopeFaces =
                boundaryFaces + exteriorOpeningFaces;

        double enclosureCoverage =
                knownEnvelopeFaces > 0
                        ? boundaryFaces
                                / (double) knownEnvelopeFaces
                        : 0.0;

        boolean enclosed =
                knownEnvelopeFaces > 0
                        && exteriorOpeningFaces
                                <= MAX_ROOM_EXTERIOR_OPENING_FACES
                        && enclosureCoverage
                                >= MIN_ROOM_ENCLOSURE_COVERAGE;

        return new RoomSample(
                true,
                enclosed,
                false,
                volume,
                boundaryFaces,
                exteriorOpeningFaces,
                solarGlazingFaces,
                heatSourceBlocks,
                heatPower,
                new RoomKey(
                        minX,
                        minY,
                        minZ,
                        maxX,
                        maxY,
                        maxZ
                )
        );
    }


    private static BlockPos resolveRoomStart(
            ServerLevel level,
            BlockPos start,
            AABB probeBounds
    )
    {
        BlockState startState =
                level.getBlockState(start);

        if (isRoomAir(level, start, startState))
        {
            return start.immutable();
        }

        /*
         * Partial collision blocks such as panes, fences, walls, and bars can
         * share the player's BlockPos even though the player is physically on
         * one side of the barrier. Choosing an arbitrary nearby air cell would
         * let an outside player borrow the warm room on the opposite side.
         *
         * For live entity scans, use the player's actual AABB and only accept
         * horizontal air cells that the body physically overlaps. The side
         * with the greatest overlap is the side the player occupies. Exact
         * ties are treated as ambiguous instead of guessing across a boundary.
         */
        if (probeBounds != null)
        {
            BlockPos best = null;
            double bestOverlap = 0.0;
            boolean ambiguous = false;

            for (Direction direction : Direction.values())
            {
                if (direction == Direction.UP
                        || direction == Direction.DOWN)
                {
                    continue;
                }

                BlockPos candidate =
                        start.relative(direction);
                BlockState candidateState =
                        level.getBlockState(candidate);

                if (!isRoomAir(
                        level,
                        candidate,
                        candidateState
                ))
                {
                    continue;
                }

                double overlap =
                        overlapVolume(
                                probeBounds,
                                candidate
                        );

                if (overlap <= 1.0e-9)
                {
                    continue;
                }

                if (overlap > bestOverlap + 1.0e-9)
                {
                    best = candidate;
                    bestOverlap = overlap;
                    ambiguous = false;
                }
                else if (Math.abs(overlap - bestOverlap)
                        <= 1.0e-9)
                {
                    ambiguous = true;
                }
            }

            if (best != null && !ambiguous)
            {
                return best.immutable();
            }

            if (ambiguous)
            {
                return null;
            }
        }

        /*
         * Preserve the old vertical fallback for non-entity probes and unusual
         * standing surfaces where the block above is the first true air cell.
         */
        BlockPos above = start.above();
        BlockState aboveState =
                level.getBlockState(above);

        if (isRoomAir(level, above, aboveState))
        {
            return above.immutable();
        }

        return null;
    }

    private static double overlapVolume(
            AABB bounds,
            BlockPos block
    )
    {
        double overlapX =
                Math.max(
                        0.0,
                        Math.min(
                                bounds.maxX,
                                block.getX() + 1.0
                        ) - Math.max(
                                bounds.minX,
                                block.getX()
                        )
                );

        double overlapY =
                Math.max(
                        0.0,
                        Math.min(
                                bounds.maxY,
                                block.getY() + 1.0
                        ) - Math.max(
                                bounds.minY,
                                block.getY()
                        )
                );

        double overlapZ =
                Math.max(
                        0.0,
                        Math.min(
                                bounds.maxZ,
                                block.getZ() + 1.0
                        ) - Math.max(
                                bounds.minZ,
                                block.getZ()
                        )
                );

        return overlapX * overlapY * overlapZ;
    }

    private static double scaledRoomHeatPower(
            RadiantHeatRegistry.Source source,
            BlockState state
    )
    {
        double power =
                source.roomHeatPower();

        if (source.fluidScaled()
                && !state.getFluidState().isEmpty())
        {
            power *=
                    state.getFluidState().getAmount()
                            / 8.0;
        }

        return power;
    }

    private static boolean isRoomAir(
            ServerLevel level,
            BlockPos pos,
            BlockState state
    )
    {
        if (!state.getFluidState().isEmpty())
        {
            return false;
        }

        /*
         * Open doors/gates/trapdoors connect two air volumes even though their
         * collision shape is not necessarily empty.
         */
        if (state.hasProperty(BlockStateProperties.OPEN)
                && state.getValue(BlockStateProperties.OPEN))
        {
            return true;
        }

        return state.getCollisionShape(
                        level,
                        pos
                )
                .isEmpty();
    }

    /*
     * canSeeSky() describes skylight visibility, not physical airflow.
     * Transparent glazing can therefore report sky visibility from inside
     * a completely sealed greenhouse.
     *
     * Room enclosure needs the physical interpretation: an air cell is an
     * exterior opening only when the vertical path above it remains passable
     * room air all the way out of the world. Glass transmits skylight but
     * terminates this airflow path.
     */
    private static boolean isExteriorSkyOpening(
            ServerLevel level,
            BlockPos pos
    )
    {
        if (!level.canSeeSky(pos))
        {
            return false;
        }

        BlockPos.MutableBlockPos cursor =
                new BlockPos.MutableBlockPos();

        cursor.set(
                pos.getX(),
                pos.getY() + 1,
                pos.getZ()
        );

        while (level.isInWorldBounds(cursor))
        {
            BlockState state =
                    level.getBlockState(cursor);

            if (!isRoomAir(level, cursor, state))
            {
                return false;
            }

            cursor.setY(cursor.getY() + 1);
        }

        return true;
    }

    private static boolean isSolarRoofGlazing(
            BlockState state
    )
    {
        return state.is(Blocks.GLASS)
                || state.getBlock()
                        instanceof net.minecraft.world.level.block.StainedGlassBlock;
    }

    private static boolean isThermallyPorousBoundary(
            BlockState state
    )
    {
        return state.getBlock() instanceof FenceBlock
                || state.getBlock() instanceof WallBlock
                || state.getBlock() instanceof FenceGateBlock
                || state.is(Blocks.IRON_BARS);
    }

    private static LevelChunk getLoadedChunk(
            ServerLevel level,
            int chunkX,
            int chunkZ,
            Map<Long, LevelChunk> cache
    )
    {
        long key =
                ((long) chunkX << 32)
                        ^ (chunkZ & 0xffffffffL);

        if (cache.containsKey(key))
        {
            return cache.get(key);
        }

        LevelChunk chunk =
                level.getChunkSource()
                        .getChunkNow(chunkX, chunkZ);

        cache.put(key, chunk);
        return chunk;
    }

    private static boolean isCloseLavaUnderfoot(
            Vec3 entityCenter,
            Vec3 sourceCenter,
            double distance
    )
    {
        if (sourceCenter.y >= entityCenter.y
                || distance > 2.5)
        {
            return false;
        }

        double dx =
                entityCenter.x - sourceCenter.x;
        double dz =
                entityCenter.z - sourceCenter.z;

        double horizontalDistanceSquared =
                dx * dx + dz * dz;

        return horizontalDistanceSquared <= 1.0;
    }

    private static boolean isObscured(
            ServerLevel level,
            Vec3 start,
            Vec3 end,
            BlockPos sourcePos
    )
    {
        double distance =
                start.distanceTo(end);

        if (distance <= 1.0)
        {
            return false;
        }

        int steps =
                Math.max(
                        1,
                        (int) Math.ceil(
                                distance
                                        * RAY_STEPS_PER_BLOCK
                        )
                );

        BlockPos.MutableBlockPos rayPos =
                new BlockPos.MutableBlockPos();

        long lastPos = Long.MIN_VALUE;

        for (int step = 1;
             step < steps;
             step++)
        {
            double progress =
                    step / (double) steps;

            rayPos.set(
                    (int) Math.floor(
                            start.x
                                    + (end.x - start.x)
                                    * progress
                    ),
                    (int) Math.floor(
                            start.y
                                    + (end.y - start.y)
                                    * progress
                    ),
                    (int) Math.floor(
                            start.z
                                    + (end.z - start.z)
                                    * progress
                    )
            );

            if (rayPos.getX() == sourcePos.getX()
                    && rayPos.getY() == sourcePos.getY()
                    && rayPos.getZ() == sourcePos.getZ())
            {
                continue;
            }

            long packed = rayPos.asLong();
            if (packed == lastPos)
            {
                continue;
            }
            lastPos = packed;

            if (level.getBlockState(rayPos)
                    .isSolidRender())
            {
                return true;
            }
        }

        return false;
    }

    public record ScanResult(
            EnvironmentSnapshot.SpatialState spatial,
            RoomSample room
    )
    {
    }

    public record RoomKey(
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ
    )
    {
    }

    public record RoomSample(
            boolean available,
            boolean enclosed,
            boolean capped,
            int volume,
            int boundaryFaces,
            int exteriorOpeningFaces,
            int solarGlazingFaces,
            int heatSourceBlocks,
            double heatPower,
            RoomKey key
    )
    {
        private static final RoomSample UNAVAILABLE =
                new RoomSample(
                        false,
                        false,
                        false,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0.0,
                        null
                );

        public static RoomSample unavailable()
        {
            return UNAVAILABLE;
        }

        public static RoomSample open(int visited)
        {
            return new RoomSample(
                    true,
                    false,
                    false,
                    visited,
                    0,
                    0,
                    0,
                    0,
                    0.0,
                    null
            );
        }

        public static RoomSample capped(int visited)
        {
            return new RoomSample(
                    true,
                    false,
                    true,
                    visited,
                    0,
                    0,
                    0,
                    0,
                    0.0,
                    null
            );
        }
    }
}
