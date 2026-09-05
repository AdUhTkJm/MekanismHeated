package io.aduhtkjm.mekanismheated.recipe;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Contract;

@NothingNullByDefault
public class BasicCrushingRecipe extends CrushingRecipe {

    protected final ItemStackIngredient input;
    protected final Block catalyst;
    protected final ItemStack output;

    /**
     * @param input    The item that may be crushed.
     * @param catalyst The block that, when it lands on the input item, triggers this recipe.
     * @param output   The item produced. Its count is scaled proportionally to the count of the crushed input stack.
     */
    public BasicCrushingRecipe(ItemStackIngredient input, Block catalyst, ItemStack output) {
        this.input = Objects.requireNonNull(input, "Input cannot be null.");
        this.catalyst = Objects.requireNonNull(catalyst, "Catalyst cannot be null.");
        Objects.requireNonNull(output, "Output cannot be null.");
        if (output.isEmpty()) {
            throw new IllegalArgumentException("Output cannot be empty.");
        }
        this.output = output.copy();
    }

    @Override
    public ItemStackIngredient getInput() {
        return input;
    }

    @Override
    public Block getCatalyst() {
        return catalyst;
    }

    @Override
    @Contract(value = "_ -> new", pure = true)
    public ItemStack getOutput(ItemStack input) {
        ItemStack result = output.copy();
        long nominal = this.input.getNeededAmount(input);
        // Scale the output proportionally to the number of input items that were actually crushed.
        if (nominal > 0 && input.getCount() != nominal) {
            result.setCount(Math.max(1, (int) Math.round((double) result.getCount() * input.getCount() / nominal)));
        }
        return result;
    }

    @Override
    public List<ItemStack> getOutputDefinition() {
        return Collections.singletonList(output);
    }

    /**
     * For Serializer use. DO NOT MODIFY RETURN VALUE.
     *
     * @return the uncopied basic output
     */
    public ItemStack getOutputRaw() {
        return output;
    }

    @Override
    public RecipeSerializer<BasicCrushingRecipe> getSerializer() {
        return ModRecipeSerializers.CRUSHING.get();
    }

    @Override
    public RecipeType<CrushingRecipe> getType() {
        return ModRecipeTypes.TYPE_CRUSHING.value();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BasicCrushingRecipe other = (BasicCrushingRecipe) o;
        return input.equals(other.input) && catalyst == other.catalyst && ItemStack.matches(output, other.output);
    }

    @Override
    public int hashCode() {
        int result = input.hashCode();
        result = 31 * result + System.identityHashCode(catalyst);
        result = 31 * result + ItemStack.hashItemAndComponents(output);
        result = 31 * result + output.getCount();
        return result;
    }
}
