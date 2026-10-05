package com.momosoftworks.coldsweat.fabric.hydration;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.momosoftworks.coldsweat.api.util.Hydration;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M8.7 explicit food hydration registry.
 *
 * Hydration values are NEVER inferred from item names. Values come from:
 * 1. datapack JSON files under data/<namespace>/food_hydration/*.json
 * 2. explicit programmatic registrations through register(...)
 *
 * Programmatic registrations override datapack values. Datapack files are
 * processed in deterministic identifier order; later files may override an
 * earlier value for the same item id.
 */
public final class FoodHydrationRegistry
{
    private static final Gson GSON =
            new Gson();

    private static final String DIRECTORY =
            "food_hydration";

    private static final Identifier RELOAD_LISTENER_ID =
            ColdSweatFabric.id("food_hydration");

    private static final Map<Identifier, Double> PROGRAMMATIC_VALUES =
            new ConcurrentHashMap<>();

    private static volatile Map<Identifier, Double> dataValues =
            Map.of();

    private FoodHydrationRegistry()
    {
    }

    public static void initialize()
    {
        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(
                        new SimpleSynchronousResourceReloadListener()
                        {
                            @Override
                            public Identifier getFabricId()
                            {
                                return RELOAD_LISTENER_ID;
                            }

                            @Override
                            public void onResourceManagerReload(
                                    ResourceManager resourceManager
                            )
                            {
                                FoodHydrationRegistry.reload(
                                        resourceManager
                                );
                            }
                        }
                );

        ColdSweatFabric.LOGGER.info(
                "Registering Cold Sweat food hydration data hooks."
        );
    }

    /**
     * Public integration hook for mods that prefer code registration.
     *
     * A value of 0 explicitly disables hydration for this id.
     */
    public static void register(
            Identifier itemId,
            double hydration
    )
    {
        validateHydration(
                itemId,
                hydration
        );

        PROGRAMMATIC_VALUES.put(
                itemId,
                hydration
        );
    }

    public static double hydrationFor(
            ItemStack stack
    )
    {
        if (stack.isEmpty())
        {
            return 0.0;
        }

        Identifier itemId =
                BuiltInRegistries.ITEM.getKey(
                        stack.getItem()
                );

        Double programmatic =
                PROGRAMMATIC_VALUES.get(itemId);

        if (programmatic != null)
        {
            return programmatic;
        }

        return dataValues.getOrDefault(
                itemId,
                0.0
        );
    }

    public static void applyConsumedFood(
            ServerPlayer player,
            ItemStack consumedStack
    )
    {
        double hydration =
                hydrationFor(consumedStack);

        if (hydration <= 0.0)
        {
            return;
        }

        Hydration.add(
                player,
                hydration
        );
    }

    private static void reload(
            ResourceManager resourceManager
    )
    {
        Map<Identifier, Double> loaded =
                new LinkedHashMap<>();

        resourceManager.listResources(
                        DIRECTORY,
                        id -> id.getPath().endsWith(".json")
                )
                .entrySet()
                .stream()
                .sorted(
                        Comparator.comparing(
                                entry -> entry.getKey().toString()
                        )
                )
                .forEach(entry ->
                        loadResource(
                                entry.getKey(),
                                entry.getValue(),
                                loaded
                        )
                );

        dataValues =
                Map.copyOf(loaded);

        ColdSweatFabric.LOGGER.info(
                "Loaded {} explicit food hydration value(s).",
                dataValues.size()
        );
    }

    private static void loadResource(
            Identifier resourceId,
            Resource resource,
            Map<Identifier, Double> output
    )
    {
        try (BufferedReader reader =
                     resource.openAsReader())
        {
            JsonObject root =
                    GSON.fromJson(
                            reader,
                            JsonObject.class
                    );

            if (root == null
                    || !root.has("values")
                    || !root.get("values").isJsonObject())
            {
                throw new JsonParseException(
                        "missing object: values"
                );
            }

            for (Map.Entry<String, JsonElement> entry :
                    root.getAsJsonObject("values")
                            .entrySet())
            {
                Identifier itemId =
                        Identifier.tryParse(
                                entry.getKey()
                        );

                if (itemId == null)
                {
                    throw new JsonParseException(
                            "invalid item id: "
                                    + entry.getKey()
                    );
                }

                if (!entry.getValue().isJsonPrimitive()
                        || !entry.getValue()
                        .getAsJsonPrimitive()
                        .isNumber())
                {
                    throw new JsonParseException(
                            "hydration for "
                                    + itemId
                                    + " must be numeric"
                    );
                }

                double hydration =
                        entry.getValue()
                                .getAsDouble();

                validateHydration(
                        itemId,
                        hydration
                );

                output.put(
                        itemId,
                        hydration
                );
            }
        }
        catch (IOException | RuntimeException exception)
        {
            ColdSweatFabric.LOGGER.warn(
                    "Could not load food hydration data {}: {}",
                    resourceId,
                    exception.getMessage()
            );
        }
    }

    private static void validateHydration(
            Identifier itemId,
            double hydration
    )
    {
        if (!Double.isFinite(hydration)
                || hydration < 0.0
                || hydration > Hydration.MAX_HYDRATION)
        {
            throw new IllegalArgumentException(
                    "Food hydration for "
                            + itemId
                            + " must be finite and between 0 and "
                            + Hydration.MAX_HYDRATION
            );
        }
    }
}
