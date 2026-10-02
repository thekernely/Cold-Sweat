package com.momosoftworks.coldsweat.core.init;

import com.momosoftworks.coldsweat.common.blockentity.BoilerBlockEntity;
import com.momosoftworks.coldsweat.common.blockentity.HearthBlockEntity;
import com.momosoftworks.coldsweat.common.blockentity.IceboxBlockEntity;
import com.momosoftworks.coldsweat.common.container.BoilerMenu;
import com.momosoftworks.coldsweat.common.container.HearthMenu;
import com.momosoftworks.coldsweat.common.container.IceboxMenu;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class ModMenus
{
    public static final ExtendedMenuType<HearthMenu, BlockPos> HEARTH =
            register(
                    "hearth",
                    new ExtendedMenuType<>(
                            (id, inventory, pos) -> new HearthMenu(
                                    id,
                                    inventory,
                                    requireHearth(inventory, pos)
                            ),
                            BlockPos.STREAM_CODEC
                    )
            );

    public static final ExtendedMenuType<BoilerMenu, BlockPos> BOILER =
            register(
                    "boiler",
                    new ExtendedMenuType<>(
                            (id, inventory, pos) -> new BoilerMenu(
                                    id,
                                    inventory,
                                    requireBoiler(inventory, pos)
                            ),
                            BlockPos.STREAM_CODEC
                    )
            );

    public static final ExtendedMenuType<IceboxMenu, BlockPos> ICEBOX =
            register(
                    "icebox",
                    new ExtendedMenuType<>(
                            (id, inventory, pos) -> new IceboxMenu(
                                    id,
                                    inventory,
                                    requireIcebox(inventory, pos)
                            ),
                            BlockPos.STREAM_CODEC
                    )
            );

    private static <T extends AbstractContainerMenu, D, M extends ExtendedMenuType<T, D>>
    M register(String path, M type)
    {
        Identifier id = ColdSweatFabric.id(path);
        ResourceKey<MenuType<?>> key =
                ResourceKey.create(Registries.MENU, id);
        return Registry.register(BuiltInRegistries.MENU, key, type);
    }

    public static void openHearth(ServerPlayer player, BlockPos pos)
    {
        open(
                player,
                pos,
                Component.translatable("block.cold_sweat.hearth"),
                (id, inventory, menuPlayer) -> new HearthMenu(
                        id,
                        inventory,
                        requireHearth(inventory, pos)
                )
        );
    }

    public static void openBoiler(ServerPlayer player, BlockPos pos)
    {
        open(
                player,
                pos,
                Component.translatable("block.cold_sweat.boiler"),
                (id, inventory, menuPlayer) -> new BoilerMenu(
                        id,
                        inventory,
                        requireBoiler(inventory, pos)
                )
        );
    }

    public static void openIcebox(ServerPlayer player, BlockPos pos)
    {
        open(
                player,
                pos,
                Component.translatable("block.cold_sweat.icebox"),
                (id, inventory, menuPlayer) -> new IceboxMenu(
                        id,
                        inventory,
                        requireIcebox(inventory, pos)
                )
        );
    }

    private static void open(
            ServerPlayer player,
            BlockPos pos,
            Component title,
            MenuFactory factory
    )
    {
        player.openMenu(new ExtendedMenuProvider<BlockPos>()
        {
            @Override
            public BlockPos getScreenOpeningData(ServerPlayer serverPlayer)
            {
                return pos;
            }

            @Override
            public Component getDisplayName()
            {
                return title;
            }

            @Override
            public AbstractContainerMenu createMenu(
                    int id,
                    Inventory inventory,
                    Player menuPlayer
            )
            {
                return factory.create(id, inventory, menuPlayer);
            }
        });
    }

    private static HearthBlockEntity requireHearth(
            Inventory inventory,
            BlockPos pos
    )
    {
        BlockEntity blockEntity =
                inventory.player.level().getBlockEntity(pos);
        if (blockEntity instanceof HearthBlockEntity hearth)
        {
            return hearth;
        }
        throw new IllegalStateException(
                "Missing Hearth block entity at " + pos
        );
    }

    private static BoilerBlockEntity requireBoiler(
            Inventory inventory,
            BlockPos pos
    )
    {
        BlockEntity blockEntity =
                inventory.player.level().getBlockEntity(pos);
        if (blockEntity instanceof BoilerBlockEntity boiler)
        {
            return boiler;
        }
        throw new IllegalStateException(
                "Missing Boiler block entity at " + pos
        );
    }

    private static IceboxBlockEntity requireIcebox(
            Inventory inventory,
            BlockPos pos
    )
    {
        BlockEntity blockEntity =
                inventory.player.level().getBlockEntity(pos);
        if (blockEntity instanceof IceboxBlockEntity icebox)
        {
            return icebox;
        }
        throw new IllegalStateException(
                "Missing Icebox block entity at " + pos
        );
    }

    public static void initialize()
    {
        ColdSweatFabric.LOGGER.info(
                "Registering Cold Sweat thermal machine menus."
        );
    }

    @FunctionalInterface
    private interface MenuFactory
    {
        AbstractContainerMenu create(
                int id,
                Inventory inventory,
                Player player
        );
    }

    private ModMenus()
    {
    }
}
