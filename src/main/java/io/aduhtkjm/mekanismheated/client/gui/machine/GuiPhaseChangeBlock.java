package io.aduhtkjm.mekanismheated.client.gui.machine;

import io.aduhtkjm.mekanismheated.ModLang;
import io.aduhtkjm.mekanismheated.tile.TileEntityPhaseChangeBlock;
import java.util.List;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.tab.GuiHeatTab;
import mekanism.common.MekanismLang;
import mekanism.common.inventory.container.tile.EmptyTileContainer;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import mekanism.common.util.text.TextUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * Screen for the phase-change blocks: a read-only inner screen showing the block's temperature, its melting point and
 * how much latent heat its buffer holds, plus Mekanism's usual heat tab.
 */
public class GuiPhaseChangeBlock extends GuiMekanismTile<TileEntityPhaseChangeBlock, EmptyTileContainer<TileEntityPhaseChangeBlock>> {

    public GuiPhaseChangeBlock(EmptyTileContainer<TileEntityPhaseChangeBlock> container, Inventory inv, Component title) {
        super(container, inv, title);
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addRenderableWidget(new GuiInnerScreen(this, 48, 23, 80, 54, () -> List.of(
              MekanismLang.TEMPERATURE.translate(MekanismUtils.getTemperatureDisplay(tile.getTotalTemperature(), TemperatureUnit.KELVIN, true)),
              ModLang.GUI_PHASE_CHANGE_MELTING_POINT.translate(MekanismUtils.getTemperatureDisplay(tile.getMeltingPoint(), TemperatureUnit.KELVIN, true)),
              ModLang.GUI_PHASE_CHANGE_BUFFER.translate(String.format("%.3f", tile.getBufferedHeat())),
              ModLang.GUI_PHASE_CHANGE_CAPACITY.translate(TextUtils.format(tile.getBufferCapacity()))
        )).clearFormat());
        addRenderableWidget(new GuiHeatTab(this, () -> {
            Component temp = MekanismUtils.getTemperatureDisplay(tile.getTotalTemperature(), TemperatureUnit.KELVIN, true);
            Component transfer = MekanismUtils.getTemperatureDisplay(tile.getLastTransferLoss(), TemperatureUnit.KELVIN, false);
            Component environment = MekanismUtils.getTemperatureDisplay(tile.getLastEnvironmentLoss(), TemperatureUnit.KELVIN, false);
            return List.of(MekanismLang.TEMPERATURE.translate(temp), MekanismLang.TRANSFERRED_RATE.translate(transfer), MekanismLang.DISSIPATED_RATE.translate(environment));
        }));
    }

    @Override
    protected void drawForegroundText(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderTitleText(guiGraphics);
        super.drawForegroundText(guiGraphics, mouseX, mouseY);
    }
}
