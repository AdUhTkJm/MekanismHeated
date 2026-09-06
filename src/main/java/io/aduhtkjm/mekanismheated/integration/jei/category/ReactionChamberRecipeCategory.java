package io.aduhtkjm.mekanismheated.integration.jei.category;

import io.aduhtkjm.mekanismheated.ModLang;
import io.aduhtkjm.mekanismheated.recipe.ReactionChamberRecipe;
import java.util.ArrayList;
import java.util.List;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
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
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.lwjgl.system.NonnullDefault;

/**
 * Reaction recipes for the Reaction Chamber: an item plus a list of fluid/chemical inputs reacts into an item plus a list
 * of fluid/chemical outputs, within a temperature window. The layout mirrors the machine's shared pool: item slots flank
 * gauges standing in for the (up to {@value #POOL_SLOTS_PER_SIDE}) fluid and chemical slots shown per side.
 *
 * <p>The machine's GUI only exposes a single wide gauge for its mixed fluid/chemical tank; because a recipe can consume and
 * produce several substances at once, the JEI category instead gives each required ingredient its own gauge. Recipes with
 * more than {@value #POOL_SLOTS_PER_SIDE} distinct fluid/chemical inputs or outputs only show the first few.
 */
@NonnullDefault
public class ReactionChamberRecipeCategory extends HolderRecipeCategory<ReactionChamberRecipe> {

    /** Number of pooled ingredient gauges shown on the input side and on the output side. */
    private static final int POOL_SLOTS_PER_SIDE = 2;

    private final GuiSlot inputItem;
    private final GuiSlot outputItem;
    private final List<GuiGauge<?>> inputPool = new ArrayList<>(POOL_SLOTS_PER_SIDE);
    private final List<GuiGauge<?>> outputPool = new ArrayList<>(POOL_SLOTS_PER_SIDE);

    public ReactionChamberRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<ReactionChamberRecipe> recipeType) {
        super(helper, recipeType);
        GaugeType inputType = GaugeType.STANDARD.with(DataType.INPUT);
        for (int i = 0; i < POOL_SLOTS_PER_SIDE; i++) {
            inputPool.add(addElement(GuiFluidGauge.getDummy(inputType, this, 6 + i * 22, 14)));
        }
        inputItem = addSlot(SlotType.INPUT, 56, 40);
        addSimpleProgress(ProgressType.RIGHT, 86, 44);
        outputItem = addSlot(SlotType.OUTPUT, 122, 40);
        GaugeType outputType = GaugeType.SMALL.with(DataType.OUTPUT);
        for (int i = 0; i < POOL_SLOTS_PER_SIDE; i++) {
            outputPool.add(addElement(GuiFluidGauge.getDummy(outputType, this, 140, 14 + i * 32)));
        }
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<ReactionChamberRecipe> recipeHolder,
                          IFocusGroup focusGroup) {
        ReactionChamberRecipe recipe = recipeHolder.value();
        if (recipe.hasItemInput()) {
            IRecipeSlotBuilder itemInput = initItem(builder, RecipeIngredientRole.INPUT, inputItem,
                  recipe.getItemInput().orElseThrow().getRepresentations());
            addTemperatureTooltip(itemInput, recipe);
        }
        //Fill the input pool gauges with the recipe's fluid then chemical inputs, in order
        int inputIndex = 0;
        for (FluidStackIngredient fluid : recipe.getFluidInputs()) {
            if (inputIndex >= POOL_SLOTS_PER_SIDE) {
                break;
            }
            initFluid(builder, RecipeIngredientRole.INPUT, inputPool.get(inputIndex++), fluid.getRepresentations());
        }
        for (ChemicalStackIngredient chemical : recipe.getChemicalInputs()) {
            if (inputIndex >= POOL_SLOTS_PER_SIDE) {
                break;
            }
            initChemical(builder, RecipeIngredientRole.INPUT, inputPool.get(inputIndex++), chemical.getRepresentations());
        }

        if (!recipe.getItemOutputDefinition().isEmpty()) {
            initItem(builder, RecipeIngredientRole.OUTPUT, outputItem, recipe.getItemOutputDefinition());
        }
        //Fill the output pool gauges with the recipe's fluid then chemical outputs, in order
        int outputIndex = 0;
        for (FluidStackIngredient fluid : recipe.getFluidOutputIngredients()) {
            if (outputIndex >= POOL_SLOTS_PER_SIDE) {
                break;
            }
            initFluid(builder, RecipeIngredientRole.OUTPUT, outputPool.get(outputIndex++), fluid.getRepresentations());
        }
        for (ChemicalStackIngredient chemical : recipe.getChemicalOutputIngredients()) {
            if (outputIndex >= POOL_SLOTS_PER_SIDE) {
                break;
            }
            initChemical(builder, RecipeIngredientRole.OUTPUT, outputPool.get(outputIndex++), chemical.getRepresentations());
        }
    }

    /**
     * Adds a note on the item input that the reaction only runs while the chamber is within the recipe's temperature
     * window, in Kelvin.
     */
    private static void addTemperatureTooltip(IRecipeSlotBuilder slotBuilder, ReactionChamberRecipe recipe) {
        slotBuilder.addRichTooltipCallback((slotView, tooltip) -> tooltip.add(ModLang.TEMPERATURE_RANGE.translate(
              MekanismUtils.getTemperatureDisplay(recipe.getMinTemperature(), TemperatureUnit.KELVIN, true),
              MekanismUtils.getTemperatureDisplay(recipe.getMaxTemperature(), TemperatureUnit.KELVIN, true))));
    }
}
