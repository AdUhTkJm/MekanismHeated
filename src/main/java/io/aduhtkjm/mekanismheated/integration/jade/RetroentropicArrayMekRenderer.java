package io.aduhtkjm.mekanismheated.integration.jade;

import io.aduhtkjm.mekanismheated.ModLang;
import io.aduhtkjm.mekanismheated.tile.multiblock.TileEntityRetroentropicArrayCasing;
import java.util.List;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.Element;

/**
 * Renders the Retroentropic Array state sent by {@link RetroentropicArrayMekDataProvider}: the input/output items as
 * icons, followed by the array's temperature and backtrack progress as plain text.
 */
public enum RetroentropicArrayMekRenderer implements IComponentProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public ResourceLocation getUid() {
        return RetroentropicArrayMekDataProvider.UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof TileEntityRetroentropicArrayCasing)) {
            return;
        }
        CompoundTag serverData = accessor.getServerData();
        if (!serverData.contains(RetroentropicArrayMekDataProvider.KEY, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag mhData = serverData.getCompound(RetroentropicArrayMekDataProvider.KEY);
        var registryAccess = accessor.getLevel().registryAccess();
        addItem(tooltip, registryAccess, mhData, "input", Component.translatable("mekanismheated.jade.input"));
        addItem(tooltip, registryAccess, mhData, "output", Component.translatable("mekanismheated.jade.output"));
        double temperature = mhData.getDouble("temperature");
        tooltip.add(new TextElement(Component.translatable("mekanismheated.jade.temperature",
              MekanismUtils.getTemperatureDisplay(temperature, TemperatureUnit.KELVIN, true))));
        int duration = mhData.getInt("duration");
        if (duration > 0) {
            tooltip.add(new TextElement(ModLang.GUI_RETROENTROPIC_ARRAY_PROGRESS.translate(mhData.getInt("backtracked"), duration)));
        }
        if (temperature >= 0) {
            tooltip.add(new TextElement(ModLang.GUI_RETROENTROPIC_ARRAY_STATUS_TOO_WARM.translate()));
        } else if (mhData.getBoolean("processing")) {
            tooltip.add(new TextElement(ModLang.GUI_RETROENTROPIC_ARRAY_STATUS_ACTIVE.translate()));
        }
    }

    private static void addItem(ITooltip tooltip, HolderLookup.Provider registryAccess, CompoundTag data, String key, Component label) {
        if (data.contains(key, Tag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.parseOptional(registryAccess, data.getCompound(key));
            if (!stack.isEmpty()) {
                tooltip.add(new ItemElement(label, List.of(stack)));
            }
        }
    }

    /**
     * Renders item icons (with their stack counts) under a label.
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

    /**
     * Renders a single line of text.
     */
    private static class TextElement extends Element {

        private final Component text;

        public TextElement(Component text) {
            this.text = text;
        }

        @Override
        public Vec2 getSize() {
            Font font = Minecraft.getInstance().font;
            return new Vec2(Math.max(font.width(text) + 8, 96), font.lineHeight + 6);
        }

        @Override
        public void render(GuiGraphics guiGraphics, float rawX, float rawY, float maxX, float maxY) {
            int x = Mth.floor(rawX);
            int y = Mth.floor(rawY);
            guiGraphics.drawString(Minecraft.getInstance().font, text, x, y + 3, 0xFFFFFF, true);
        }
    }
}
