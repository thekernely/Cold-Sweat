package com.momosoftworks.coldsweat.core.init;

import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds
{
    public static final Holder<SoundEvent> FREEZE = register("entity.player.damage.freeze");

    public static final Holder<SoundEvent> SOUL_LAMP_ON = register("item.soulspring_lamp.on");
    public static final Holder<SoundEvent> SOUL_LAMP_OFF = register("item.soulspring_lamp.off");

    public static final Holder<SoundEvent> WATERSKIN_POUR = register("item.waterskin.pour");
    public static final Holder<SoundEvent> WATERSKIN_FILL = register("item.waterskin.fill");

    public static final Holder<SoundEvent> HEARTH_DEPLETE = register("block.hearth.fuel_deplete");
    public static final Holder<SoundEvent> BOILER_DEPLETE = register("block.boiler.fuel_deplete");
    public static final Holder<SoundEvent> ICEBOX_DEPLETE = register("block.icebox.fuel_deplete");
    public static final Holder<SoundEvent> ICEBOX_OPEN = register("block.icebox.open");
    public static final Holder<SoundEvent> ICEBOX_CLOSE = register("block.icebox.close");

    public static final Holder<SoundEvent> CHAMELEON_AMBIENT = register("entity.chameleon.ambient");
    public static final Holder<SoundEvent> CHAMELEON_HURT = register("entity.chameleon.hurt");
    public static final Holder<SoundEvent> CHAMELEON_DEATH = register("entity.chameleon.death");
    public static final Holder<SoundEvent> CHAMELEON_FIND = register("entity.chameleon.find");
    public static final Holder<SoundEvent> CHAMELEON_TONGUE_IN = register("entity.chameleon.tongue.in");
    public static final Holder<SoundEvent> CHAMELEON_TONGUE_OUT = register("entity.chameleon.tongue.out");
    public static final Holder<SoundEvent> CHAMELEON_SHED = register("entity.chameleon.shed");
    public static final Holder<SoundEvent> CHAMELEON_SHED_READY = register("entity.chameleon.shed.ready");
    public static final Holder<SoundEvent> CHAMELEON_SHED_FAIL = register("entity.chameleon.shed.fail");

    public static final Holder<SoundEvent> ARMOR_EQUIP_CHAMELEON = register("item.armor.equip_chameleon_scale");

    public static final Holder<SoundEvent> BUCKET_FILL_SLUSH = register("item.bucket.fill_slush");
    public static final Holder<SoundEvent> BUCKET_EMPTY_SLUSH = register("item.bucket.empty_slush");

    private static Holder<SoundEvent> register(String path)
    {
        Identifier id = ColdSweatFabric.id(path);
        return Registry.registerForHolder(
                BuiltInRegistries.SOUND_EVENT,
                id,
                SoundEvent.createVariableRangeEvent(id)
        );
    }

    public static void initialize()
    {
        ColdSweatFabric.LOGGER.info("Registering Cold Sweat sound events.");
    }

    private ModSounds()
    {
    }
}
