package io.aduhtkjm.mekanismheated.recipe;

import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.NonnullDefault;

import java.util.function.Predicate;

@NonnullDefault
public class HeatSmelterRecipe
    extends EitherRecipe<SingleRecipeInput, EitherRecipe<SingleRecipeInput, HeatedItemStackToItemStackRecipe, HeatedItemStackToFluidRecipe>, ItemStackToItemStackRecipe>
    implements Predicate<@NotNull ItemStack> {
    public HeatSmelterRecipe(@Nullable HeatedItemStackToItemStackRecipe oversmelt, @Nullable HeatedItemStackToFluidRecipe melt, @Nullable ItemStackToItemStackRecipe smelt) {
        super(oversmelt == null && melt == null ? null : new EitherRecipe<>(oversmelt, melt), smelt);
    }

    public static HeatSmelterRecipe oversmelt(HeatedItemStackToItemStackRecipe recipe) {
        return new HeatSmelterRecipe(recipe, null, null);
    }

    public static HeatSmelterRecipe melt(HeatedItemStackToFluidRecipe recipe) {
        return new HeatSmelterRecipe(null, recipe, null);
    }

    public static HeatSmelterRecipe smelt(ItemStackToItemStackRecipe recipe) {
        return new HeatSmelterRecipe(null, null, recipe);
    }

    public HeatedItemStackToItemStackRecipe getOversmelt() {
        return getLeft().getLeft();
    }

    public HeatedItemStackToFluidRecipe getMelt() {
        return getLeft().getRight();
    }

    public ItemStackToItemStackRecipe getSmelt() {
        return getRight();
    }

    public boolean isOversmelt() {
        if (!isLeft())
            return false;

        return getLeft().isLeft();
    }

    public boolean isMelt() {
        if (!isLeft())
            return false;

        return getLeft().isRight();
    }

    public boolean isSmelt() {
        return isRight();
    }

    public boolean isItemOutput() {
        return isOversmelt() || isSmelt();
    }

    public boolean isFluidOutput() {
        return isMelt();
    }

    public ItemStackIngredient getInput() {
        if (isOversmelt())
            return getOversmelt().getInput();
        if (isSmelt())
            return getSmelt().getInput();
        if (isMelt())
            return getMelt().getInput();
        throw new IllegalStateException("This heat smelter recipe should not be empty");
    }

    public ItemStack getItemOutput(ItemStack input) {
        if (isOversmelt())
            return getOversmelt().getOutput(input);
        return getSmelt().getOutput(input);
    }

    public FluidStack getFluidOutput(ItemStack input) {
        return getMelt().getOutput(input);
    }

    @Override
    public boolean test(ItemStack itemStack) {
        if (isOversmelt())
            return getOversmelt().test(itemStack);
        if (isSmelt())
            return getSmelt().test(itemStack);
        if (isMelt())
            return getMelt().test(itemStack);
        return false;
    }
}
