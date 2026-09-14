package dev.futuretech.item;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/** Immutable item component; mutable FluidStacks are only created at the storage boundary. */
public record StoredTankFluid(FluidResource resource, int amount) {
    public static final StoredTankFluid EMPTY = new StoredTankFluid(FluidResource.EMPTY, 0);
    public static final Codec<StoredTankFluid> CODEC = FluidStack.OPTIONAL_CODEC.xmap(StoredTankFluid::of, StoredTankFluid::toStack);
    public static final StreamCodec<RegistryFriendlyByteBuf, StoredTankFluid> STREAM_CODEC =
            FluidStack.OPTIONAL_STREAM_CODEC.map(StoredTankFluid::of, StoredTankFluid::toStack);

    public StoredTankFluid {
        if (amount < 0) throw new IllegalArgumentException("Negative fluid amount");
        if (resource.isEmpty() || amount == 0) {
            resource = FluidResource.EMPTY;
            amount = 0;
        }
    }

    public static StoredTankFluid of(FluidStack stack) {
        return stack.isEmpty() ? EMPTY : new StoredTankFluid(FluidResource.of(stack), stack.getAmount());
    }

    public FluidStack toStack() { return resource.toStack(amount); }
}
