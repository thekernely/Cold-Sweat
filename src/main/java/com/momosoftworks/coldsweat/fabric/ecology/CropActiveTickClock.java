package com.momosoftworks.coldsweat.fabric.ecology;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Counts only actual block-ticking server ticks for chunks with observed crops.
 * Saved frost stress survives reloads; this CLOCK intentionally does not.
 *
 * A dimension's gameTime can advance while a chunk is unloaded/non-ticking,
 * so gameTime deltas cannot safely represent frost exposure.
 */
public final class CropActiveTickClock
{
    private static final int MAX_TRACKED_CHUNKS_PER_LEVEL = 512;
    private static final long EXPIRE_AFTER_TICKS = 24_000L;

    private static final Map<ServerLevel, Map<Long, Clock>> CLOCKS =
            new WeakHashMap<>();

    private static boolean initialized;

    private CropActiveTickClock() {}

    public static synchronized void initialize()
    {
        if (initialized) return;
        initialized = true;
        ServerTickEvents.END_LEVEL_TICK.register(CropActiveTickClock::tickLevel);
    }

    public static long activeTicks(ServerLevel level, BlockPos cropPos)
    {
        int x = cropPos.getX() >> 4;
        int z = cropPos.getZ() >> 4;
        long packed = pack(x, z);

        Map<Long, Clock> clocks =
                CLOCKS.computeIfAbsent(level, ignored -> new HashMap<>());

        Clock clock = clocks.get(packed);
        if (clock == null)
        {
            if (clocks.size() >= MAX_TRACKED_CHUNKS_PER_LEVEL)
            {
                prune(clocks, level.getGameTime());
            }
            if (clocks.size() >= MAX_TRACKED_CHUNKS_PER_LEVEL)
            {
                // Bounded fallback: does not turn global elapsed time into exposure.
                return -1L;
            }
            clock = new Clock(x, z);
            clocks.put(packed, clock);
        }
        clock.lastObservedGameTick = level.getGameTime();
        return clock.activeTicks;
    }

    private static void tickLevel(ServerLevel level)
    {
        Map<Long, Clock> clocks = CLOCKS.get(level);
        if (clocks == null || clocks.isEmpty()) return;
        long now = level.getGameTime();

        Iterator<Clock> iterator = clocks.values().iterator();
        while (iterator.hasNext())
        {
            Clock clock = iterator.next();
            if (now - clock.lastObservedGameTick > EXPIRE_AFTER_TICKS)
            {
                iterator.remove();
                continue;
            }
            if (level.getChunkSource().getChunkNow(clock.x, clock.z) != null
                    && level.shouldTickBlocksAt(pack(clock.x, clock.z)))
            {
                clock.activeTicks++;
            }
        }
    }

    public static void clearAll()
    {
        CLOCKS.clear();
    }

    private static void prune(Map<Long, Clock> clocks, long now)
    {
        clocks.values().removeIf(c -> now - c.lastObservedGameTick > EXPIRE_AFTER_TICKS);
    }

    private static long pack(int x, int z)
    {
        return (x & 0xffffffffL) | ((z & 0xffffffffL) << 32);
    }

    private static final class Clock
    {
        private final int x, z;
        private long activeTicks;
        private long lastObservedGameTick;

        private Clock(int x, int z)
        {
            this.x = x;
            this.z = z;
        }
    }
}
