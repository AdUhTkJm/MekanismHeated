package io.aduhtkjm.mekanismheated.integration.jei;

import java.util.List;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.NonnullDefault;

/**
 * Recipe viewer type pairing one of our machines with its category layout.
 *
 * <p>Mirrors Mekanism's {@code RVRecipeTypeWrapper}, but without the recipe type provider part: addons cannot create
 * instances of Mekanism's internal {@code MekanismRecipeType} (private constructor, mekanism-only namespace check), so
 * our categories are fed from the vanilla recipe manager instead of an input cache. Always uses holder based recipes as
 * our recipes are registered in the recipe manager.</p>
 */
@NonnullDefault
public record ModRecipeViewerType<RECIPE>(ResourceLocation id, ItemLike iconItem, int xOffset, int yOffset, int width, int height)
      implements IRecipeViewerRecipeType<RECIPE> {

    @Override
    public Class<? extends RECIPE> recipeClass() {
        //Only needed for non-holder recipe types, which we never are
        throw new UnsupportedOperationException("Holder based recipe viewer types do not track their recipe class");
    }

    @Override
    public boolean requiresHolder() {
        return true;
    }

    @Override
    public ItemStack iconStack() {
        return new ItemStack(iconItem);
    }

    @Nullable
    @Override
    public ResourceLocation icon() {
        //Handled by the icon stack
        return null;
    }

    @Override
    public Component getTextComponent() {
        return Component.translatable(iconItem.asItem().getDescriptionId());
    }

    @Override
    public List<ItemLike> workstations() {
        return List.of(iconItem);
    }
}
