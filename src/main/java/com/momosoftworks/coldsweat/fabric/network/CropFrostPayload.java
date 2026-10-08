package com.momosoftworks.coldsweat.fabric.network;

import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** Bounded, clientbound snapshot segment. First segment clears the previous view. */
public record CropFrostPayload(boolean reset, List<Entry> entries)
        implements CustomPacketPayload
{
    public static final int MAX_ENTRIES = 64;
    public static final Type<CropFrostPayload> TYPE =
            new Type<>(ColdSweatFabric.id("crop_frost_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CropFrostPayload> CODEC =
            StreamCodec.of(CropFrostPayload::encode, CropFrostPayload::decode);

    public CropFrostPayload
    {
        if (entries.size() > MAX_ENTRIES)
        {
            throw new IllegalArgumentException("Frost packet exceeds 64 entries");
        }
        entries = List.copyOf(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, CropFrostPayload payload)
    {
        buf.writeBoolean(payload.reset);
        buf.writeVarInt(payload.entries.size());
        for (Entry entry : payload.entries)
        {
            buf.writeLong(entry.packedPos());
            buf.writeByte(entry.tier());
        }
    }

    private static CropFrostPayload decode(RegistryFriendlyByteBuf buf)
    {
        boolean reset = buf.readBoolean();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES)
        {
            throw new IllegalArgumentException("Invalid frost payload length: " + count);
        }
        List<Entry> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            long pos = buf.readLong();
            int tier = buf.readUnsignedByte();
            if (tier < 1 || tier > 3)
            {
                throw new IllegalArgumentException("Invalid frost tier: " + tier);
            }
            result.add(new Entry(pos, (byte) tier));
        }
        return new CropFrostPayload(reset, result);
    }

    public record Entry(long packedPos, byte tier) { }
}
