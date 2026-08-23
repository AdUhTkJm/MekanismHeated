package io.aduhtkjm.mekanismheated.client.gui.machine;

import io.aduhtkjm.mekanismheated.ModLang;
import io.aduhtkjm.mekanismheated.client.gui.element.GuiMixedFluidGauge;
import io.aduhtkjm.mekanismheated.content.fractionation.FractionationMultiblockData;
import io.aduhtkjm.mekanismheated.tile.multiblock.TileEntityThermalFractionationController;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.GuiDownArrow;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.bar.GuiBar.IBarInfoHandler;
import mekanism.client.gui.element.bar.GuiHorizontalRateBar;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.tab.GuiHeatTab;
import mekanism.common.MekanismLang;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * Controller GUI of a Thermal Fractionation Tower. A single mixed fluid gauge on the left shows every tank of the tower
 * stacked in physical order (feed sump at the bottom, output banks above), the center holds the status screen.
 */
public class GuiThermalFractionationController
      extends GuiMekanismTile<TileEntityThermalFractionationController, MekanismTileContainer<TileEntityThermalFractionationController>> {

    private GuiElement mixedGauge;

    public GuiThermalFractionationController(MekanismTileContainer<TileEntityThermalFractionationController> container, Inventory inv, Component title) {
        super(container, inv, title);
        imageWidth += 20;
        inventoryLabelX += 10;
        inventoryLabelY += 2;
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addRenderableWidget(new GuiInnerScreen(this, 48, 19, 130, 40, () -> List.of(
              MekanismLang.MULTIBLOCK_FORMED.translate(),
              ModLang.GUI_FRACTIONATION_HEIGHT.translate(tile.getMultiblock().height()),
              ModLang.GUI_FRACTIONATION_LAYERS.translate(tile.getMultiblock().getBankCount()),
              MekanismLang.TEMPERATURE.translate(MekanismUtils.getTemperatureDisplay(tile.getMultiblock().getTemperature(), TemperatureUnit.KELVIN, true))
        )).padding(3).clearSpacing());
        mixedGauge = addRenderableWidget(new GuiMixedFluidGauge(this::getLayers, GaugeType.STANDARD, this, 15, 13));
        addRenderableWidget(new GuiHorizontalRateBar(this, new IBarInfoHandler() {
            @Override
            public Component getTooltip() {
                return MekanismUtils.getTemperatureDisplay(tile.getMultiblock().getTemperature(), TemperatureUnit.KELVIN, true);
            }

            @Override
            public double getLevel() {
                return Math.min(1, tile.getMultiblock().getTemperature() / FractionationMultiblockData.MAX_DISPLAY_TEMPERATURE);
            }
        }, 58, 62));
        addRenderableWidget(new GuiHeatTab(this, () -> {
            Component environment = MekanismUtils.getTemperatureDisplay(tile.getMultiblock().lastEnvironmentLoss, TemperatureUnit.KELVIN, false);
            return Collections.singletonList(MekanismLang.DISSIPATED_RATE.translate(environment));
        }));
    }

    /**
     * @return all tanks of the tower ordered bottom to top: feed sump first, then the output banks.
     */
    private List<IExtendedFluidTank> getLayers() {
        FractionationMultiblockData multiblock = tile.getMultiblock();
        List<IExtendedFluidTank> layers = new ArrayList<>(multiblock.getBankCount() + 1);
        layers.add(multiblock.inputTank);
        layers.addAll(multiblock.getOutputBanks());
        return layers;
    }

    @Override
    protected void drawForegroundText(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderTitleTextWithOffset(guiGraphics, mixedGauge.getRelativeRight());
        renderInventoryText(guiGraphics);
        super.drawForegroundText(guiGraphics, mouseX, mouseY);
    }
}
