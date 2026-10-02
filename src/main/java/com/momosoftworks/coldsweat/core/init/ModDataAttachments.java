package com.momosoftworks.coldsweat.core.init;

import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureData;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public final class ModDataAttachments
{
    /**
     * Fabric-native replacement for Cold Sweat's NeoForge entity-temperature
     * attachment/capability storage.
     *
     * The value persists between saves and is synchronized to clients. We use
     * the broad Fabric sync predicate for the initial correctness-first port;
     * tracking-range optimization can be tightened later without changing the
     * stored data format.
     *
     * Deliberately NOT copyOnDeath(): upstream Cold Sweat resets player
     * temperature after death and only copies the capability for non-death
     * player clones.
     */
    public static final AttachmentType<TemperatureData> ENTITY_TEMPERATURE =
            AttachmentRegistry.create(
                    ColdSweatFabric.id("entity_temperature"),
                    builder -> builder
                            .initializer(TemperatureData::new)
                            .persistent(TemperatureData.CODEC)
                            .syncWith(
                                    TemperatureData.STREAM_CODEC,
                                    AttachmentSyncPredicate.all()
                            )
            );

    public static void initialize()
    {
        ColdSweatFabric.LOGGER.info("Registering Cold Sweat persistent synchronized temperature attachment.");
    }

    private ModDataAttachments()
    {
    }
}
