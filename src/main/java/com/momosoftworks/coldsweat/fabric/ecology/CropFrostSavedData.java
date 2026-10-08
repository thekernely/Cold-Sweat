package com.momosoftworks.coldsweat.fabric.ecology;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * World/dimension-persistent crop frost condition. Stores no timestamps:
 * ticking-time bookkeeping is intentionally transient and cannot backfill
 * time spent in unloaded chunks or across a server restart.
 *
 * SavedData.setDirty() schedules ordinary world saves; nothing writes files
 * on each random tick. Never retain zero-stress records.
 */
public final class CropFrostSavedData extends SavedData
{
    public static final int MAX_ENTRIES = 16_384;
    private static final float MIN_SAVED_STRESS = 0.0001F;

    public record Entry(long position, String blockId, float stress, int age)
    {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.LONG.fieldOf("pos").forGetter(Entry::position),
                        Codec.STRING.fieldOf("block").forGetter(Entry::blockId),
                        Codec.FLOAT.fieldOf("stress").forGetter(Entry::stress),
                        Codec.INT.optionalFieldOf("age", -1).forGetter(Entry::age)
                ).apply(instance, Entry::new));
    }

    public static final Codec<CropFrostSavedData> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Entry.CODEC.listOf().optionalFieldOf("crops", List.of())
                                    .forGetter(CropFrostSavedData::snapshot)
                    ).apply(instance, CropFrostSavedData::new));

    public static final SavedDataType<CropFrostSavedData> TYPE =
            new SavedDataType<>(
                    ColdSweatFabric.id("crop_frost_stress"),
                    CropFrostSavedData::new,
                    CODEC,
                    DataFixTypes.LEVEL);

    private final Map<Long, Entry> crops = new HashMap<>();

    public CropFrostSavedData()
    {
    }

    private CropFrostSavedData(List<Entry> loaded)
    {
        for (Entry row : loaded)
        {
            if (crops.size() >= MAX_ENTRIES) break;
            if (row == null
                    || row.blockId() == null
                    || row.blockId().isBlank()
                    || !Float.isFinite(row.stress())
                    || row.stress() < MIN_SAVED_STRESS)
            {
                continue;
            }

            crops.put(row.position(), new Entry(
                    row.position(), row.blockId(),
                    Math.min(1.0F, row.stress()), row.age()));
        }
    }

    public static CropFrostSavedData get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<Entry> snapshot()
    {
        // Stable ordering for deterministic, verifiable save diffs.
        return crops.values().stream()
                .sorted(Comparator.comparingLong(Entry::position))
                .toList();
    }

    public void update(long packed, Block block, double stress, int age)
    {
        if (!Double.isFinite(stress) || stress < MIN_SAVED_STRESS)
        {
            remove(packed);
            return;
        }

        String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
        float stored = (float) Math.max(0.0, Math.min(1.0, stress));
        Entry old = crops.get(packed);

        if (old != null
                && old.blockId().equals(blockId)
                && old.age() == age
                && Math.abs(old.stress() - stored) < 0.00001F)
        {
            return;
        }

        if (old == null && crops.size() >= MAX_ENTRIES)
        {
            // No unbounded accumulation of records in heavily farmed worlds.
            return;
        }

        crops.put(packed, new Entry(packed, blockId, stored, age));
        setDirty();
    }

    public void remove(long packed)
    {
        if (crops.remove(packed) != null)
        {
            setDirty();
        }
    }
}
