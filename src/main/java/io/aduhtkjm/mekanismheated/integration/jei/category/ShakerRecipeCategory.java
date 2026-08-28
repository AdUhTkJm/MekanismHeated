package io.aduhtkjm.mekanismheated.integration.jei.category;

import io.aduhtkjm.mekanismheated.recipe.ShakerRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.client.gui.element.GuiUpArrow;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.recipe_viewer.RecipeViewerUtils;
import mekanism.client.recipe_viewer.jei.HolderRecipeCategory;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.tile.component.config.DataType;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Shaking recipes for the Shaker: item plus optional fluid to 1-3 items. Layout mirrors the machine's GUI. */
public class ShakerRecipeCategory extends HolderRecipeCategory<ShakerRecipe> {

    private static final int OUTPUT_SLOTS = 3;

    private final GuiGauge<?> inputTank;
    private final GuiSlot input;
    private final List<GuiSlot> outputs = new ArrayList<>(OUTPUT_SLOTS);

    public ShakerRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<ShakerRecipe> recipeType) {
        super(helper, recipeType);
        inputTank = addElement(GuiFluidGauge.getDummy(GaugeType.STANDARD.with(DataType.INPUT), this, 6, 10));
        input = addSlot(SlotType.INPUT, 64, 17);
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            outputs.add(addSlot(SlotType.OUTPUT, 116, 17 + 18 * i));
        }
        addElement(new GuiUpArrow(this, 68, 38));
        addSimpleProgress(ProgressType.BAR, 86, 38);
        addElement(new GuiVerticalPowerBar(this, RecipeViewerUtils.FULL_BAR, 164, 16));
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<ShakerRecipe> recipeHolder, IFocusGroup focusGroup) {
        ShakerRecipe recipe = recipeHolder.value();
        initItem(builder, RecipeIngredientRole.INPUT, input, recipe.getInput().getRepresentations());
        Optional<FluidStackIngredient> fluidInput = recipe.getFluidInput();
        if (fluidInput.isPresent()) {
            initFluid(builder, RecipeIngredientRole.INPUT, inputTank, fluidInput.orElseThrow().getRepresentations());
        }
        List<ItemStack> outputDefinition = recipe.getOutputDefinition();
        for (int i = 0; i < Math.min(outputs.size(), outputDefinition.size()); i++) {
            ItemStack output = outputDefinition.get(i);
            if (!output.isEmpty()) {
                initItem(builder, RecipeIngredientRole.OUTPUT, outputs.get(i), List.of(output));
            }
        }
    }
}
