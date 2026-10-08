package com.momosoftworks.coldsweat.fabric.client;

import com.momosoftworks.coldsweat.fabric.network.CropFrostPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.HashMap;
import java.util.Map;

/** Client display cache only; the server owns the physical frost state. */
public final class CropFrostClientState
{
    private static final int MAX_ENTRIES = 256;
    private static final Map<Long, Byte> TIERS = new HashMap<>();
    private static ClientLevel currentLevel;
    private static long lastSnapshotMillis;

    private CropFrostClientState() { }

    public static void initialize()
    {
        ClientPlayNetworking.registerGlobalReceiver(CropFrostPayload.TYPE,
                (packet, context) -> {
                    // Fabric's 26.2 client payload callback executes on the client thread.
                    accept(packet, context.client().level);
                });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    private static void accept(CropFrostPayload packet, ClientLevel level)
    {
        if (level == null) return;
        if (level != currentLevel || packet.reset())
        {
            TIERS.clear();
            currentLevel = level;
        }
        for (CropFrostPayload.Entry entry : packet.entries())
        {
            if (TIERS.size() >= MAX_ENTRIES && !TIERS.containsKey(entry.packedPos())) break;
            TIERS.put(entry.packedPos(), entry.tier());
        }
        lastSnapshotMillis = System.currentTimeMillis();
    }

    public static Map<Long, Byte> tiers()
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level != currentLevel
                || System.currentTimeMillis() - lastSnapshotMillis > 10_000L)
        {
            clear();
        }
        return TIERS;
    }

    public static void clear()
    {
        TIERS.clear();
        currentLevel = null;
        lastSnapshotMillis = 0L;
    }
}
