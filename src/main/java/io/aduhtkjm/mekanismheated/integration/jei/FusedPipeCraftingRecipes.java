package io.aduhtkjm.mekanismheated.integration.jei;

import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedFunction;
import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedPipeConfig;
import io.aduhtkjm.mekanismheated.recipe.FusedPipeRecipe;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import org.lwjgl.system.NonnullDefault;

/**
 * Expands our single data driven {@link FusedPipeRecipe} into the concrete crafting recipes JEI shows.
 *
 * <p>{@code FusedPipeRecipe} picks the functions it enables from whatever sits in the grid, so it declares no
 * ingredients of its own ({@code Recipe#getIngredients()} is empty) and JEI's crafting category validator silently
 * drops it. To keep the crafting tab useful, every non-empty combination of the recipe's function ingredients is
 * materialised here as an ordinary {@link ShapelessRecipe} carrying that variant's
 * {@code BLOCK_ENTITY_DATA}.</p>
 *
 * <p>These copies exist only inside JEI: they are never added to the recipe manager, so crafting is still resolved
 * by the one dynamic recipe and datapack changes to the fused pipe recipe are picked up on the next reload.</p>
 */
@NonnullDefault
public final class FusedPipeCraftingRecipes {

    /**
     * The id prefix for the synthetic recipes, keeping them clearly apart from the real
     * {@code mekanismheated:fused_pipe/...} data recipes. JEI reports these ids as a recipe's registry name.
     */
    private static final String ID_PREFIX = "jei_fused_pipe/";

    private FusedPipeCraftingRecipes() {
    }

    /**
     * Collects every fused pipe variant offered by the loaded recipes.
     *
     * @param level The client level whose recipe manager holds the data driven fused pipe recipes.
     * @return One holder per variant, ready to be handed to {@code IRecipeRegistration#addRecipes}.
     */
    public static List<RecipeHolder<CraftingRecipe>> create(Level level) {
        List<RecipeHolder<CraftingRecipe>> variants = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if (holder.value() instanceof FusedPipeRecipe recipe) {
                addVariants(variants, holder.id(), recipe);
            }
        }
        return variants;
    }

    /**
     * Adds one recipe per non-empty combination of the functions the given recipe has an ingredient for. The empty
     * combination is skipped: {@code FusedPipeRecipe#matches} rejects a grid that enables no function, so such a pipe
     * cannot be crafted and showing it would be a lie.
     */
    private static void addVariants(List<RecipeHolder<CraftingRecipe>> variants, ResourceLocation recipeId, FusedPipeRecipe recipe) {
        Map<FusedFunction, Ingredient> fn2Ingredient = new EnumMap<>(FusedFunction.class);
        for (FusedPipeRecipe.FunctionEntry entry : recipe.getFunctions()) {
            fn2Ingredient.putIfAbsent(entry.function(), entry.item());
        }

        // Walk FusedFunction's own order instead of the JSON's, so the grid layout is stable across datapacks.
        List<FusedFunction> functions = new ArrayList<>();
        for (FusedFunction function : FusedFunction.VALUES) {
            if (fn2Ingredient.containsKey(function)) {
                functions.add(function);
            }
        }

        // Starting at 1 skips the rejected empty set.
        for (int mask = 1; mask < (1 << functions.size()); mask++) {
            List<FusedFunction> enabled = new ArrayList<>();
            NonNullList<Ingredient> ingredients = NonNullList.create();
            for (int i = 0; i < recipe.getBaseCount(); i++) {
                ingredients.add(recipe.getBase());
            }
            for (int i = 0; i < functions.size(); i++) {
                if ((mask & (1 << i)) != 0) {
                    FusedFunction function = functions.get(i);
                    enabled.add(function);
                    ingredients.add(fn2Ingredient.get(function));
                }
            }
            variants.add(new RecipeHolder<>(variantId(recipeId, enabled), createRecipe(recipe, enabled, ingredients)));
        }
    }

    private static ShapelessRecipe createRecipe(FusedPipeRecipe recipe, List<FusedFunction> enabled, NonNullList<Ingredient> ingredients) {
        // The recipe's result already carries the default per-function block entity data, so copy it and
        // overwrite that component with this variant's function set.
        ItemStack result = recipe.getResultRaw().copy();
        result.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(FusedPipeConfig.createBlockEntityData(enabled)));
        return new ShapelessRecipe(recipe.getGroup(), recipe.category(), result, ingredients);
    }

    /**
     * Builds a readable, collision free id such as
     * {@code mekanismheated:jei_fused_pipe/fused_pipe_energy_fluid}. Keeps the id in the namespace of the recipe it was
     * expanded from, so fused pipe recipes added by other datapacks cannot collide with ours.
     */
    private static ResourceLocation variantId(ResourceLocation recipeId, List<FusedFunction> enabled) {
        StringBuilder path = new StringBuilder(ID_PREFIX).append(recipeId.getPath().replace('/', '_'));
        for (FusedFunction function : enabled) {
            path.append('_').append(function.getSerializedName());
        }
        return ResourceLocation.fromNamespaceAndPath(recipeId.getNamespace(), path.toString());
    }
}
