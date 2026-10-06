package com.momosoftworks.coldsweat.common.capability.insulation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.momosoftworks.coldsweat.api.insulation.AdaptiveInsulation;
import com.momosoftworks.coldsweat.api.insulation.Insulation;
import com.momosoftworks.coldsweat.api.registry.InsulationRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persistent sewn-insulation state stored directly on an armor ItemStack.
 *
 * Upstream stores ItemStack + InsulatorData pairs. The complete InsulatorData
 * requirement/config graph is deliberately deferred; this Fabric boundary
 * stores the resolved insulation values instead. It preserves the behavior
 * required by the built-in M5 insulation ingredients while remaining ready for
 * a later config loader to resolve richer definitions before insertion.
 */
public record ItemInsulationCap(List<Entry> insulation)
{
    public static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.CODEC.fieldOf("item").forGetter(Entry::item),
            Insulation.getCodec().listOf().fieldOf("insulation").forGetter(Entry::insulation)
    ).apply(instance, Entry::new));

    public static final Codec<ItemInsulationCap> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ENTRY_CODEC.listOf().fieldOf("insulation").forGetter(ItemInsulationCap::insulation)
    ).apply(instance, ItemInsulationCap::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemInsulationCap> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public ItemInsulationCap()
    {
        this(List.of());
    }

    public ItemInsulationCap
    {
        insulation = List.copyOf(insulation);
    }

    public List<Entry> getInsulation()
    {
        return insulation;
    }

    public List<Insulation> getInsulators()
    {
        List<Insulation> result = new ArrayList<>();
        for (Entry entry : insulation)
        {
            result.addAll(Insulation.deepCopy(entry.insulation()));
        }
        return List.copyOf(result);
    }

    public ItemInsulationCap addInsulationItem(ItemStack stack)
    {
        List<Insulation> values = InsulationRegistry.getItemInsulation(stack);
        if (values.isEmpty())
        {
            return this;
        }

        ItemStack storedStack = stack.copy();
        storedStack.setCount(1);

        List<Entry> entries = new ArrayList<>(insulation);
        entries.add(new Entry(storedStack, values));
        return new ItemInsulationCap(entries);
    }

    public ItemInsulationCap removeInsulationItem(ItemStack stack)
    {
        List<Entry> entries = new ArrayList<>(insulation);
        for (int i = 0; i < entries.size(); i++)
        {
            if (ItemStack.isSameItemSameComponents(entries.get(i).item(), stack))
            {
                entries.remove(i);
                break;
            }
        }
        return new ItemInsulationCap(entries);
    }

    public ItemInsulationCap removeLast()
    {
        if (insulation.isEmpty())
        {
            return this;
        }
        List<Entry> entries = new ArrayList<>(insulation);
        entries.removeLast();
        return new ItemInsulationCap(entries);
    }

    public Optional<ItemStack> getLastInsulationItem()
    {
        return insulation.isEmpty()
                ? Optional.empty()
                : Optional.of(insulation.getLast().item().copy());
    }

    public ItemInsulationCap adapt(double worldTemp, double minTemp, double maxTemp)
    {
        boolean changed = false;
        List<Entry> adaptedEntries = new ArrayList<>(insulation.size());

        for (Entry entry : insulation)
        {
            List<Insulation> values = Insulation.deepCopy(entry.insulation());
            for (Insulation value : values)
            {
                if (value instanceof AdaptiveInsulation adaptive)
                {
                    double newFactor = AdaptiveInsulation.calculateChange(
                            adaptive,
                            worldTemp,
                            minTemp,
                            maxTemp
                    );
                    if (Double.compare(adaptive.getFactor(), newFactor) != 0)
                    {
                        adaptive.setFactor(newFactor);
                        changed = true;
                    }
                }
            }
            adaptedEntries.add(new Entry(entry.item().copy(), values));
        }

        return changed ? new ItemInsulationCap(adaptedEntries) : this;
    }

    public record Entry(ItemStack item, List<Insulation> insulation)
    {
        public Entry
        {
            insulation = List.copyOf(insulation);
        }
    }
}
