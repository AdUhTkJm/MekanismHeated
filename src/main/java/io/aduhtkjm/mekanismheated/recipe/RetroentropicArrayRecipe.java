package io.aduhtkjm.mekanismheated.recipe;

import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.lwjgl.system.NonnullDefault;

import java.util.List;
import java.util.Optional;

@NonnullDefault
public abstract class RetroentropicArrayRecipe extends MekanismRecipe<SingleRecipeInput> {
    public abstract boolean test(ItemStack input);

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return !isIncomplete() && test(input.item());
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.TYPE_RETROENTROPIC_ARRAY.get();
    }

    public abstract ItemStackIngredient getInput();

    public abstract ItemStack getOutput(ItemStack item);

    public abstract ItemStackIngredient getOutputIngredient();

    public abstract List<ItemStack> getOutputDefinition();

    public abstract int getDuration();

    @Override
    public boolean isIncomplete() {
        return getOutputIngredient().hasNoMatchingInstances() || getInput().hasNoMatchingInstances();
    }
}
