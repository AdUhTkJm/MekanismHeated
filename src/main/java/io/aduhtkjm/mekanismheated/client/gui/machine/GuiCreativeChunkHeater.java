package io.aduhtkjm.mekanismheated.client.gui.machine;

import io.aduhtkjm.mekanismheated.ModLang;
import io.aduhtkjm.mekanismheated.network.PacketSetChunkTargetTemperature;
import io.aduhtkjm.mekanismheated.tile.TileEntityCreativeChunkHeater;
import java.util.List;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.common.inventory.container.tile.EmptyTileContainer;
import mekanism.common.network.PacketUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import mekanism.common.util.text.InputValidator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

public class GuiCreativeChunkHeater extends GuiMekanismTile<TileEntityCreativeChunkHeater, EmptyTileContainer<TileEntityCreativeChunkHeater>> {

    private GuiTextField targetTempField;

    public GuiCreativeChunkHeater(EmptyTileContainer<TileEntityCreativeChunkHeater> container, Inventory inv, Component title) {
        super(container, inv, title);
        imageHeight = 91;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addRenderableWidget(new GuiInnerScreen(this, 48, 23, 80, 52, () -> List.of(
              ModLang.GUI_CHUNK_AMBIENT.translate(MekanismUtils.getTemperatureDisplay(tile.getChunkAmbientTemperature(), TemperatureUnit.KELVIN, true)),
              ModLang.GUI_TARGET_TEMPERATURE.translate(MekanismUtils.getTemperatureDisplay(tile.getTargetTemperature(), TemperatureUnit.KELVIN, true)),
              ModLang.GUI_CHUNK_DELTA.translate(MekanismUtils.getTemperatureDisplay(tile.getLastAppliedDelta(), TemperatureUnit.KELVIN, false))
        )).clearFormat());

        targetTempField = addRenderableWidget(new GuiTextField(this, 50, 63, 76, 12));
        targetTempField.setMaxLength(10);
        targetTempField.setInputValidator(InputValidator.DIGIT)
              .configureDigitalInput(this::setTargetTemperature);
        setInitialFocus(targetTempField);
    }

    private void setTargetTemperature() {
        if (!targetTempField.getText().isEmpty()) {
            try {
                double temp = Math.max(0, Double.parseDouble(targetTempField.getText()));
                PacketUtils.sendToServer(new PacketSetChunkTargetTemperature(tile.getBlockPos(), temp));
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
