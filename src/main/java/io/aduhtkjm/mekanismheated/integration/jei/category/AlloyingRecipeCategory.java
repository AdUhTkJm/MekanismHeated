package io.aduhtkjm.mekanismheated.integration.jei.category;

import io.aduhtkjm.mekanismheated.recipe.AlloyRecipe;
import java.util.ArrayList;
import java.util.List;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.recipe_viewer.jei.HolderRecipeCategory;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.tile.component.config.DataType;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.lwjgl.system.NonnullDefault;

/**
 * Alloying recipes for the Heat Smelter: two or three molten fluids combine into one alloy. Unlike the smelting and
 * melting categories there are no item slots, since both inputs and the output live in the machine's fluid tank; the
 * inputs are shown as separate gauges on the left and the alloy as the output gauge on the right.
 *
 * <p>The inputs are an unordered group, so they are shown as separate slots (shapeless) rather than as an ordered row.
 * A recipe that declares fewer than {@link AlloyRecipe#MAX_INPUTS} inputs leaves the last gauges empty, so the row is
 * drawn per recipe and only the gauges the recipe actually uses are shown.</p>
 */
@NonnullDefault
public class AlloyingRecipeCategory extends HolderRecipeCategory<AlloyRecipe> {

    /** Left edge and vertical position of the first input gauge; the rest follow it in a row. */
    private static final int INPUT_X = 30;
    private static final int INPUT_Y = 17;
    private static final int INPUT_SPACING = 20;

    /** The color each input gauge uses, in the order the inputs are declared. */
    private static final DataType[] INPUT_DATA_TYPES = {DataType.INPUT_1, DataType.INPUT_2, DataType.EXTRA};

    private final List<GuiGauge<?>> inputGauges = new ArrayList<>(AlloyRecipe.MAX_INPUTS);
    private final GuiGauge<?> output;

    public AlloyingRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<AlloyRecipe> recipeType) {
        super(helper, recipeType);
        //Small gauges rather than full height ones so the row fits the category's height
        for (int i = 0; i < AlloyRecipe.MAX_INPUTS; i++) {
            inputGauges.add(addElement(GuiFluidGauge.getDummy(GaugeType.SMALL_MED.with(INPUT_DATA_TYPES[i]), this,
                  INPUT_X + i * INPUT_SPACING, INPUT_Y)));
        }
        //Right of the whole input row, so it stays clear of a recipe that uses all of the gauges
        addSimpleProgress(ProgressType.BAR, INPUT_X + AlloyRecipe.MAX_INPUTS * INPUT_SPACING + 2, 38);
        output = addElement(GuiFluidGauge.getDummy(GaugeType.STANDARD.with(DataType.OUTPUT), this, 139, 13));
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<AlloyRecipe> recipeHolder, IFocusGroup focusGroup) {
        AlloyRecipe recipe = recipeHolder.value();
        //The inputs are matched as an unordered group, so let JEI show them without an order
        builder.setShapeless();
        List<FluidStackIngredient> inputs = recipe.getInputs();
        for (int i = 0; i < inputs.size() && i < inputGauges.size(); i++) {
            initFluid(builder, RecipeIngredientRole.INPUT, inputGauges.get(i), inputs.get(i).getRepresentations());
        }
        initFluid(builder, RecipeIngredientRole.OUTPUT, output, recipe.getOutput().getRepresentations());
    }
}
