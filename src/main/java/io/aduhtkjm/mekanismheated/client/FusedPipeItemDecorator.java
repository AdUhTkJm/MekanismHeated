package io.aduhtkjm.mekanismheated.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedPipeConfig;
import mekanism.api.tier.BaseTier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.client.IItemDecorator;
import org.lwjgl.system.NonnullDefault;

import java.util.EnumMap;
import java.util.Map;

/**
 * Draws a flat 16x16 icon for the fused pipe item, covering the default 3D block-model render in
 * GUIs (inventory, tooltips, JEI, ...). This mirrors how Mekanism gives its transmitters (e.g. the
 * universal cable) a specifically-drawn icon via an {@link IItemDecorator} (see Mekanism's
 * {@code TransmitterTypeDecorator}); the item still renders as its normal 3D model when held.
 *
 * <p>The texture is chosen by the pipe's displayed tier, so each tier (basic/advanced/elite/
 * ultimate) has its own separate, tweakable icon at
 * {@code mekanismheated:textures/item/fused_pipe_<tier>.png}.
 */
@NonnullDefault
public class FusedPipeItemDecorator implements IItemDecorator {

    private static final BaseTier[] ICON_TIERS = {BaseTier.BASIC, BaseTier.ADVANCED, BaseTier.ELITE, BaseTier.ULTIMATE};
    private static final Map<BaseTier, ResourceLocation> TEXTURES = new EnumMap<>(BaseTier.class);

    static {
        for (BaseTier tier : ICON_TIERS) {
            TEXTURES.put(tier, ResourceLocation.fromNamespaceAndPath(Mod.MODID, "textures/item/fused_pipe_" + tier.getLowerName() + ".png"));
        }
    }

    @Override
    public boolean render(GuiGraphics guiGraphics, Font font, ItemStack stack, int xOffset, int yOffset) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation texture = TEXTURES.getOrDefault(displayTier(stack), TEXTURES.get(BaseTier.BASIC));
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        // Push forward in Z so the flat icon draws on top of the 3D model already rendered.
        pose.translate(0, 0, 200);
        guiGraphics.blit(texture, xOffset, yOffset, 0, 0, 16, 16, 16, 16);
        pose.popPose();
        return true;
    }

    /**
     * Reads the displayed tier from the stack's {@code BLOCK_ENTITY_DATA} component, falling back
     * to {@link BaseTier#BASIC}. Same source as the {@code mekanismheated:tier} item property.
     */
    private static BaseTier displayTier(ItemStack stack) {
        CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data == null) {
            return BaseTier.BASIC;
        }
        CompoundTag tag = data.copyTag();
        if (!tag.contains(FusedPipeConfig.TAG_CONFIG, Tag.TAG_COMPOUND)) {
            return BaseTier.BASIC;
        }
        return FusedPipeConfig.displayTier(tag.getCompound(FusedPipeConfig.TAG_CONFIG));
    }
}
