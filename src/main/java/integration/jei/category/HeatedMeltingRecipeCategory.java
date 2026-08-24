package integration.jei.category;

import io.aduhtkjm.mekanismheated.recipe.HeatedItemStackToFluidRecipe;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.tile.component.config.DataType;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Heated melting recipes for the Heat Smelter: item to fluid above the melting threshold. */
public class HeatedMeltingRecipeCategory extends AbstractHeatSmelterRecipeCategory<HeatedItemStackToFluidRecipe> {

    private final GuiGauge<?> outputTank;

    public HeatedMeltingRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<HeatedItemStackToFluidRecipe> recipeType) {
        super(helper, recipeType);
        outputTank = addElement(GuiFluidGauge.getDummy(GaugeType.STANDARD.with(DataType.OUTPUT), this, 139, 13));
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<HeatedItemStackToFluidRecipe> recipeHolder, IFocusGroup focusGroup) {
        HeatedItemStackToFluidRecipe recipe = recipeHolder.value();
        addTemperatureTooltip(initItem(builder, RecipeIngredientRole.INPUT, input, recipe.getInput().getRepresentations()),
              recipe.getTemperatureThreshold());
        initFluid(builder, RecipeIngredientRole.OUTPUT, outputTank, recipe.getOutputDefinition());
    }
}
