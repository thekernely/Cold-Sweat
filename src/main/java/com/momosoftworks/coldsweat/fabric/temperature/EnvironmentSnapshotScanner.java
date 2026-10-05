package com.momosoftworks.coldsweat.fabric.temperature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
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
                        entity.blockPosition()
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
     * A player who starts in directly sky-exposed air is still outdoors, and a
     * very large connected space that reaches the safety cap is still treated
     * as open/unbounded.
     */
    private static RoomSample scanRoom(
            ServerLevel level,
            BlockPos start
    )
    {
        BlockPos actualStart = start;
        BlockState startState =
                level.getBlockState(actualStart);

        if (!isRoomAir(level, actualStart, startState))
        {
            actualStart = start.above();
            startState = level.getBlockState(actualStart);

            if (!isRoomAir(
                    level,
                    actualStart,
                    startState
            ))
            {
                return RoomSample.unavailable();
            }
        }

        boolean meaningfulSky =
                level.dimensionType().hasSkyLight()
                        && !level.dimensionType().hasCeiling();

        if (meaningfulSky
                && level.canSeeSky(actualStart))
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
                            && level.canSeeSky(neighbor))
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

        return new RoomSample(
                true,
                true,
                false,
                volume,
                boundaryFaces,
                exteriorOpeningFaces,
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
                    0.0,
                    null
            );
        }
    }
}
