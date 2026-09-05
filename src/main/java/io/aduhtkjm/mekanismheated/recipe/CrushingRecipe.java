package io.aduhtkjm.mekanismheated.recipe;

import io.aduhtkjm.mekanismheated.Mod;
import java.util.List;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Input: ItemStack
 * <br>
 * Output: ItemStack
 * <br>
 * Condition: the item must be hit by a falling block that is the recipe's catalyst (e.g. an anvil).
 *
 * <p>Crushing recipes are not processed by a machine; they are applied directly to dropped items when a matching block
 * lands on them (see {@code io.aduhtkjm.mekanismheated.recipe.impl.CrushingHandler}).</p>
 */
@NothingNullByDefault
public abstract class CrushingRecipe extends MekanismRecipe<CrushingRecipeInput> {

    private static final Holder<Item> HEAT_SMELTER = DeferredHolder.create(Registries.ITEM,
          ResourceLocation.fromNamespaceAndPath(Mod.MODID, "heat_smelter"));

    /**
     * Gets the item ingredient that may be crushed.
     */
    public abstract ItemStackIngredient getInput();

    /**
     * Gets the block that, when it lands on the input item, triggers this recipe.
     */
    public abstract Block getCatalyst();

    /**
     * Gets a new output based on the given input item.
     *
     * @param input Specific item input.
     *
     * @return New output.
     *
     * @implNote The passed in input should <strong>NOT</strong> be modified.
     */
    public abstract ItemStack getOutput(ItemStack input);

    /**
     * For recipe viewers, gets the output representation to display.
     *
     * @return Representation of the output, <strong>MUST NOT</strong> be modified.
     */
    public abstract List<ItemStack> getOutputDefinition();

    @Override
    public boolean matches(CrushingRecipeInput input, Level level) {
        //Don't match incomplete recipes or ones that don't match
        return !isIncomplete() && getCatalyst() == input.catalyst() && getInput().test(input.item());
    }

    @Override
    public boolean isIncomplete() {
        return getInput().hasNoMatchingInstances();
    }

    @Override
    public void logMissingTags() {
        getInput().logMissingTags();
    }

    @Override
    public String getGroup() {
        return "crushing";
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(HEAT_SMELTER);
    }
}
