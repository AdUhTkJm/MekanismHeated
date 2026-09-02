package io.aduhtkjm.mekanismheated.client.gui.machine;

import io.aduhtkjm.mekanismheated.ModLang;
import io.aduhtkjm.mekanismheated.network.PacketSetHeatTarget;
import io.aduhtkjm.mekanismheated.tile.TileEntityCreativeHeatBlock;
import java.util.List;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.tab.GuiHeatTab;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.common.MekanismLang;
import mekanism.common.inventory.container.tile.EmptyTileContainer;
import mekanism.common.network.PacketUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import mekanism.common.util.text.InputValidator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

public class GuiCreativeHeatBlock extends GuiMekanismTile<TileEntityCreativeHeatBlock, EmptyTileContainer<TileEntityCreativeHeatBlock>> {

    private GuiTextField targetTempField;

    public GuiCreativeHeatBlock(EmptyTileContainer<TileEntityCreativeHeatBlock> container, Inventory inv, Component title) {
        super(container, inv, title);
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addRenderableWidget(new GuiInnerScreen(this, 48, 23, 80, 42, () -> List.of(
              MekanismLang.TEMPERATURE.translate(MekanismUtils.getTemperatureDisplay(tile.getTotalTemperature(), TemperatureUnit.KELVIN, true)),
              ModLang.GUI_TARGET_TEMPERATURE.translate(MekanismUtils.getTemperatureDisplay(tile.getTargetTemperature(), TemperatureUnit.KELVIN, true))
        )).clearFormat());
        addRenderableWidget(new GuiHeatTab(this, () -> {
            Component temp = MekanismUtils.getTemperatureDisplay(tile.getTotalTemperature(), TemperatureUnit.KELVIN, true);
            Component transfer = MekanismUtils.getTemperatureDisplay(tile.getLastTransferLoss(), TemperatureUnit.KELVIN, false);
            Component environment = MekanismUtils.getTemperatureDisplay(tile.getLastEnvironmentLoss(), TemperatureUnit.KELVIN, false);
            return List.of(MekanismLang.TEMPERATURE.translate(temp), MekanismLang.TRANSFERRED_RATE.translate(transfer), MekanismLang.DISSIPATED_RATE.translate(environment));
        }));

        targetTempField = addRenderableWidget(new GuiTextField(this, 50, 51, 76, 12));
        targetTempField.setMaxLength(10);
        targetTempField.setInputValidator(InputValidator.DIGIT)
              .configureDigitalInput(this::setTargetTemperature);
        setInitialFocus(targetTempField);
    }

    private void setTargetTemperature() {
        if (!targetTempField.getText().isEmpty()) {
            try {
                double temp = Math.max(0, Double.parseDouble(targetTempField.getText()));
                PacketUtils.sendToServer(new PacketSetHeatTarget(tile.getBlockPos(), temp));
            } catch (NumberFormatException ignored) {
            }
            targetTempField.setText("");
        }
    }

    @Override
    protected void drawForegroundText(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderTitleText(guiGraphics);
        super.drawForegroundText(guiGraphics, mouseX, mouseY);
    }
}
