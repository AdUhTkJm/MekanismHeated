package integration.jei.category;

import io.aduhtkjm.mekanismheated.ModLang;
import mekanism.client.gui.element.GuiUpArrow;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.recipe_viewer.RecipeViewerUtils;
import mekanism.client.recipe_viewer.jei.HolderRecipeCategory;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Shared layout for the heat smelter's recipe viewer categories; mirrors the machine's GUI: input slot at top left,
 * fuel/power slot beneath it, and the power bar on the right.
 */
abstract class AbstractHeatSmelterRecipeCategory<RECIPE extends Recipe<?>> extends HolderRecipeCategory<RECIPE> {

    protected final GuiSlot input;

    protected AbstractHeatSmelterRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<RECIPE> recipeType) {
        super(helper, recipeType);
        input = addSlot(SlotType.INPUT, 64, 17);
        addElement(new GuiUpArrow(this, 68, 38));
        addSimpleProgress(ProgressType.BAR, 86, 38);
        addElement(new GuiVerticalPowerBar(this, RecipeViewerUtils.FULL_BAR, 164, 16));
        //Visual stand-in for the fuel slot
        addSlot(SlotType.POWER, 64, 53).with(SlotOverlay.POWER);
    }

    /**
     * Notes on the input that the smelter only processes it once it is at least the given temperature, in Kelvin.
     */
    protected void addTemperatureTooltip(IRecipeSlotBuilder slotBuilder, double temperatureThreshold) {
        slotBuilder.addRichTooltipCallback((slotView, tooltip) -> tooltip.add(ModLang.MIN_TEMPERATURE.translate(
              MekanismUtils.getTemperatureDisplay(temperatureThreshold, TemperatureUnit.KELVIN, true))));
    }
}
