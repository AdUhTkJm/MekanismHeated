package io.aduhtkjm.mekanismheated.recipe;

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
public class BasicCondenserRecipe extends CondenserRecipe {

    private static final Holder<Item> CONDENSER = DeferredHolder.create(Registries.ITEM,
          ResourceLocation.fromNamespaceAndPath(Mod.MODID, "condenser"));

    protected final FluidStackIngredient fluidInput;
    protected final Optional<ItemStackIngredient> itemInput;
    protected final ItemStackIngredient output;

    public BasicCondenserRecipe(FluidStackIngredient fluidInput, Optional<ItemStackIngredient> itemInput, ItemStackIngredient output) {
        this.fluidInput = Objects.requireNonNull(fluidInput, "Fluid input cannot be null.");
        this.itemInput = Objects.requireNonNull(itemInput, "Item input cannot be null.");
        Objects.requireNonNull(output, "Output cannot be null.");
        this.output = output;
    }

    @Override
    public boolean test(FluidStack fluidStack, ItemStack itemStack) {
        return fluidInput.test(fluidStack) && (!hasItemInput() || itemInput.orElseThrow().test(itemStack));
    }

    @Override
    public FluidStackIngredient getFluidInput() {
        return fluidInput;
    }

    @Override
    public Optional<ItemStackIngredient> getItemInput() {
        return itemInput;
    }

    @Override
    @Contract(value = "_, _ -> new", pure = true)
    public ItemStack getOutput(FluidStack fluid, ItemStack item) {
        List<ItemStack> reps = output.getRepresentations();
        return reps.isEmpty() ? ItemStack.EMPTY : reps.getFirst().copy();
    }

    @Override
    public ItemStackIngredient getOutputIngredient() {
        return output;
    }

    @Override
    public List<ItemStack> getOutputDefinition() {
        return output.getRepresentations();
    }

    /**
     * For Serializer use. DO NOT MODIFY RETURN VALUE.
     */
    public ItemStackIngredient getOutputRaw() {
        return output;
    }

    @Override
    public RecipeSerializer<BasicCondenserRecipe> getSerializer() {
        return ModRecipeSerializers.CONDENSING.get();
    }

    @Override
    public RecipeType<CondenserRecipe> getType() {
        return ModRecipeTypes.TYPE_CONDENSING.value();
    }

    @Override
    public String getGroup() {
        return "condenser";
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(CONDENSER);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BasicCondenserRecipe other = (BasicCondenserRecipe) o;
        return fluidInput.equals(other.fluidInput) && itemInput.equals(other.itemInput) && output.equals(other.output);
    }

    @Override
    public int hashCode() {
        int result = fluidInput.hashCode();
        result = 31 * result + itemInput.hashCode();
        result = 31 * result + output.hashCode();
        return result;
    }
}
