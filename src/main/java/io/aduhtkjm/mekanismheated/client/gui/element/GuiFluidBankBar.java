package io.aduhtkjm.mekanismheated.client.gui.element;

import java.util.function.IntSupplier;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.bar.GuiBar;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.NotNull;

/**
 * Compact horizontal bar that renders a fluid-tinted fill level, used for the fractionation tower's output banks where a
 * full-sized fluid gauge would not fit.
 */
public class GuiFluidBankBar extends GuiBar<GuiBar.IBarInfoHandler> {

    private final IntSupplier fillColorSupplier;

    public GuiFluidBankBar(IGuiWrapper gui, IBarInfoHandler handler, IntSupplier fillColorSupplier, int x, int y, int width, int height) {
        super(GuiBar.BAR, gui, handler, x, y, width, height, true);
        this.fillColorSupplier = fillColorSupplier;
    }

    @Override
    protected void renderBarOverlay(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, double handlerLevel) {
        int fill = calculateScaled(handlerLevel, width - 2);
        if (fill > 0) {
            guiGraphics.fill(relativeX + 1, relativeY + 1, relativeX + 1 + fill, relativeY + height - 1, fillColorSupplier.getAsInt());
        }
    }
}
