package com.deathfrog.salvationmod.core.colony.requestable;

import com.minecolonies.api.colony.requestsystem.requestable.INonExhaustiveDeliverable;
import com.minecolonies.api.crafting.ItemStorage;
import com.deathfrog.salvationmod.core.colony.buildings.BuildingEnvironmentalLab;
import com.google.common.reflect.TypeToken;
import com.minecolonies.api.colony.requestsystem.factory.IFactoryController;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.ReflectionUtils;
import com.minecolonies.api.util.Utils;
import com.minecolonies.api.util.constant.TypeConstants;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class CorruptedItemDeliverable implements INonExhaustiveDeliverable
{
    private static final Set<TypeToken<?>> TYPE_TOKENS = (Set<TypeToken<?>>) ReflectionUtils.getSuperClasses(TypeToken.of(CorruptedItemDeliverable.class))
        .stream()
        .filter((type) -> !type.equals(TypeConstants.OBJECT))
        .collect(Collectors.toSet());

    private static final String NBT_COUNT = "Count";
    private static final String NBT_RESULT = "Result";
    private final int count;
    private final int leftOver;
    private ItemStack result;

    public CorruptedItemDeliverable(int count)
    {
        this.count = count;
        this.leftOver = 0;
    }

    public CorruptedItemDeliverable(int count, int leftOver)
    {
        this.count = count;
        this.leftOver = leftOver;
    }

    public CorruptedItemDeliverable(int count, ItemStack result)
    {
        this.count = count;
        this.result = result;
        this.leftOver = 0;
    }

    public static CompoundTag serialize(HolderLookup.@NotNull Provider provider,
        IFactoryController controller,
        CorruptedItemDeliverable corruptedItem)
    {
        CompoundTag compound = new CompoundTag();
        compound.putInt(NBT_COUNT, corruptedItem.count);
        if (!ItemStackUtils.isEmpty(corruptedItem.result))
        {
            compound.put(NBT_RESULT, corruptedItem.result.saveOptional(provider));
        }

        return compound;
    }

    public static CorruptedItemDeliverable deserialize(HolderLookup.@NotNull Provider provider,
        IFactoryController controller,
        CompoundTag compound)
    {
        int count = compound.getInt(NBT_COUNT);
        ItemStack result =
            compound.contains(NBT_RESULT) ? ItemStackUtils.deserializeFromNBT(compound.getCompound(NBT_RESULT), provider) :
                ItemStackUtils.EMPTY;
        return new CorruptedItemDeliverable(count, result);
    }

    public static void serialize(IFactoryController controller, RegistryFriendlyByteBuf buffer, CorruptedItemDeliverable input)
    {
        buffer.writeInt(input.getCount());
        buffer.writeBoolean(!ItemStackUtils.isEmpty(input.result));
        if (!ItemStackUtils.isEmpty(input.result))
        {
            Utils.serializeCodecMess(buffer, input.result);
        }
    }

    public static CorruptedItemDeliverable deserialize(IFactoryController controller, RegistryFriendlyByteBuf buffer)
    {
        int count = buffer.readInt();
        ItemStack result = buffer.readBoolean() ? Utils.deserializeCodecMess(buffer) : ItemStack.EMPTY;
        return new CorruptedItemDeliverable(count, result);
    }

    public boolean matches(@NotNull ItemStack stack)
    {
        return BuildingEnvironmentalLab.allowedItems.contains(new ItemStorage(stack));
    }

    public void setResult(@NotNull ItemStack result)
    {
        this.result = result;
    }

    public IDeliverable copyWithCount(int newCount)
    {
        return new CorruptedItemDeliverable(newCount);
    }

    public int getCount()
    {
        return this.count;
    }

    public int getMinimumCount()
    {
        return 1;
    }

    public @NotNull ItemStack getResult()
    {
        return this.result;
    }

    public Set<TypeToken<?>> getSuperClasses()
    {
        return TYPE_TOKENS;
    }

    public int getLeftOver()
    {
        return this.leftOver;
    }
}
