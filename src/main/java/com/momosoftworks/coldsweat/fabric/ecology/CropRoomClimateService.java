package com.momosoftworks.coldsweat.fabric.ecology;

import com.momosoftworks.coldsweat.api.temperature.modifier.BiomeTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.ElevationTempModifier;
import com.momosoftworks.coldsweat.fabric.temperature.EnvironmentSnapshotScanner;
import com.momosoftworks.coldsweat.fabric.temperature.RoomThermalManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * M10.2b: crop-discovered, room-centric greenhouse thermal upkeep.
 *
 * <p>Crop random ticks only DISCOVER/OBSERVE rooms. They never flood-fill the
 * world per crop. Once discovered, one shared room sample feeds M9's existing
 * heat/solar/leakage reservoir every second while all of its chunks are block
 * ticking. Geometry and heater state refresh at a bounded interval.
 *
 * <p>No forced chunk tickets, no Ecliptic dependency, no offline stress
 * catch-up, and no duplicate greenhouse temperature formula.
 */
public final class CropRoomClimateService
{
    private static final int ROOF_SEARCH_BLOCKS = 16;
    private static final int MAX_ROOMS_PER_LEVEL = 96;
    private static final int MAX_CHUNK_PROBE_RECORDS = 2048;
    private static final long ROOM_REFRESH_TICKS = 400;
    private static final long CHUNK_PROBE_COOLDOWN_TICKS = 240;
    private static final long WORLD_SCAN_GAP_TICKS = 5;
    private static final long CROP_OBSERVATION_TIMEOUT_TICKS = 2400;
    private static final long TICK_STEP_TICKS = 20;

    private static final Map<ServerLevel, Tracker> TRACKERS = new WeakHashMap<>();
    private static boolean initialized;

    private CropRoomClimateService() {}

    public static synchronized void initialize()
    {
        if (initialized) return;
        initialized = true;
        ServerTickEvents.END_LEVEL_TICK.register(CropRoomClimateService::tickLevel);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> TRACKERS.clear());
    }

    /**
     * @return measured retained air in Celsius, or NaN if this crop isn't in a
     * currently qualified/observed room. Caller falls back to outdoor air.
     */
    public static double sampleAirC(
            ServerLevel level,
            BlockPos cropPos,
            double outdoorAmbientMc
    )
    {
        Tracker tracker = TRACKERS.computeIfAbsent(level, ignored -> new Tracker());
        long now = level.getGameTime();

        for (TrackedRoom room : tracker.rooms.values())
        {
            if (!contains(room.sample.key(), cropPos)) continue;
            if (!allChunksTicking(level, room.sample.key())) return Double.NaN;

            room.lastCropSeenTick = now;
            double airC = RoomThermalManager.getCachedRoomTemperatureC(level, cropPos);
            return Double.isFinite(airC) ? airC : Double.NaN;
        }

        // Open fields should be almost free; skylight through glass isn't
        // enough to identify a roof, so inspect a short, loaded vertical ray.
        if (!hasPhysicalOverhead(level, cropPos)) return Double.NaN;
        if (!canTick(level, cropPos)) return Double.NaN;
        if (now < tracker.nextWorldScanTick) return Double.NaN;

        long chunk = packedChunk(cropPos.getX() >> 4, cropPos.getZ() >> 4);
        if (now < tracker.nextProbeByChunk.getOrDefault(chunk, Long.MIN_VALUE))
        {
            return Double.NaN;
        }

        // Mark attempts before scanning, including unsuccessful ones. This is
        // critical when hundreds of plants share the same incomplete shed.
        tracker.nextWorldScanTick = now + WORLD_SCAN_GAP_TICKS;
        tracker.nextProbeByChunk.put(chunk, now + CHUNK_PROBE_COOLDOWN_TICKS);
        pruneProbeRecords(tracker, now);
        pruneInactiveRooms(level, tracker, now);
        if (tracker.rooms.size() >= MAX_ROOMS_PER_LEVEL) return Double.NaN;

        EnvironmentSnapshotScanner.RoomSample sample =
                EnvironmentSnapshotScanner.scanRoomOnlyAt(level, cropPos);
        if (!qualified(sample) || !allChunksTicking(level, sample.key()))
        {
            return Double.NaN;
        }

        TrackedRoom existing = tracker.rooms.get(sample.key());
        if (existing == null)
        {
            existing = new TrackedRoom(sample, cropPos.immutable(), now);
            tracker.rooms.put(sample.key(), existing);
        }
        else
        {
            existing.lastCropSeenTick = now;
        }

        RoomThermalManager.update(level, sample, outdoorAmbientMc);
        return RoomThermalManager.getCachedRoomTemperatureC(level, cropPos);
    }

    private static void tickLevel(ServerLevel level)
    {
        long now = level.getGameTime();
        if (Math.floorMod(now, TICK_STEP_TICKS) != 0) return;

        Tracker tracker = TRACKERS.get(level);
        if (tracker == null || tracker.rooms.isEmpty()) return;

        Iterator<Map.Entry<EnvironmentSnapshotScanner.RoomKey, TrackedRoom>> iterator =
                tracker.rooms.entrySet().iterator();
        while (iterator.hasNext())
        {
            TrackedRoom room = iterator.next().getValue();
            if (!allChunksTicking(level, room.sample.key()))
            {
                // A loaded but non-block-ticking chunk is not active ecology.
                // Discard skipped simulation time, including unloaded intervals.
                RoomThermalManager.pauseRoom(level, room.sample.key());
                continue;
            }

            if (now - room.lastCropSeenTick > CROP_OBSERVATION_TIMEOUT_TICKS)
            {
                iterator.remove();
                continue;
            }

            if (now >= room.nextRefreshTick && now >= tracker.nextWorldScanTick)
            {
                tracker.nextWorldScanTick = now + WORLD_SCAN_GAP_TICKS;
                EnvironmentSnapshotScanner.RoomSample refreshed =
                        EnvironmentSnapshotScanner.scanRoomOnlyAt(level, room.cropAnchor);
                room.nextRefreshTick = now + ROOM_REFRESH_TICKS;

                if (!qualified(refreshed)
                        || !allChunksTicking(level, refreshed.key()))
                {
                    // An opened/destroyed envelope cannot keep its old heat.
                    RoomThermalManager.forgetRoom(level, room.sample.key());
                    iterator.remove();
                    continue;
                }

                room.sample = refreshed;
            }

            double outdoorMc =
                    BiomeTempModifier.sampleLocalClimateAt(level, room.cropAnchor)
                            + ElevationTempModifier.getAltitudeOffset(
                                    level, room.cropAnchor);
            RoomThermalManager.update(level, room.sample, outdoorMc);
        }

        // Rekey after scans, not during Map iteration. Strongly overlapping
        // bounding boxes are already reconciled by RoomThermalManager.
        if (!tracker.rooms.isEmpty())
        {
            LinkedHashMap<EnvironmentSnapshotScanner.RoomKey, TrackedRoom> keyed =
                    new LinkedHashMap<>();
            for (TrackedRoom room : tracker.rooms.values())
            {
                keyed.put(room.sample.key(), room);
            }
            tracker.rooms.clear();
            tracker.rooms.putAll(keyed);
        }
    }

    private static boolean qualified(EnvironmentSnapshotScanner.RoomSample sample)
    {
        return sample != null && sample.available() && sample.enclosed()
                && !sample.capped() && sample.key() != null && sample.volume() > 0;
    }

    /**
     * Minecraft's canonical packed chunk-long representation. Using an
     * explicit helper avoids depending on the renamed ChunkPos API in 26.2.
     * The low 32 bits are chunk X; the high 32 bits are chunk Z.
     */
    private static long packedChunk(int chunkX, int chunkZ)
    {
        return (chunkX & 0xffffffffL) | ((chunkZ & 0xffffffffL) << 32);
    }

    private static boolean canTick(ServerLevel level, BlockPos pos)
    {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        return level.getChunkSource().getChunkNow(chunkX, chunkZ) != null
                && level.shouldTickBlocksAt(packedChunk(chunkX, chunkZ));
    }

    private static boolean allChunksTicking(
            ServerLevel level,
            EnvironmentSnapshotScanner.RoomKey key)
    {
        if (key == null) return false;
        int minX = key.minX() >> 4;
        int maxX = key.maxX() >> 4;
        int minZ = key.minZ() >> 4;
        int maxZ = key.maxZ() >> 4;
        if (maxX - minX > 16 || maxZ - minZ > 16) return false;

        for (int cx = minX; cx <= maxX; cx++)
        {
            for (int cz = minZ; cz <= maxZ; cz++)
            {
                if (level.getChunkSource().getChunkNow(cx, cz) == null
                        || !level.shouldTickBlocksAt(packedChunk(cx, cz)))
                {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean contains(
            EnvironmentSnapshotScanner.RoomKey key,
            BlockPos pos)
    {
        return key != null
                && pos.getX() >= key.minX() && pos.getX() <= key.maxX()
                && pos.getY() >= key.minY() && pos.getY() <= key.maxY()
                && pos.getZ() >= key.minZ() && pos.getZ() <= key.maxZ();
    }

    public static boolean hasPhysicalOverhead(ServerLevel level, BlockPos pos)
    {
        // An opaque roof can be arbitrarily high; canSeeSky handles it.
        if (!level.canSeeSky(pos.above())) return true;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy <= ROOF_SEARCH_BLOCKS; dy++)
        {
            cursor.set(pos.getX(), pos.getY() + dy, pos.getZ());
            if (!level.isInWorldBounds(cursor) || !level.hasChunkAt(cursor)) return false;
            BlockState block = level.getBlockState(cursor);
            if (!block.getCollisionShape(level, cursor).isEmpty()) return true;
        }
        return false;
    }

    private static void pruneInactiveRooms(ServerLevel level, Tracker tracker, long now)
    {
        tracker.rooms.values().removeIf(room ->
                allChunksTicking(level, room.sample.key())
                        && now - room.lastCropSeenTick > CROP_OBSERVATION_TIMEOUT_TICKS);
    }

    private static void pruneProbeRecords(Tracker tracker, long now)
    {
        if (tracker.nextProbeByChunk.size() <= MAX_CHUNK_PROBE_RECORDS) return;
        tracker.nextProbeByChunk.values().removeIf(next -> next < now);
        if (tracker.nextProbeByChunk.size() > MAX_CHUNK_PROBE_RECORDS)
        {
            tracker.nextProbeByChunk.clear();
        }
    }

    private static final class Tracker
    {
        private final LinkedHashMap<EnvironmentSnapshotScanner.RoomKey, TrackedRoom> rooms =
                new LinkedHashMap<>();
        private final Map<Long, Long> nextProbeByChunk = new HashMap<>();
        private long nextWorldScanTick;
    }

    private static final class TrackedRoom
    {
        private EnvironmentSnapshotScanner.RoomSample sample;
        private final BlockPos cropAnchor;
        private long lastCropSeenTick;
        private long nextRefreshTick;

        private TrackedRoom(
                EnvironmentSnapshotScanner.RoomSample sample,
                BlockPos cropAnchor,
                long now)
        {
            this.sample = sample;
            this.cropAnchor = cropAnchor;
            this.lastCropSeenTick = now;
            this.nextRefreshTick = now + ROOM_REFRESH_TICKS;
        }
    }
}
