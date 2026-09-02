package io.aduhtkjm.mekanismheated.integration.jade;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import mekanism.api.SerializationConstants;
import mekanism.api.chemical.ChemicalStack;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.GuiUtils.TilingDirection;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.integration.lookingat.ChemicalElement;
import mekanism.common.integration.lookingat.EnergyElement;
import mekanism.common.integration.lookingat.FluidElement;
import mekanism.common.integration.lookingat.LookingAtElement;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Matrix4f;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.Element;

public enum FusedPipeMekRenderer implements IComponentProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public ResourceLocation getUid() {
        return FusedPipeMekDataProvider.UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag serverData = accessor.getServerData();
        if (!serverData.contains(FusedPipeMekDataProvider.KEY, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag mhData = serverData.getCompound(FusedPipeMekDataProvider.KEY);

        // Energy
        if (mhData.contains("energy", Tag.TAG_COMPOUND)) {
            CompoundTag energyTag = mhData.getCompound("energy");
            long energy = energyTag.getLong(SerializationConstants.ENERGY);
            long capacity = energyTag.getLong(SerializationConstants.MAX);
            if (capacity > 0) {
                tooltip.add(new MekElement(
                      Component.translatable("mekanismheated.jade.energy"),
                      new EnergyElement(energy, capacity),
                      null));
            }
        }

        // Fluid
        if (mhData.contains("fluid", Tag.TAG_COMPOUND)) {
            CompoundTag fluidTag = mhData.getCompound("fluid");
            FluidStack fluid = FluidStack.parseOptional(
                  accessor.getLevel().registryAccess(),
                  fluidTag.getCompound(SerializationConstants.FLUID));
            int capacity = fluidTag.getInt(SerializationConstants.MAX);
            if (capacity > 0) {
                tooltip.add(new MekElement(
                      Component.translatable("mekanismheated.jade.fluid"),
                      new FluidElement(fluid, capacity),
                      guiGraphics -> MekanismRenderer.color(guiGraphics, fluid)));
            }
        }

        // Chemical
        if (mhData.contains("chemical", Tag.TAG_COMPOUND)) {
            CompoundTag chemTag = mhData.getCompound("chemical");
            ChemicalStack chemical = ChemicalStack.parseOptional(
                  accessor.getLevel().registryAccess(),
                  chemTag.getCompound(SerializationConstants.CHEMICAL));
            long capacity = chemTag.getLong(SerializationConstants.MAX);
            if (capacity > 0) {
                tooltip.add(new MekElement(
                      Component.translatable("mekanismheated.jade.chemical"),
                      new ChemicalElement(chemical, capacity),
                      guiGraphics -> MekanismRenderer.color(guiGraphics, chemical)));
            }
        }

        // Heat
        if (mhData.contains("heat", Tag.TAG_COMPOUND)) {
            double temperature = mhData.getCompound("heat").getDouble("temperature");
            Component value = MekanismUtils.getTemperatureDisplay(temperature, TemperatureUnit.KELVIN, true);
            tooltip.add(new HeatElement(Component.translatable("mekanismheated.jade.heat"), value));
        }

        // Items
        if (mhData.contains("items", Tag.TAG_LIST)) {
            ListTag itemList = mhData.getList("items", Tag.TAG_COMPOUND);
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < itemList.size(); i++) {
                ItemStack stack = ItemStack.parseOptional(
                      accessor.getLevel().registryAccess(),
                      itemList.getCompound(i));
                if (!stack.isEmpty()) {
                    items.add(stack);
                }
            }
            if (!items.isEmpty()) {
                tooltip.add(new ItemElement(Component.translatable("mekanismheated.jade.items"), items));
            }
        }
    }

    private static class MekElement extends Element {

        private final Component text;
        private final LookingAtElement element;
        private final Consumer<GuiGraphics> iconTinter;

        public MekElement(Component text, LookingAtElement element, Consumer<GuiGraphics> iconTinter) {
            this.text = text;
            this.element = element;
            this.iconTinter = iconTinter;
        }

        @Override
        public Vec2 getSize() {
            int width = Math.max(element.getWidth(), 96);
            int height = element.getHeight() + 2 + 14;
            return new Vec2(width, height);
        }

        @Override
        public void render(GuiGraphics guiGraphics, float rawX, float rawY, float maxX, float maxY) {
            int x = Mth.floor(rawX);
            int y = Mth.floor(rawY);
            //Jade positions elements via render coordinates rather than pose translation, and scales
            //the whole tooltip down (TooltipRect.scale) once it gets tall. Mekanism's drawScrollingString
            //builds its scissor from the pose translation alone (ignoring that scale), so under the scale
            //the scissor no longer lines up with the scaled text and scrolling strings get clipped away and
            //vanish. We therefore translate the pose to the element origin and draw the scrolling text
            //ourselves, computing the scissor in absolute screen space from the current pose scale.
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(x, y, 0);
            drawScaledScrollingString(guiGraphics, text, 0, 3, element.getWidth(), 0xFFFFFF);
            renderBody(guiGraphics, element, iconTinter, 0, 14);
            guiGraphics.pose().popPose();
        }

        //Reimplements LookingAtElement#render (border + fill icon + value) but draws the value with a
        //scale-aware scissor (see drawScaledScrollingString) so it is not clipped when Jade scales the tooltip.
        private static void renderBody(GuiGraphics guiGraphics, LookingAtElement element, Consumer<GuiGraphics> iconTinter, int x, int y) {
            int width = element.getWidth();
            int height = element.getHeight();
            guiGraphics.fill(x, y, x + width - 1, y + 1, 0xFF000000);
            guiGraphics.fill(x, y, x + 1, y + height - 1, 0xFF000000);
            guiGraphics.fill(x + width - 1, y, x + width, y + height - 1, 0xFF000000);
            guiGraphics.fill(x, y + height - 1, x + width, y + height, 0xFF000000);
            TextureAtlasSprite icon = element.getIcon();
            if (icon != null) {
                int fillLevel = element.getScaledLevel(width - 2);
                if (fillLevel > 0) {
                    if (iconTinter != null) {
                        iconTinter.accept(guiGraphics);
                    }
                    GuiUtils.drawTiledSprite(guiGraphics, x + 1, y + 1, height - 2, fillLevel, height - 2,
                          icon, 16, 16, 0, TilingDirection.DOWN_RIGHT);
                    if (iconTinter != null) {
                        MekanismRenderer.resetColor(guiGraphics);
                    }
                }
            }
            drawScaledScrollingString(guiGraphics, element.getText(), x, y + 3, width, 0xFFFFFF);
        }

        //Like Mekanism's drawScrollingString, but derives the scissor from the pose's absolute translation
        //multiplied by its current scale, so it stays aligned with the text even when the GUI is scaled.
        private static void drawScaledScrollingString(GuiGraphics guiGraphics, Component text, int x, int y, int width, int color) {
            Font font = Minecraft.getInstance().font;
            int minX = x + 4;
            int maxX = x + width - 4;
            int minY = y;
            int maxY = y + font.lineHeight;
            int textWidth = font.width(text);
            boolean scrolling = textWidth > maxX - minX;
            float targetY = (minY + maxY - font.lineHeight) / 2F;
            float targetX = scrolling ? minX - (float) getOverflowedBy(font, textWidth - (maxX - minX)) : minX;
            if (scrolling) {
                Matrix4f pose = guiGraphics.pose().last().pose();
                float scale = pose.m00();
                int left = (int) pose.m30();
                int top = (int) pose.m31();
                guiGraphics.enableScissor(
                      (int) (left + scale * minX), (int) (top + scale * minY),
                      (int) (left + scale * maxX), (int) (top + scale * maxY));
            }
            guiGraphics.drawString(font, text.getVisualOrderText(), targetX, targetY, color, false);
            if (scrolling) {
                guiGraphics.disableScissor();
            }
        }

        //Mirrors Mekanism's IFancyFontRenderer#getOverflowedBy (itself a port of vanilla's AbstractWidget
        //renderScrollingString) so the scroll speed matches. Vanilla's PERIOD_PER_SCROLLED_PIXEL=0.5 and
        //MIN_SCROLL_PERIOD=3.0 are private, so they are inlined here.
        private static double getOverflowedBy(Font font, double overflowWidth) {
            double seconds = Util.getMillis() / 1_000D;
            double scrollPeriod = Math.max(overflowWidth * 0.5D, 3.0D);
            double modifier = Math.cos((2 * Math.PI) * seconds / scrollPeriod);
            if (!font.isBidirectional()) {
                modifier = -modifier;
            }
            return (Math.sin((Math.PI / 2) * modifier) / 2.0 + 0.5) * overflowWidth;
        }
    }

    /**
     * Renders the network's temperature as a plain text value under a label, with no bar: the fused
     * pipe heat function has no meaningful stored/capacity ratio to display, only the temperature.
     */
    private static class HeatElement extends Element {

        private final Component label;
        private final Component value;

        public HeatElement(Component label, Component value) {
            this.label = label;
            this.value = value;
        }

        @Override
        public Vec2 getSize() {
            return new Vec2(96, 28);
        }

        @Override
        public void render(GuiGraphics guiGraphics, float rawX, float rawY, float maxX, float maxY) {
            int x = Mth.floor(rawX);
            int y = Mth.floor(rawY);
            guiGraphics.drawString(Minecraft.getInstance().font, label, x, y + 3, 0xFFFFFF, true);
            guiGraphics.drawString(Minecraft.getInstance().font, value, x, y + 15, 0xFFFFFF, true);
        }
    }

    /**
     * Renders a compact row of item icons (each with its buffered count) under a label, so the pipe
     * contents stay a single line regardless of how many kinds are in the buffer.
     */
    private static class ItemElement extends Element {

        private final Component label;
        private final List<ItemStack> items;

        public ItemElement(Component label, List<ItemStack> items) {
            this.label = label;
            this.items = items;
        }

        @Override
        public Vec2 getSize() {
            return new Vec2(96, 32);
        }

        @Override
        public void render(GuiGraphics guiGraphics, float rawX, float rawY, float maxX, float maxY) {
            int x = Mth.floor(rawX);
            int y = Mth.floor(rawY);
            guiGraphics.drawString(Minecraft.getInstance().font, label, x, y + 3, 0xFFFFFF, true);
            int iconX = x + 1;
            int iconY = y + 14;
            for (ItemStack stack : items) {
                guiGraphics.renderItem(stack, iconX, iconY);
                iconX += 19;
            }
        }
    }
}
