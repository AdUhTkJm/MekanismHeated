package io.aduhtkjm.mekanismheated.client.gui.machine;

import io.aduhtkjm.mekanismheated.ModLang;
import io.aduhtkjm.mekanismheated.client.gui.element.GuiFluidBankBar;
import io.aduhtkjm.mekanismheated.content.fractionation.FractionationMultiblockData;
import io.aduhtkjm.mekanismheated.tile.multiblock.TileEntityThermalFractionationController;
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
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.tab.GuiHeatTab;
import mekanism.common.MekanismLang;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;

/**
 * Controller GUI of a Thermal Fractionation Tower: feed sump gauge on the left, status screen in the center, one compact
 * bar per output bank stacked in the right-hand column.
 */
public class GuiThermalFractionationController
      extends GuiMekanismTile<TileEntityThermalFractionationController, MekanismTileContainer<TileEntityThermalFractionationController>> {

    /**
     * Maximum number of output bank bars that fit in the right-hand column.
     */
    private static final int MAX_BANK_BARS = 8;
    private static final int BANK_BAR_X = 152;
    private static final int BANK_BAR_Y = 13;
    private static final int BANK_BAR_STEP = 8;

    private GuiElement inputGauge;

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
        addRenderableWidget(new GuiInnerScreen(this, 48, 19, 100, 40, () -> List.of(
              MekanismLang.MULTIBLOCK_FORMED.translate(),
              ModLang.GUI_FRACTIONATION_HEIGHT.translate(tile.getMultiblock().height()),
              ModLang.GUI_FRACTIONATION_LAYERS.translate(tile.getMultiblock().getBankCount()),
              MekanismLang.TEMPERATURE.translate(MekanismUtils.getTemperatureDisplay(tile.getMultiblock().getTemperature(), TemperatureUnit.KELVIN, true))
        )).padding(3).clearSpacing());
        addRenderableWidget(new GuiDownArrow(this, 32, 39));
        inputGauge = addRenderableWidget(new GuiFluidGauge(() -> tile.getMultiblock().inputTank,
              () -> tile.getMultiblock().getFluidTanks(null), GaugeType.STANDARD, this, 6, 13));
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
        for (int i = 0; i < Math.min(tile.getMultiblock().getBankCount(), MAX_BANK_BARS); i++) {
            int bankIndex = i;
            // Note Java does not allow capturing non-effectively-final variables.
            addRenderableWidget(new GuiFluidBankBar(this, new BankBarHandler(i), () -> getBankFillColor(bankIndex),
                  BANK_BAR_X, BANK_BAR_Y + i * BANK_BAR_STEP, 38, 6));
        }
        addRenderableWidget(new GuiHeatTab(this, () -> {
            Component environment = MekanismUtils.getTemperatureDisplay(tile.getMultiblock().lastEnvironmentLoss, TemperatureUnit.KELVIN, false);
            return Collections.singletonList(MekanismLang.DISSIPATED_RATE.translate(environment));
        }));
    }

    private IExtendedFluidTank getBank(int index) {
        FractionationMultiblockData multiblock = tile.getMultiblock();
        List<IExtendedFluidTank> banks = multiblock.getOutputBanks();
        return index < banks.size() ? banks.get(index) : null;
    }

    /**
     * @return an opaque display color for the given bank's fluid, falling back to a neutral tone.
     */
    private int getBankFillColor(int index) {
        IExtendedFluidTank bank = getBank(index);
        FluidStack stack = bank == null ? FluidStack.EMPTY : bank.getFluid();
        if (stack.isEmpty()) {
            return 0xFF565656;
        }
        int tint = IClientFluidTypeExtensions.of(stack.getFluid()).getTintColor(stack);
        if (tint == -1 || (tint & 0xFF000000) == 0) {
            //No usable tint (or fully transparent): render a neutral steel tone
            return 0xFF8A9BB0;
        }
        return tint | 0xFF000000;
    }

    private class BankBarHandler implements IBarInfoHandler {

        private final int index;

        private BankBarHandler(int index) {
            this.index = index;
        }

        @Override
        public Component getTooltip() {
            IExtendedFluidTank bank = getBank(index);
            if (bank == null || bank.getFluidAmount() <= 0) {
                return MekanismLang.NO_FLUID.translate();
            }
            FluidStack stack = bank.getFluid();
            return stack.getHoverName().copy().append(": " + stack.getAmount() + " / " + bank.getCapacity() + " mB");
        }

        @Override
        public double getLevel() {
            IExtendedFluidTank bank = getBank(index);
            if (bank == null || bank.getCapacity() == 0) {
                return 0;
            }
            return (double) bank.getFluidAmount() / bank.getCapacity();
        }
    }

    @Override
    protected void drawForegroundText(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderTitleTextWithOffset(guiGraphics, inputGauge.getRelativeRight(), BANK_BAR_X);
        renderInventoryText(guiGraphics);
        super.drawForegroundText(guiGraphics, mouseX, mouseY);
    }
}
