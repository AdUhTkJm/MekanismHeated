package io.aduhtkjm.mekanismheated.client.gui.machine;

import io.aduhtkjm.mekanismheated.ModLang;
import io.aduhtkjm.mekanismheated.integration.jei.ModRecipeViewerTypes;
import io.aduhtkjm.mekanismheated.tile.multiblock.RetroentropicArrayData;
import io.aduhtkjm.mekanismheated.tile.multiblock.TileEntityRetroentropicArrayCasing;
import java.util.List;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.IProgressInfoHandler;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.tab.GuiHeatTab;
import mekanism.common.MekanismLang;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.system.NonnullDefault;

/**
 * GUI of the Retroentropic Array. The array has no separate controller block, so this screen is opened from any formed
 * casing. It shows the shared input/output slots, the backtrack progress and the array's (sub-zero) temperature.
 */
@NonnullDefault
public class GuiRetroentropicArray
      extends GuiMekanismTile<TileEntityRetroentropicArrayCasing, MekanismTileContainer<TileEntityRetroentropicArrayCasing>> {

    public GuiRetroentropicArray(MekanismTileContainer<TileEntityRetroentropicArrayCasing> container, Inventory inv, Component title) {
        super(container, inv, ModLang.GUI_RETROENTROPIC_ARRAY.translate());
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addRenderableWidget(new GuiProgress(() ->
            tile.getMultiblock().getScaledProgress(), ProgressType.BAR, this, 80, 38)
                .recipeViewerCategories(ModRecipeViewerTypes.RETROENTROPIC_ARRAY_PROCESSING));
        addRenderableWidget(new GuiHeatTab(this, () -> {
            var multiblock = tile.getMultiblock();
            Component temp = MekanismUtils.getTemperatureDisplay(multiblock.getTemperature(), TemperatureUnit.KELVIN, true);
            Component transfer = MekanismUtils.getTemperatureDisplay(multiblock.getLastTransferLoss(), TemperatureUnit.KELVIN, false);
            Component environment = MekanismUtils.getTemperatureDisplay(multiblock.getLastEnvironmentLoss(), TemperatureUnit.KELVIN, false);
            return List.of(MekanismLang.TEMPERATURE.translate(temp), MekanismLang.TRANSFERRED_RATE.translate(transfer), MekanismLang.DISSIPATED_RATE.translate(environment));
        }));
    }

    @Override
    protected void drawForegroundText(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderTitleText(guiGraphics);
        renderInventoryText(guiGraphics);
        super.drawForegroundText(guiGraphics, mouseX, mouseY);
    }
}
