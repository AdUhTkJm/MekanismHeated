package io.aduhtkjm.mekanismheated.integration.jei.category;

import io.aduhtkjm.mekanismheated.ModLang;
import io.aduhtkjm.mekanismheated.recipe.CondenserRecipe;
import io.aduhtkjm.mekanismheated.recipe.RetroentropicArrayRecipe;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.recipe_viewer.jei.HolderRecipeCategory;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.lwjgl.system.NonnullDefault;

import java.util.Optional;

/** Condensing recipes for the Condenser: fluid plus optional item to a single item. Layout mirrors the machine's GUI. */
@NonnullDefault
public class RetroentropicArrayRecipeCategory extends HolderRecipeCategory<RetroentropicArrayRecipe> {
    private final GuiSlot input;
    private final GuiSlot output;

    public RetroentropicArrayRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<RetroentropicArrayRecipe> recipeType) {
        super(helper, recipeType);
        input = addSlot(SlotType.INPUT, 65, 35);
        output = addSlot(SlotType.OUTPUT, 116, 35);
        addSimpleProgress(ProgressType.BAR, 86, 38);
    }

    private void addTooltip(IRecipeSlotBuilder slotBuilder, int duration) {
        slotBuilder.addRichTooltipCallback((slotView, tooltip) -> tooltip.add(ModLang.REACTION_DURATION.translate(duration)));
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<RetroentropicArrayRecipe> holder, IFocusGroup focusGroup) {
        var recipe = holder.value();
        var itemInput = recipe.getInput();
        var inputBuilder = initItem(builder, RecipeIngredientRole.INPUT, input, itemInput.getRepresentations());
        initItem(builder, RecipeIngredientRole.OUTPUT, output, recipe.getOutputDefinition());
        addTooltip(inputBuilder, recipe.getDuration());
    }
}
