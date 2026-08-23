package io.aduhtkjm.mekanismheated.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.aduhtkjm.mekanismheated.Mod;
import java.util.List;
import java.util.function.Predicate;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.vanilla_input.SingleFluidRecipeInput;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Input: FluidStack (fed into the tower's feed sump)
 * <br>
 * Output: multiple fluid stacks, each targeting a specific output bank of the tower.
 * <br>
 * Condition: the tower must be at least {@link #getMinTemperature()} Kelvin to process; processing speed scales linearly,
 * reaching nominal speed at {@link #getBaseTemperature()} Kelvin.
 */
public abstract class FractionationRecipe extends MekanismRecipe<SingleFluidRecipeInput> implements Predicate<@NotNull FluidStack> {

    /** Maximum number of banks a fractionation tower can have (interior layers of an 18-high tower minus the sump). */
    public static final int MAX_BANKS = 15;

    private static final Holder<Item> THERMAL_FRACTIONATION_CONTROLLER = DeferredHolder.create(Registries.ITEM,
          ResourceLocation.fromNamespaceAndPath(Mod.MODID, "thermal_fractionation_controller"));

    /**
     * @param bank Zero-based index of the output bank, counted from the bottom of the tower.
     * @param stack The fluid to deposit into that bank.
     */
    public record BankOutput(int bank, FluidStack stack) {

        public static final Codec<BankOutput> CODEC = RecordCodecBuilder.create(instance -> instance.group(
              Codec.intRange(0, MAX_BANKS - 1).fieldOf("bank").forGetter(BankOutput::bank),
              FluidStack.CODEC.fieldOf("fluid").forGetter(BankOutput::stack)
        ).apply(instance, BankOutput::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, BankOutput> STREAM_CODEC = StreamCodec.composite(
              ByteBufCodecs.VAR_INT, BankOutput::bank,
              FluidStack.STREAM_CODEC, BankOutput::stack,
              BankOutput::new);
    }

    @Override
    public boolean matches(SingleFluidRecipeInput input, Level level) {
        return !isIncomplete() && test(input.fluid());
    }

    @Override
    public boolean test(FluidStack fluidStack) {
        return getInput().test(fluidStack);
    }

    /**
     * Gets the fluid ingredient fed through the valves into the sump.
     */
    public abstract FluidStackIngredient getInput();

    /**
     * For JEI/display purposes, the outputs to display.
     *
     * @return Representation of the outputs, <strong>MUST NOT</strong> be modified.
     */
    public abstract List<BankOutput> getOutputs();

    /**
     * Minimum temperature in Kelvin; below it the recipe cannot process at all.
     */
    public abstract double getMinTemperature();

    /**
     * Temperature in Kelvin at which the recipe processes one operation per tick. Between min and base the speed scales
     * linearly from zero to one; above base it keeps scaling proportionally.
     */
    public abstract double getBaseTemperature();

    @Override
    public boolean isIncomplete() {
        return getOutputs().isEmpty();
    }

    @Contract(value = "_ -> new", pure = true)
    public List<FluidStack> getOutputDefinition() {
        return getOutputs().stream().map(BankOutput::stack).toList();
    }

    @Override
    public RecipeType<FractionationRecipe> getType() {
        return ModRecipeTypes.TYPE_FRACTIONATING.value();
    }

    @Override
    public String getGroup() {
        return "fractionating";
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(THERMAL_FRACTIONATION_CONTROLLER);
    }
}
