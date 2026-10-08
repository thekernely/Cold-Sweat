package com.momosoftworks.coldsweat.fabric.temperature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists room thermal reservoirs across integrated/dedicated server restarts.
 * No game timestamps are serialized: elapsed time while unloaded/offline must
 * never be treated as real observed thermal evolution.
 *
 * This is a bounded cache, not a chunk force-loader or a room discovery index.
 * RoomThermalManager restores an entry only after a live enclosed-room scan.
 */
public final class RoomThermalSavedData extends SavedData
{
    private static final int MAX_ROOMS = 256;
    private static final double MIN_SAVE_CHANGE_C = 0.01;

    public record Entry(
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ,
            double airTemperatureC)
    {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.INT.fieldOf("min_x").forGetter(Entry::minX),
                        Codec.INT.fieldOf("min_y").forGetter(Entry::minY),
                        Codec.INT.fieldOf("min_z").forGetter(Entry::minZ),
                        Codec.INT.fieldOf("max_x").forGetter(Entry::maxX),
                        Codec.INT.fieldOf("max_y").forGetter(Entry::maxY),
                        Codec.INT.fieldOf("max_z").forGetter(Entry::maxZ),
                        Codec.DOUBLE.fieldOf("air_c").forGetter(Entry::airTemperatureC)
                ).apply(instance, Entry::new));

        public EnvironmentSnapshotScanner.RoomKey key()
        {
            return new EnvironmentSnapshotScanner.RoomKey(
                    minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    public static final Codec<RoomThermalSavedData> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Entry.CODEC.listOf().optionalFieldOf("rooms", List.of())
                                    .forGetter(RoomThermalSavedData::snapshot)
                    ).apply(instance, RoomThermalSavedData::new));

    public static final SavedDataType<RoomThermalSavedData> TYPE =
            new SavedDataType<>(
                    ColdSweatFabric.id("room_thermal_state"),
                    RoomThermalSavedData::new,
                    CODEC,
                    DataFixTypes.LEVEL);

    private final Map<EnvironmentSnapshotScanner.RoomKey, Double> rooms =
            new LinkedHashMap<>();

    public RoomThermalSavedData()
    {
    }

    private RoomThermalSavedData(List<Entry> loaded)
    {
        for (Entry entry : loaded)
        {
            if (rooms.size() >= MAX_ROOMS) break;
            if (entry == null || !Double.isFinite(entry.airTemperatureC())) continue;
            EnvironmentSnapshotScanner.RoomKey key = entry.key();
            if (!valid(key)) continue;
            rooms.put(key, entry.airTemperatureC());
        }
    }

    public static RoomThermalSavedData get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<Entry> snapshot()
    {
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<EnvironmentSnapshotScanner.RoomKey, Double> row : rooms.entrySet())
        {
            var key = row.getKey();
            entries.add(new Entry(
                    key.minX(), key.minY(), key.minZ(),
                    key.maxX(), key.maxY(), key.maxZ(), row.getValue()));
        }
        entries.sort(Comparator.comparingInt(Entry::minX)
                .thenComparingInt(Entry::minY)
                .thenComparingInt(Entry::minZ)
                .thenComparingInt(Entry::maxX)
                .thenComparingInt(Entry::maxY)
                .thenComparingInt(Entry::maxZ));
        return entries;
    }

    public void put(EnvironmentSnapshotScanner.RoomKey key, double temperatureC)
    {
        if (!valid(key) || !Double.isFinite(temperatureC)) return;

        Double previous = rooms.get(key);
        if (previous != null && Math.abs(previous - temperatureC) < MIN_SAVE_CHANGE_C)
            return;

        if (previous == null && rooms.size() >= MAX_ROOMS)
        {
            // Bounded deterministic eviction for worlds with many old rooms.
            var oldest = rooms.keySet().iterator();
            if (oldest.hasNext()) rooms.remove(oldest.next());
        }

        rooms.put(key, temperatureC);
        setDirty();
    }

    public void remove(EnvironmentSnapshotScanner.RoomKey key)
    {
        if (key != null && rooms.remove(key) != null) setDirty();
    }

    private static boolean valid(EnvironmentSnapshotScanner.RoomKey key)
    {
        if (key == null) return false;
        long dx = (long) key.maxX() - key.minX() + 1L;
        long dy = (long) key.maxY() - key.minY() + 1L;
        long dz = (long) key.maxZ() - key.minZ() + 1L;
        return dx > 0 && dy > 0 && dz > 0
                && dx <= 256 && dy <= 256 && dz <= 256;
    }
}
