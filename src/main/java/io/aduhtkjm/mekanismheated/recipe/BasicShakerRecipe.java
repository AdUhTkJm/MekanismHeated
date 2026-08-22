package io.aduhtkjm.mekanismheated.recipe;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import io.aduhtkjm.mekanismheated.Mod;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.Contract;

@NothingNullByDefault
public class BasicShakerRecipe extends ShakerRecipe {

    /**
     * Accepts either a single item stack or a list of 1-3 item stacks for the output.
     */
    public static final Codec<List<ItemStack>> OUTPUT_CODEC = Codec.either(ItemStack.CODEC, ItemStack.CODEC.listOf(1, 3)).xmap(
          either -> either.map(List::of, List::copyOf),
          outputs -> outputs.size() == 1 ? Either.left(outputs.getFirst()) : Either.right(outputs));

    private static final Holder<Item> SHAKER = DeferredHolder.create(Registries.ITEM,
          ResourceLocation.fromNamespaceAndPath(Mod.MODID, "shaker"));

    protected final ItemStackIngredient input;
    protected final Optional<FluidStackIngredient> fluidInput;
    protected final List<ItemStack> outputs;

    /**
     * @param input      Item input.
     * @param fluidInput Optional fluid input; empty if the recipe does not consume any fluid.
     * @param outputs    1-3 item stacks to produce.
     */
    public BasicShakerRecipe(ItemStackIngredient input, Optional<FluidStackIngredient> fluidInput, List<ItemStack> outputs) {
        this.input = Objects.requireNonNull(input, "Item input cannot be null.");
        this.fluidInput = Objects.requireNonNull(fluidInput, "Fluid input cannot be null.");
        Objects.requireNonNull(outputs, "Outputs cannot be null.");
        if (outputs.isEmpty() || outputs.size() > 3) {
            throw new IllegalArgumentException("Shaker recipes must have between one and three outputs.");
        }
        for (ItemStack output : outputs) {
            Objects.requireNonNull(output, "Output cannot be null.");
            if (output.isEmpty()) {
                throw new IllegalArgumentException("Output cannot be empty.");
            }
        }
        this.outputs = List.copyOf(outputs);
    }

    @Override
    public boolean test(ItemStack itemStack, FluidStack fluidStack) {
        return input.test(itemStack) && (!hasFluidInput() || fluidInput.orElseThrow().test(fluidStack));
    }

    @Override
    public ItemStackIngredient getInput() {
        return input;
    }

    @Override
    public Optional<FluidStackIngredient> getFluidInput() {
        return fluidInput;
    }

    @Override
    @Contract(value = "_, _ -> new", pure = true)
    public List<ItemStack> getOutput(ItemStack input, FluidStack fluidStack) {
        return outputs.stream().map(ItemStack::copy).toList();
    }

    @Override
    public List<ItemStack> getOutputDefinition() {
        return outputs;
    }

    /**
     * For Serializer use. DO NOT MODIFY RETURN VALUE.
     *
     * @return the uncopied basic outputs
     */
    public List<ItemStack> getOutputRaw() {
        return outputs;
    }

    @Override
    public RecipeSerializer<BasicShakerRecipe> getSerializer() {
        return ModRecipeSerializers.SHAKING.get();
    }

    @Override
    public RecipeType<ShakerRecipe> getType() {
        return ModRecipeTypes.TYPE_SHAKING.value();
    }

    @Override
    public String getGroup() {
        return "shaker";
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(SHAKER);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BasicShakerRecipe other = (BasicShakerRecipe) o;
        return input.equals(other.input) && fluidInput.equals(other.fluidInput) && ItemStack.listMatches(outputs, other.outputs);
    }

    @Override
    public int hashCode() {
        int result = input.hashCode();
        result = 31 * result + fluidInput.hashCode();
        result = 31 * result + ItemStack.hashStackList(outputs);
        return result;
    }
}
