package io.aduhtkjm.mekanismheated.integration.jei.category;

import io.aduhtkjm.mekanismheated.ModLang;
import io.aduhtkjm.mekanismheated.recipe.BasicFractionationRecipe;
import io.aduhtkjm.mekanismheated.recipe.FractionationRecipe;
import io.aduhtkjm.mekanismheated.recipe.FractionationRecipe.BankOutput;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.recipe_viewer.jei.HolderRecipeCategory;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.lwjgl.system.NonnullDefault;

/**
 * Fractionation recipes for the Thermal Fractionation Tower, covering both forms in a single category: input-based
 * recipes show the feed sump on the left, while passive recipes (no input) leave it empty. The banked outputs are shown
 * stacked in a column on the right, mirroring the tower's vertical layout with the lowest bank at the bottom.
 *
 * <p>Only the first {@value #OUTPUT_SLOTS} outputs (by bank, bottom to top) are shown, so a recipe targeting more banks
 * than this only displays its lowest few.</p>
 */
@NonnullDefault
public class FractionationRecipeCategory extends HolderRecipeCategory<FractionationRecipe> {

    /** Number of output-bank gauges shown, bottom to top. */
    private static final int OUTPUT_SLOTS = 3;

    private final GuiGauge<?> inputGauge;
    private final List<GuiGauge<?>> outputPool = new ArrayList<>(OUTPUT_SLOTS);

    public FractionationRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<FractionationRecipe> recipeType) {
        super(helper, recipeType);
        inputGauge = addElement(GuiFluidGauge.getDummy(GaugeType.STANDARD.with(DataType.INPUT), this, 8, 30));
        addSimpleProgress(ProgressType.RIGHT, 84, 36);
        GaugeType outputType = GaugeType.SMALL.with(DataType.OUTPUT);
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            // i = 0 is the lowest bank, drawn at the bottom of the column
            outputPool.add(addElement(GuiFluidGauge.getDummy(outputType, this, 140, 78 - i * 32)));
        }
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<FractionationRecipe> recipeHolder,
                          IFocusGroup focusGroup) {
        FractionationRecipe recipe = recipeHolder.value();
        IRecipeSlotBuilder temperatureSlot = null;
        // Input-based recipes feed the sump; passive recipes have no input
        if (recipe instanceof BasicFractionationRecipe basic) {
            temperatureSlot = initFluid(builder, RecipeIngredientRole.INPUT, inputGauge, basic.getInput().getRepresentations());
        }
        // Outputs, ordered by bank index so the lowest bank fills the bottom gauge
        List<BankOutput> outputs = new ArrayList<>(recipe.getOutputs());
        outputs.sort(Comparator.comparingInt(BankOutput::bank));
        for (int i = 0; i < outputs.size() && i < OUTPUT_SLOTS; i++) {
            BankOutput output = outputs.get(i);
            IRecipeSlotBuilder outputSlot = initFluid(builder, RecipeIngredientRole.OUTPUT, outputPool.get(i), List.of(output.stack()));
            // Passive recipes have no input slot to carry the temperature window, so attach it to the first output instead
            if (temperatureSlot == null) {
                temperatureSlot = outputSlot;
            }
        }
        if (temperatureSlot != null) {
            addTemperatureTooltip(temperatureSlot, recipe);
        }
    }

    /**
     * Adds a note that the recipe only runs while the tower is within the recipe's temperature window, in Kelvin.
     */
    private static void addTemperatureTooltip(IRecipeSlotBuilder slotBuilder, FractionationRecipe recipe) {
        slotBuilder.addRichTooltipCallback((slotView, tooltip) -> tooltip.add(ModLang.TEMPERATURE_RANGE.translate(
              MekanismUtils.getTemperatureDisplay(recipe.getMinTemperature(), TemperatureUnit.KELVIN, true),
              MekanismUtils.getTemperatureDisplay(recipe.getMaxTemperature(), TemperatureUnit.KELVIN, true))));
    }
}
