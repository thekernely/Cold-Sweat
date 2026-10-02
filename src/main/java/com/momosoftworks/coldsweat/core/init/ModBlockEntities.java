package com.momosoftworks.coldsweat.core.init;

import com.momosoftworks.coldsweat.common.blockentity.BoilerBlockEntity;
import com.momosoftworks.coldsweat.common.blockentity.HearthBlockEntity;
import com.momosoftworks.coldsweat.common.blockentity.IceboxBlockEntity;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;

public final class ModBlockEntities
{
    public static final BlockEntityType<HearthBlockEntity> HEARTH = register(
            "hearth",
            new BlockEntityType<>(
                    HearthBlockEntity::new,
                    Set.of(ModBlocks.HEARTH_BOTTOM)
            )
    );

    public static final BlockEntityType<BoilerBlockEntity> BOILER = register(
            "boiler",
            new BlockEntityType<>(
                    BoilerBlockEntity::new,
                    Set.of(ModBlocks.BOILER)
            )
    );

    public static final BlockEntityType<IceboxBlockEntity> ICEBOX = register(
            "icebox",
            new BlockEntityType<>(
                    IceboxBlockEntity::new,
                    Set.of(ModBlocks.ICEBOX)
            )
    );

    private static <T extends BlockEntityType<?>> T register(String path, T type)
    {
        Identifier id = ColdSweatFabric.id(path);
        ResourceKey<BlockEntityType<?>> key = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, id);
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, key, type);
    }

    public static void initialize()
    {
        ColdSweatFabric.LOGGER.info("Registering Cold Sweat thermal block entities.");
    }

    private ModBlockEntities()
    {
    }
}
