package com.momosoftworks.coldsweat.api.registry;

import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Registry for Cold Sweat temperature modifier factories.
 *
 * This mirrors upstream's logical registry without depending on NeoForge's
 * registration event bus. Fabric initialization fills this registry directly.
 */
public final class TempModifierRegistry
{
    private static final Map<Identifier, TempModifierHolder> TEMP_MODIFIERS =
            new LinkedHashMap<>();

    public static Map<Identifier, TempModifierHolder> getEntries()
    {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(TEMP_MODIFIERS)
        );
    }

    public static synchronized void register(
            Identifier id,
            Supplier<? extends TempModifier> supplier
    )
    {
        if (id == null)
        {
            throw new IllegalArgumentException("TempModifier id cannot be null");
        }
        if (supplier == null)
        {
            throw new IllegalArgumentException(
                    "TempModifier supplier cannot be null for " + id
            );
        }

        TempModifierHolder holder = new TempModifierHolder(supplier, id);

        if (TEMP_MODIFIERS.containsKey(id))
        {
            throw new IllegalStateException(
                    "Duplicate TempModifier id: " + id
            );
        }

        for (TempModifierHolder existing : TEMP_MODIFIERS.values())
        {
            if (existing.getModifierClass() == holder.getModifierClass())
            {
                throw new IllegalStateException(
                        "TempModifier class "
                                + holder.getModifierClass().getName()
                                + " is already registered as "
                                + existing.getId()
                );
            }
        }

        TEMP_MODIFIERS.put(id, holder);
    }

    public static synchronized void flush()
    {
        TEMP_MODIFIERS.clear();
    }

    public static Optional<TempModifier> getValue(Identifier id)
    {
        TempModifierHolder holder = TEMP_MODIFIERS.get(id);
        return holder != null
                ? Optional.of(holder.get())
                : Optional.empty();
    }

    public static Identifier getKey(TempModifier modifier)
    {
        TempModifierHolder holder = getHolder(modifier);
        return holder != null ? holder.getId() : null;
    }

    public static boolean containsKey(Identifier id)
    {
        return TEMP_MODIFIERS.containsKey(id);
    }

    public static TempModifierHolder getHolder(TempModifier modifier)
    {
        if (modifier == null)
        {
            return null;
        }

        for (TempModifierHolder holder : TEMP_MODIFIERS.values())
        {
            if (holder.getModifierClass() == modifier.getClass())
            {
                return holder;
            }
        }
        return null;
    }

    public static final class TempModifierHolder
    {
        private final Supplier<? extends TempModifier> supplier;
        private final Class<? extends TempModifier> modifierClass;
        private final Identifier id;

        private TempModifierHolder(
                Supplier<? extends TempModifier> supplier,
                Identifier id
        )
        {
            this.supplier = supplier;

            TempModifier probe = supplier.get();
            if (probe == null)
            {
                throw new IllegalStateException(
                        "TempModifier supplier returned null for " + id
                );
            }

            this.modifierClass = probe.getClass();
            this.id = id;
        }

        public TempModifier get()
        {
            TempModifier modifier = supplier.get();
            if (modifier == null)
            {
                throw new IllegalStateException(
                        "TempModifier supplier returned null for " + id
                );
            }
            return modifier;
        }

        public Class<? extends TempModifier> getModifierClass()
        {
            return modifierClass;
        }

        public Identifier getId()
        {
            return id;
        }

        @Override
        public String toString()
        {
            return "TempModifierHolder{"
                    + modifierClass.getName()
                    + " -> "
                    + id
                    + "}";
        }
    }

    private TempModifierRegistry()
    {
    }
}
