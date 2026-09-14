package io.aduhtkjm.mekanismheated.recipe;

import mekanism.api.recipes.ingredients.ItemStackIngredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.lwjgl.system.NonnullDefault;

import java.util.List;

@NonnullDefault
public class BasicRetroentropicArrayRecipe extends RetroentropicArrayRecipe {

    protected final ItemStackIngredient input;
    protected final ItemStackIngredient output;
    protected final int duration;

    BasicRetroentropicArrayRecipe(ItemStackIngredient input, ItemStackIngredient output, int duration) {
        this.input = input;
        this.output = output;
        this.duration = duration;
    }

    @Override
    public boolean test(ItemStack input) {
        return this.input.test(input);
    }

    @Override
    public ItemStackIngredient getInput() {
        return input;
    }

    @Override
    public ItemStack getOutput(ItemStack item) {
        var reps = output.getRepresentations();
        return reps.isEmpty() ? ItemStack.EMPTY : reps.getFirst();
    }

    @Override
    public ItemStackIngredient getOutputIngredient() {
        return output;
    }

    @Override
    public List<ItemStack> getOutputDefinition() {
        return output.getRepresentations();
    }

    @Override
    public int getDuration() {
        return duration;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.RETROENTROPIC_ARRAY.get();
    }
}
