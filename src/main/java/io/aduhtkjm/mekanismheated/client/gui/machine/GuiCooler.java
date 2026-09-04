package io.aduhtkjm.mekanismheated.client.gui.machine;

import io.aduhtkjm.mekanismheated.ModLang;
import io.aduhtkjm.mekanismheated.tile.TileEntityCooler;
import java.util.List;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.element.tab.GuiHeatTab;
import mekanism.client.gui.element.tab.GuiWarningTab;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.inventory.warning.IWarningTracker;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import io.aduhtkjm.mekanismheated.network.PacketCoolerSetEnergy;
import mekanism.common.network.PacketUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import mekanism.common.util.text.EnergyDisplay;
import mekanism.common.util.text.InputValidator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

public class GuiCooler extends GuiMekanismTile<TileEntityCooler, MekanismTileContainer<TileEntityCooler>> {

    private GuiTextField energyUsageField;

    public GuiCooler(MekanismTileContainer<TileEntityCooler> container, Inventory inv, Component title) {
        super(container, inv, title);
        inventoryLabelY += 2;
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addRenderableWidget(new GuiInnerScreen(this, 48, 23, 80, 52, () -> List.of(
              ModLang.COOLER_HOT_TEMPERATURE.translate(MekanismUtils.getTemperatureDisplay(tile.getHotTemperature(), TemperatureUnit.KELVIN, true)),
              ModLang.COOLER_COLD_TEMPERATURE.translate(MekanismUtils.getTemperatureDisplay(tile.getColdTemperature(), TemperatureUnit.KELVIN, true)),
              ModLang.COOLER_USAGE.translate(EnergyDisplay.of(tile.getEnergyContainer().getEnergyPerTick()))
        )).clearFormat());
        addRenderableWidget(new GuiVerticalPowerBar(this, tile.getEnergyContainer(), 164, 15))
              .warning(WarningType.NOT_ENOUGH_ENERGY, () -> {
                  MachineEnergyContainer<TileEntityCooler> energyContainer = tile.getEnergyContainer();
                  return energyContainer.isEmpty() && energyContainer.getEnergyPerTick() > 0;
              }).warning(WarningType.NOT_ENOUGH_ENERGY_REDUCED_RATE, () -> {
                  MachineEnergyContainer<TileEntityCooler> energyContainer = tile.getEnergyContainer();
                  return energyContainer.getEnergyPerTick() > energyContainer.getEnergy();
              });
        addRenderableWidget(new GuiEnergyTab(this, tile.getEnergyContainer(), tile::getEnergyUsed));
        addRenderableWidget(new GuiHeatTab(this, () -> {
            Component hotTemp = MekanismUtils.getTemperatureDisplay(tile.getHotTemperature(), TemperatureUnit.KELVIN, true);
            Component coldTemp = MekanismUtils.getTemperatureDisplay(tile.getColdTemperature(), TemperatureUnit.KELVIN, true);
            return List.of(
                  ModLang.COOLER_HOT_TEMPERATURE.translate(hotTemp),
                  ModLang.COOLER_COLD_TEMPERATURE.translate(coldTemp)
            );
        }));

        energyUsageField = addRenderableWidget(new GuiTextField(this, 50, 61, 76, 12));
        energyUsageField.setMaxLength(7);
        energyUsageField.setInputValidator(InputValidator.DIGIT)
              .configureDigitalInput(this::setEnergyUsage);
        setInitialFocus(energyUsageField);
    }

    @Override
    protected void drawForegroundText(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderTitleText(guiGraphics);
        renderInventoryText(guiGraphics);
        super.drawForegroundText(guiGraphics, mouseX, mouseY);
    }

    private void setEnergyUsage() {
        if (!energyUsageField.getText().isEmpty()) {
            try {
                PacketUtils.sendToServer(new PacketCoolerSetEnergy(tile.getBlockPos(),
                      MekanismUtils.convertToJoules(Math.max(0, Long.parseLong(energyUsageField.getText())))));
            } catch (NumberFormatException ignored) {
            }
            energyUsageField.setText("");
        }
    }

    @Override
    protected void addWarningTab(IWarningTracker warningTracker) {
        addRenderableWidget(new GuiWarningTab(this, warningTracker, false));
    }
}
