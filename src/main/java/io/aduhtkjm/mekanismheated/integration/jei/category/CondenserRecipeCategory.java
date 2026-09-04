package io.aduhtkjm.mekanismheated.integration.jei.category;

import io.aduhtkjm.mekanismheated.recipe.CondenserRecipe;
import java.util.Optional;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import mekanism.client.gui.element.GuiUpArrow;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.recipe_viewer.jei.HolderRecipeCategory;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.tile.component.config.DataType;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.lwjgl.system.NonnullDefault;

/** Condensing recipes for the Condenser: fluid plus optional item to a single item. Layout mirrors the machine's GUI. */
@NonnullDefault
public class CondenserRecipeCategory extends HolderRecipeCategory<CondenserRecipe> {
    private final GuiGauge<?> inputTank;
    private final GuiSlot input;
    private final GuiSlot output;

    public CondenserRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<CondenserRecipe> recipeType) {
        super(helper, recipeType);
        inputTank = addElement(GuiFluidGauge.getDummy(GaugeType.STANDARD.with(DataType.INPUT), this, 36, 10));
        input = addSlot(SlotType.INPUT, 65, 35);
        output = addSlot(SlotType.OUTPUT, 116, 35);
        addSimpleProgress(ProgressType.BAR, 86, 38);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<CondenserRecipe> recipeHolder, IFocusGroup focusGroup) {
        CondenserRecipe recipe = recipeHolder.value();
        FluidStackIngredient fluidInput = recipe.getFluidInput();
        initFluid(builder, RecipeIngredientRole.INPUT, inputTank, fluidInput.getRepresentations());
        Optional<ItemStackIngredient> itemInput = recipe.getItemInput();
        if (itemInput.isPresent()) {
            initItem(builder, RecipeIngredientRole.INPUT, input, itemInput.orElseThrow().getRepresentations());
        }
        initItem(builder, RecipeIngredientRole.OUTPUT, output, recipe.getOutputDefinition());
    }
}
