package io.aduhtkjm.mekanismheated.recipe;

import java.util.List;
import java.util.Objects;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.fluids.FluidStack;

@NothingNullByDefault
public class BasicFractionationRecipe extends FractionationRecipe {

    private final FluidStackIngredient input;
    private final List<BankOutput> outputs;
    private final double minTemperature;
    private final double maxTemperature;
    private final double baseTemperature;

    /**
     * @param input           Fluid input ingredient, fed into the tower's sump.
     * @param outputs         One or more outputs, each targeting a bank index counted from the bottom of the tower.
     * @param minTemperature  Minimum temperature in Kelvin required to process; must be greater than zero.
     * @param baseTemperature Temperature in Kelvin for nominal (one operation per tick) speed; must be at least the minimum.
     */
    public BasicFractionationRecipe(FluidStackIngredient input, List<BankOutput> outputs, double minTemperature, double maxTemperature, double baseTemperature) {
        this.input = Objects.requireNonNull(input, "Fluid input cannot be null.");
        Objects.requireNonNull(outputs, "Outputs cannot be null.");
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("Fractionation recipes must have at least one output.");
        }
        boolean[] seenBanks = new boolean[MAX_BANKS];
        for (BankOutput output : outputs) {
            Objects.requireNonNull(output, "Output cannot be null.");
            Objects.requireNonNull(output.stack(), "Output fluid cannot be null.");
            if (output.stack().isEmpty() || output.stack().getAmount() <= 0) {
                throw new IllegalArgumentException("Output fluid amount must be positive.");
            }
            if (output.bank() < 0 || output.bank() >= MAX_BANKS) {
                throw new IllegalArgumentException("Output bank index must be between 0 and " + (MAX_BANKS - 1) + ", got " + output.bank() + ".");
            }
            if (seenBanks[output.bank()]) {
                throw new IllegalArgumentException("Duplicate output bank index " + output.bank() + ".");
            }
            seenBanks[output.bank()] = true;
        }
        if (minTemperature <= 0) {
            throw new IllegalArgumentException("Minimum temperature must be greater than zero.");
        }
        if (baseTemperature < minTemperature) {
            throw new IllegalArgumentException("Base temperature must be at least the minimum temperature.");
        }
        this.outputs = List.copyOf(outputs);
        this.minTemperature = minTemperature;
        this.maxTemperature = maxTemperature;
        this.baseTemperature = baseTemperature;
    }

    @Override
    public FluidStackIngredient getInput() {
        return input;
    }

    @Override
    public List<BankOutput> getOutputs() {
        return outputs;
    }

    /**
     * For serializer use. DO NOT MODIFY RETURN VALUE.
     */
    public List<BankOutput> getOutputsRaw() {
        return outputs;
    }

    @Override
    public double getMinTemperature() {
        return minTemperature;
    }

    @Override
    public double getMaxTemperature() {
        return maxTemperature;
    }

    @Override
    public double getBaseTemperature() {
        return baseTemperature;
    }

    @Override
    public RecipeSerializer<BasicFractionationRecipe> getSerializer() {
        return ModRecipeSerializers.FRACTIONATING.get();
    }

    /**
     * Convenience helper returning the amount of input fluid one operation consumes, or zero if the stack does not match.
     */
    public int getInputAmount(FluidStack stored) {
        FluidStack match = input.getMatchingInstance(stored);
        return match.isEmpty() ? 0 : match.getAmount();
    }
}
