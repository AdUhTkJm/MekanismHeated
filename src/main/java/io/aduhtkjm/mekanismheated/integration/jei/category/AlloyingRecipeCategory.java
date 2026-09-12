package io.aduhtkjm.mekanismheated.integration.jei.category;

import io.aduhtkjm.mekanismheated.recipe.AlloyRecipe;
import mekanism.client.gui.element.GuiUpArrow;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.recipe_viewer.jei.HolderRecipeCategory;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.tile.component.config.DataType;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.lwjgl.system.NonnullDefault;

/**
 * Alloying recipes for the Heat Smelter: two molten metals combine into one alloy. Unlike the smelting and melting
 * categories there are no item slots, since both inputs and the output live in the machine's fluid tank; the two inputs
 * are shown as separate gauges on the left and the alloy as the output gauge on the right.
 */
@NonnullDefault
public class AlloyingRecipeCategory extends HolderRecipeCategory<AlloyRecipe> {

    private final GuiGauge<?> input1;
    private final GuiGauge<?> input2;
    private final GuiGauge<?> output;

    public AlloyingRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<AlloyRecipe> recipeType) {
        super(helper, recipeType);
        //Two small gauges rather than full height ones so the pair fits the category's height
        input1 = addElement(GuiFluidGauge.getDummy(GaugeType.SMALL_MED.with(DataType.INPUT_1), this, 30, 17));
        input2 = addElement(GuiFluidGauge.getDummy(GaugeType.SMALL_MED.with(DataType.INPUT_2), this, 50, 17));
        //Static arrow: alloying has no progress bar of its own, it consumes matching fluids as soon as they are present
        addElement(new GuiUpArrow(this, 68, 38));
        output = addElement(GuiFluidGauge.getDummy(GaugeType.STANDARD.with(DataType.OUTPUT), this, 139, 13));
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<AlloyRecipe> recipeHolder, IFocusGroup focusGroup) {
        AlloyRecipe recipe = recipeHolder.value();
        initFluid(builder, RecipeIngredientRole.INPUT, input1, recipe.getInput1().getRepresentations());
        initFluid(builder, RecipeIngredientRole.INPUT, input2, recipe.getInput2().getRepresentations());
        initFluid(builder, RecipeIngredientRole.OUTPUT, output, recipe.getOutput().getRepresentations());
    }
}
