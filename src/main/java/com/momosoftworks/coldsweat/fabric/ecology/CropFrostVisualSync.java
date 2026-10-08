package com.momosoftworks.coldsweat.fabric.ecology;

import com.momosoftworks.coldsweat.fabric.network.CropFrostPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Server-authoritative visual-only crop frost snapshots; no chunk tickets. */
public final class CropFrostVisualSync
{
    private static final long UPDATE_TICKS = 40L;
    private static final int RADIUS_BLOCKS = 32;
    private static final int MAX_VISIBLE = 256;

    private CropFrostVisualSync() { }

    public static void initialize()
    {
        PayloadTypeRegistry.clientboundPlay().register(
                CropFrostPayload.TYPE, CropFrostPayload.CODEC);
        ServerTickEvents.END_LEVEL_TICK.register(CropFrostVisualSync::tickLevel);
    }

    private static void tickLevel(ServerLevel level)
    {
        if (Math.floorMod(level.getGameTime(), UPDATE_TICKS) != 0) return;
        for (ServerPlayer player : level.players())
        {
            if (!ServerPlayNetworking.canSend(player, CropFrostPayload.TYPE)) continue;

            Map<Long, Byte> tiers = CropClimateRuntime.nearbyVisualTiers(
                    level, player.blockPosition(), RADIUS_BLOCKS, MAX_VISIBLE);
            List<CropFrostPayload.Entry> entries = new ArrayList<>(tiers.size());
            tiers.forEach((pos, tier) -> entries.add(
                    new CropFrostPayload.Entry(pos, tier)));

            if (entries.isEmpty())
            {
                // Empty reset revokes every stale overlay on this client.
                ServerPlayNetworking.send(player, new CropFrostPayload(true, List.of()));
                continue;
            }

            boolean first = true;
            for (int offset = 0; offset < entries.size(); offset += CropFrostPayload.MAX_ENTRIES)
            {
                int end = Math.min(entries.size(), offset + CropFrostPayload.MAX_ENTRIES);
                ServerPlayNetworking.send(player,
                        new CropFrostPayload(first, entries.subList(offset, end)));
                first = false;
            }
        }
    }
}
