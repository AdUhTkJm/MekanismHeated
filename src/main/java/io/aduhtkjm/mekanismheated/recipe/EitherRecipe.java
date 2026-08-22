package io.aduhtkjm.mekanismheated.recipe;

import java.util.function.Predicate;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * A union of two recipes, holding exactly one of the two types. Instances are constructed at runtime from existing
 * recipe lookups and are never serialized/deserialized, so {@link #getSerializer()} and {@link #getType()} return
 * {@code null}.
 *
 * @param <INPUT> The vanilla input type shared by both recipe types (e.g. {@code SingleRecipeInput}).
 * @param <T>     The left (item-to-item) recipe type.
 * @param <U>     The right (item-to-fluid) recipe type.
 */
@NothingNullByDefault
public class EitherRecipe<INPUT extends RecipeInput, T extends MekanismRecipe<INPUT> & Predicate<ItemStack>, U extends MekanismRecipe<INPUT> & Predicate<ItemStack>>
      extends MekanismRecipe<INPUT> implements Predicate<ItemStack> {

    @Nullable
    private final T t;
    @Nullable
    private final U u;

    protected EitherRecipe(@Nullable T t, @Nullable U u) {
        this.t = t;
        this.u = u;
    }

    public static <INPUT extends RecipeInput, T extends MekanismRecipe<INPUT> & Predicate<ItemStack>, U extends MekanismRecipe<INPUT> & Predicate<ItemStack>>
    EitherRecipe<INPUT, T, U> left(T t) {
        return new EitherRecipe<>(t, null);
    }

    public static <INPUT extends RecipeInput, T extends MekanismRecipe<INPUT> & Predicate<ItemStack>, U extends MekanismRecipe<INPUT> & Predicate<ItemStack>>
    EitherRecipe<INPUT, T, U> right(U u) {
        return new EitherRecipe<>(null, u);
    }

    public boolean isLeft() {
        return t != null;
    }

    public boolean isRight() {
        return u != null;
    }

    public T getLeft() {
        assert t != null;
        return t;
    }

    public U getRight() {
        assert u != null;
        return u;
    }

    @Override
    public boolean test(ItemStack itemStack) {
        if (t != null) {
            return t.test(itemStack);
        } else if (u != null) {
            return u.test(itemStack);
        }
        return false;
    }

    @Override
    public boolean matches(INPUT input, Level level) {
        if (t != null) {
            return t.matches(input, level);
        } else if (u != null) {
            return u.matches(input, level);
        }
        return false;
    }

    @Override
    public boolean isIncomplete() {
        if (t != null) {
            return t.isIncomplete();
        } else if (u != null) {
            return u.isIncomplete();
        }
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return null;
    }

    @Override
    public RecipeType<?> getType() {
        return null;
    }
}
