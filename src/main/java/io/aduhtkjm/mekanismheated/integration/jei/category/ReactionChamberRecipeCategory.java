package io.aduhtkjm.mekanismheated.integration.jei.category;

import io.aduhtkjm.mekanismheated.ModLang;
import io.aduhtkjm.mekanismheated.recipe.ReactionChamberRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToLongFunction;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.client.gui.element.gauge.GaugeOverlay;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.recipe_viewer.jei.ChemicalStackRenderer;
import mekanism.client.recipe_viewer.jei.HolderRecipeCategory;
import mekanism.client.recipe_viewer.jei.MekanismJEI;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.lwjgl.system.NonnullDefault;

/**
 * Reaction recipes for the Reaction Chamber: an item plus a list of fluid/chemical inputs reacts into an item plus a list
 * of fluid/chemical outputs, within a temperature window.
 *
 * <p>A recipe may use any number of fluids and chemicals, so the layout mirrors the machine's own stacked pool gauge
 * rather than giving each ingredient a gauge of its own: the item input and a single pool gauge sit on the left, the
 * progress arrow in the middle, and the output pool gauge and the item output on the right. Each pool gauge is split into
 * one band per pooled ingredient - fluids filling it from the bottom up and chemicals from the top down, as
 * {@code GuiStackedFluidChemicalGauge} does - which keeps the recipe readable however many ingredients it has.</p>
 */
@NonnullDefault
public class ReactionChamberRecipeCategory extends HolderRecipeCategory<ReactionChamberRecipe> {

    /** Smallest height a pooled ingredient's band is squeezed to, so that even a tiny amount stays visible and hoverable. */
    private static final int MIN_BAND_HEIGHT = 6;

    private final IGuiHelper guiHelper;
    private final GuiSlot inputItem;
    private final GuiGauge<?> inputPool;
    private final GuiGauge<?> outputPool;
    private final GuiSlot outputItem;

    public ReactionChamberRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<ReactionChamberRecipe> recipeType) {
        super(helper, recipeType);
        guiHelper = helper;
        inputItem = addSlot(SlotType.INPUT, 8, 40);
        inputPool = addElement(GuiFluidGauge.getDummy(GaugeType.MEDIUM.with(DataType.INPUT), this, 30, 18));
        addSimpleProgress(ProgressType.RIGHT, 68, 44);
        outputPool = addElement(GuiFluidGauge.getDummy(GaugeType.MEDIUM.with(DataType.OUTPUT), this, 104, 18));
        outputItem = addSlot(SlotType.OUTPUT, 142, 40);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<ReactionChamberRecipe> recipeHolder,
                          IFocusGroup focusGroup) {
        ReactionChamberRecipe recipe = recipeHolder.value();
        // The temperature window and the duration are noted on the first input slot, whichever kind of input comes first.
        IRecipeSlotBuilder infoSlot = null;
        if (recipe.hasItemInput()) {
            infoSlot = initItem(builder, RecipeIngredientRole.INPUT, inputItem,
                  recipe.getItemInput().orElseThrow().getRepresentations());
        }

        List<FluidStackIngredient> fluidInputs = recipe.getFluidInputs();
        List<ChemicalStackIngredient> chemicalInputs = recipe.getChemicalInputs();
        List<Band> inputBands = resolveBands(inputPool, fluidInputs.size(), chemicalInputs.size(),
              pooledWeights(fluidInputs, chemicalInputs));
        int bandIndex = 0;
        for (FluidStackIngredient fluid : fluidInputs) {
            IRecipeSlotBuilder slot = initFluidBand(builder, RecipeIngredientRole.INPUT, inputBands.get(bandIndex++),
                  fluid.getRepresentations());
            if (infoSlot == null) {
                infoSlot = slot;
            }
        }
        for (ChemicalStackIngredient chemical : chemicalInputs) {
            IRecipeSlotBuilder slot = initChemicalBand(builder, RecipeIngredientRole.INPUT, inputBands.get(bandIndex++),
                  chemical.getRepresentations());
            if (infoSlot == null) {
                infoSlot = slot;
            }
        }
        if (infoSlot != null) {
            addRecipeTooltip(infoSlot, recipe);
        }

        if (!recipe.getItemOutputDefinition().isEmpty()) {
            initItem(builder, RecipeIngredientRole.OUTPUT, outputItem, recipe.getItemOutputDefinition());
        }
        List<FluidStackIngredient> fluidOutputs = recipe.getFluidOutputIngredients();
        List<ChemicalStackIngredient> chemicalOutputs = recipe.getChemicalOutputIngredients();
        List<Band> outputBands = resolveBands(outputPool, fluidOutputs.size(), chemicalOutputs.size(),
              pooledWeights(fluidOutputs, chemicalOutputs));
        bandIndex = 0;
        for (FluidStackIngredient fluid : fluidOutputs) {
            initFluidBand(builder, RecipeIngredientRole.OUTPUT, outputBands.get(bandIndex++), fluid.getRepresentations());
        }
        for (ChemicalStackIngredient chemical : chemicalOutputs) {
            initChemicalBand(builder, RecipeIngredientRole.OUTPUT, outputBands.get(bandIndex++), chemical.getRepresentations());
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<ReactionChamberRecipe> recipeHolder,
                                   IFocusGroup focuses) {
        super.createRecipeExtras(builder, recipeHolder, focuses);
        // A pool is made of one JEI slot per ingredient, and an overlay attached to a slot is drawn at that slot's origin,
        // so putting the gauge's tick marks on a band would repeat the whole gauge texture once per band. Recipe extras
        // are drawn on top of every slot instead, which is where the ticks belong to be readable over the bands, exactly
        // as the machine's stacked gauge draws them over its contents.
        addBarOverlay(builder, inputPool);
        addBarOverlay(builder, outputPool);
    }

    /**
     * Adds a note on the recipe's first input that the reaction only runs while the chamber is within the recipe's temperature
     * window (in Kelvin), and how many ticks the chamber waits between two of its operations.
     */
    private static void addRecipeTooltip(IRecipeSlotBuilder slotBuilder, ReactionChamberRecipe recipe) {
        slotBuilder.addRichTooltipCallback((slotView, tooltip) -> {
            tooltip.add(ModLang.TEMPERATURE_RANGE.translate(
                  MekanismUtils.getTemperatureDisplay(recipe.getMinTemperature(), TemperatureUnit.KELVIN, true),
                  MekanismUtils.getTemperatureDisplay(recipe.getMaxTemperature(), TemperatureUnit.KELVIN, true)));
            tooltip.add(ModLang.REACTION_DURATION.translate(recipe.getDuration()));
        });
    }

    /**
     * Splits a pool gauge's inner window into one band per pooled ingredient. Fluids take bands from the bottom of the
     * window upward and chemicals take bands from the top downward, both in recipe order, so the two groups meet in the
     * middle the way they do in the machine's own stacked pool gauge. A band is as tall as its share of the pool's total
     * amount, except that every band keeps at least {@value #MIN_BAND_HEIGHT} pixels so that small amounts stay visible.
     *
     * @param fluidCount    The number of fluid ingredients, which are the first entries of {@code weights}.
     * @param chemicalCount The number of chemical ingredients, which are the remaining entries of {@code weights}.
     * @param weights       How much each ingredient contributes to the pool, in pool order (fluids first).
     *
     * @return One band per ingredient, in the same order as {@code weights}.
     */
    private static List<Band> resolveBands(GuiGauge<?> pool, int fluidCount, int chemicalCount, double[] weights) {
        int count = fluidCount + chemicalCount;
        if (count == 0) {
            return List.of();
        }
        int innerX = pool.getX() + 1;
        int innerY = pool.getY() + 1;
        int innerWidth = pool.getWidth() - 2;
        int innerHeight = pool.getHeight() - 2;
        // Each band gets a floor height so that it stays readable, and the remaining room is shared out by amount. A recipe
        // with more ingredients than the gauge has pixels cannot give every band a pixel, so the floor bottoms out at one.
        int floor = innerHeight > count ? Math.min(MIN_BAND_HEIGHT, innerHeight / count) : 1;
        int flexible = Math.max(0, innerHeight - floor * count);
        double total = 0;
        for (double weight : weights) {
            total += weight;
        }
        int[] heights = new int[count];
        double[] remainders = new double[count];
        int used = 0;
        for (int i = 0; i < count; i++) {
            double exact = total > 0 ? flexible * weights[i] / total : (double) flexible / count;
            int whole = (int) exact;
            heights[i] = floor + whole;
            remainders[i] = exact - whole;
            used += heights[i];
        }
        // Rounding always loses less than one pixel per band, so hand the leftover pixels to the bands that were cut the most.
        for (int left = innerHeight - used; left > 0; left--) {
            int largest = 0;
            for (int i = 1; i < count; i++) {
                if (remainders[i] > remainders[largest]) {
                    largest = i;
                }
            }
            heights[largest]++;
            remainders[largest] = -1;
        }
        List<Band> bands = new ArrayList<>(count);
        // The first fluid is the lowest band, and the first chemical the highest.
        int fluidTop = innerY + innerHeight;
        for (int i = 0; i < fluidCount; i++) {
            fluidTop -= heights[i];
            bands.add(new Band(innerX, fluidTop, innerWidth, heights[i]));
        }
        int chemicalBottom = innerY;
        for (int i = fluidCount; i < count; i++) {
            bands.add(new Band(innerX, chemicalBottom, innerWidth, heights[i]));
            chemicalBottom += heights[i];
        }
        return bands;
    }

    /** How much each of a pool's ingredients contributes to it, in pool order: fluids first, then chemicals. */
    private static double[] pooledWeights(List<FluidStackIngredient> fluidInputs, List<ChemicalStackIngredient> chemicalInputs) {
        double[] weights = new double[fluidInputs.size() + chemicalInputs.size()];
        int index = 0;
        for (FluidStackIngredient fluid : fluidInputs) {
            weights[index++] = representativeAmount(fluid.getRepresentations(), FluidStack::getAmount);
        }
        for (ChemicalStackIngredient chemical : chemicalInputs) {
            weights[index++] = representativeAmount(chemical.getRepresentations(), ChemicalStack::getAmount);
        }
        return weights;
    }

    /**
     * The amount an ingredient is weighed by: the largest of its representations, which is also the one that fills a band
     * completely when it is the representation on display, or one bucket for an ingredient that carries no amount at all.
     */
    private static <STACK> long representativeAmount(List<STACK> representations, ToLongFunction<STACK> amount) {
        long largest = representations.stream().mapToLong(amount).max().orElse(0);
        return largest > 0 ? largest : FluidType.BUCKET_VOLUME;
    }

    /**
     * Adds a JEI slot covering one band of a pool gauge. The slot deliberately carries no gauge overlay of its own: the
     * pool is framed and ticked once for the whole gauge (by the gauge element and the recipe extras) so that its bands
     * read as one stacked tank instead of as a separate gauge each.
     */
    private static IRecipeSlotBuilder initFluidBand(IRecipeLayoutBuilder builder, RecipeIngredientRole role, Band band,
          List<FluidStack> stacks) {
        //A capacity of zero or less makes the renderer throw, so an amount-less ingredient falls back to a bucket
        long capacity = representativeAmount(stacks, FluidStack::getAmount);
        return builder.addSlot(role, band.x(), band.y())
              .addIngredients(NeoForgeTypes.FLUID_STACK, stacks)
              .setFluidRenderer(capacity, false, band.width(), band.height());
    }

    /** Adds a JEI slot covering one band of a pool gauge; see {@link #initFluidBand} for why it carries no overlay. */
    private static IRecipeSlotBuilder initChemicalBand(IRecipeLayoutBuilder builder, RecipeIngredientRole role, Band band,
          List<ChemicalStack> stacks) {
        long capacity = representativeAmount(stacks, ChemicalStack::getAmount);
        return builder.addSlot(role, band.x(), band.y())
              .addIngredients(MekanismJEI.TYPE_CHEMICAL, stacks)
              .setCustomRenderer(MekanismJEI.TYPE_CHEMICAL, new ChemicalStackRenderer(capacity, band.width(), band.height()));
    }

    /** Draws a pool gauge's tick marks over the bands it frames, where the gauge element itself cannot draw them. */
    private void addBarOverlay(IRecipeExtrasBuilder builder, GuiGauge<?> pool) {
        GaugeOverlay overlay = pool.getGaugeOverlay();
        IDrawable ticks = guiHelper.drawableBuilder(overlay.getBarOverlay(), 0, 0, overlay.getWidth(), overlay.getHeight())
              .setTextureSize(overlay.getWidth(), overlay.getHeight())
              .build();
        builder.addDrawable(ticks, pool.getX() + 1, pool.getY() + 1);
    }

    /** The rectangle one pooled ingredient occupies inside a pool gauge. */
    private record Band(int x, int y, int width, int height) {
    }
}
