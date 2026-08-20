package io.aduhtkjm.mekanismheated.recipes;

import io.aduhtkjm.mekanismheated.MekanismHeated;
import java.util.function.Predicate;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

/**
 * Input: ItemStack
 * <br>
 * Output: Heat (in heat units)
 *
 * @apiNote Fuel conversion recipes are used by the Heat Smelter's fuel slot to convert items into heat.
 */
@NothingNullByDefault
public abstract class ItemStackToHeatRecipe extends MekanismRecipe<SingleRecipeInput> implements Predicate<@NotNull ItemStack> {

    private static final Holder<Item> HEAT_SMELTER = DeferredHolder.create(Registries.ITEM,
          ResourceLocation.fromNamespaceAndPath(MekanismHeated.MODID, "heat_smelter"));

    @Override
    public abstract boolean test(ItemStack itemStack);

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        //Don't match incomplete recipes or ones that don't match
        return !isIncomplete() && test(input.item());
    }

    /**
     * Gets the input ingredient.
     */
    public abstract ItemStackIngredient getInput();

    /**
     * Gets the output based on the given input.
     *
     * @param input Specific input.
     *
     * @return Output as a constant.
     *
     * @apiNote While Mekanism does not currently make use of the input, it is important to support it and pass the proper value in case any addons define input based
     * outputs where things like NBT may be different.
     * @implNote The passed in input should <strong>NOT</strong> be modified.
     */
    @Range(from = 1, to = Long.MAX_VALUE)
    public abstract long getOutput(ItemStack input);

    /**
     * For JEI, gets the output representations to display.
     *
     * @return Representation of the output, <strong>MUST NOT</strong> be modified.
     */
    public abstract long[] getOutputDefinition();

    @Override
    public boolean isIncomplete() {
        return getInput().hasNoMatchingInstances();
    }

    @Override
    public void logMissingTags() {
        getInput().logMissingTags();
    }

    @Override
    public final RecipeType<ItemStackToHeatRecipe> getType() {
        return MekHeatedRecipeTypes.TYPE_FUEL_CONVERSION.value();
    }

    @Override
    public String getGroup() {
        return "fuel_conversion";
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(HEAT_SMELTER);
    }
}