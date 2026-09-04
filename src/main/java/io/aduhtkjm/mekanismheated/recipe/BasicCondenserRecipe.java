package io.aduhtkjm.mekanismheated.recipe;

import com.mojang.serialization.Codec;
import io.aduhtkjm.mekanismheated.Mod;
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
    protected final ItemStack output;

    public BasicCondenserRecipe(FluidStackIngredient fluidInput, Optional<ItemStackIngredient> itemInput, ItemStack output) {
        this.fluidInput = Objects.requireNonNull(fluidInput, "Fluid input cannot be null.");
        this.itemInput = Objects.requireNonNull(itemInput, "Item input cannot be null.");
        Objects.requireNonNull(output, "Output cannot be null.");
        if (output.isEmpty()) {
            throw new IllegalArgumentException("Output cannot be empty.");
        }
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
        return output.copy();
    }

    @Override
    public ItemStack getOutputDefinition() {
        return output;
    }

    /**
     * For Serializer use. DO NOT MODIFY RETURN VALUE.
     */
    public ItemStack getOutputRaw() {
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
        return fluidInput.equals(other.fluidInput) && itemInput.equals(other.itemInput) && ItemStack.matches(output, other.output);
    }

    @Override
    public int hashCode() {
        int result = fluidInput.hashCode();
        result = 31 * result + itemInput.hashCode();
        result = 31 * result + output.hashCode();
        return result;
    }
}
