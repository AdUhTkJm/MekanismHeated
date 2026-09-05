package io.aduhtkjm.mekanismheated.client.gui.element;

import io.aduhtkjm.mekanismheated.tank.MultiFluidChemicalTank;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import mekanism.api.chemical.ChemicalStack;
import mekanism.client.gui.GuiUtils.TilingDirection;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.MekanismRenderer.FluidTextureType;
import mekanism.common.MekanismLang;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.util.text.TextUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A single gauge window that renders the contents of a {@link MultiFluidChemicalTank} together, using the wide fluid tank
 * texture. Liquids form bands stacked from the bottom of the window upward (in tank order), while chemicals - which are
 * generally gases - form bands hanging from the top of the window downward. Both are sized proportionally to their share
 * of the shared pool's total capacity, so the empty space between them (if any) represents the pool's unused headspace.
 */
public class GuiStackedFluidChemicalGauge extends GuiGauge<Void> {

    private final Supplier<MultiFluidChemicalTank> tankSupplier;
    @Nullable
    private BooleanSupplier warningSupplier;

    public GuiStackedFluidChemicalGauge(Supplier<MultiFluidChemicalTank> tankSupplier, GaugeType type, IGuiWrapper gui, int x, int y) {
        super(type, gui, x, y);
        this.tankSupplier = tankSupplier;
    }

    @Override
    public GuiStackedFluidChemicalGauge warning(@NotNull WarningType type, @NotNull BooleanSupplier warningSupplier) {
        this.warningSupplier = warningSupplier;
        return (GuiStackedFluidChemicalGauge) super.warning(type, warningSupplier);
    }

    @Override
    public int getScaledLevel() {
        MultiFluidChemicalTank tank = tankSupplier.get();
        if (tank == null || tank.getTotalCapacity() <= 0) {
            return 0;
        }
        return (int) Math.round((double) tank.getTotalAmount() / tank.getTotalCapacity() * (height - 2));
    }

    @Nullable
    @Override
    public TextureAtlasSprite getIcon() {
        return null;
    }

    @Override
    public Component getLabel() {
        return null;
    }

    @Override
    public List<Component> getTooltipText() {
        List<Component> ret = new ArrayList<>();
        MultiFluidChemicalTank tank = tankSupplier.get();
        if (tank == null || tank.isEmpty()) {
            ret.add(MekanismLang.EMPTY.translate());
            return ret;
        }
        //Chemicals (rendered hanging from the top) are already listed top-down, while the fluid list is bottom-up,
        // so mirror the gauge: gases first, then liquids each from the top of their stack downwards.
        for (ChemicalStack chemical : tank.getChemicals()) {
            ret.add(MekanismLang.GENERIC_STORED_MB.translate(chemical, TextUtils.format(chemical.getAmount())));
        }
        List<FluidStack> fluids = tank.getFluids();
        for (int i = fluids.size() - 1; i >= 0; i--) {
            FluidStack fluid = fluids.get(i);
            ret.add(MekanismLang.GENERIC_STORED_MB.translate(fluid, TextUtils.format(fluid.getAmount())));
        }
        return ret;
    }

    @Nullable
    @Override
    public TransmissionType getTransmission() {
        return TransmissionType.FLUID;
    }

    @Override
    public void renderContents(GuiGraphics guiGraphics) {
        boolean warning = warningSupplier != null && warningSupplier.getAsBoolean();
        if (warning) {
            guiGraphics.blit(WARNING_BACKGROUND_TEXTURE, relativeX + 1, relativeY + 1, 0, 0, width - 2, height - 2, 256, 256);
        }
        MultiFluidChemicalTank tank = tankSupplier.get();
        if (tank != null && tank.getTotalCapacity() > 0) {
            int innerHeight = height - 2;
            int innerWidth = width - 2;
            int totalCapacity = tank.getTotalCapacity();

            //Liquids stack from the bottom of the window upward, each band sized by its share of the total capacity
            int yCursor = relativeY + 1 + innerHeight;
            for (FluidStack fluid : tank.getFluids()) {
                int bandHeight = (int) Math.round((double) fluid.getAmount() / totalCapacity * innerHeight);
                if (bandHeight <= 0) {
                    continue;
                }
                yCursor -= bandHeight;
                TextureAtlasSprite icon = MekanismRenderer.getFluidTexture(fluid, FluidTextureType.STILL);
                if (icon != null) {
                    MekanismRenderer.color(guiGraphics, fluid);
                    drawTiledSprite(guiGraphics, relativeX + 1, yCursor, bandHeight, innerWidth, bandHeight, icon, TilingDirection.UP_RIGHT);
                    MekanismRenderer.resetColor(guiGraphics);
                }
            }

            //Chemicals (generally gases) hang from the top of the window downward, in tank order
            int chemicalCursor = relativeY + 1;
            for (ChemicalStack chemical : tank.getChemicals()) {
                int bandHeight = (int) Math.round((double) chemical.getAmount() / totalCapacity * innerHeight);
                if (bandHeight <= 0) {
                    continue;
                }
                TextureAtlasSprite icon = MekanismRenderer.getChemicalTexture(chemical);
                if (icon != null) {
                    MekanismRenderer.color(guiGraphics, chemical);
                    drawTiledSprite(guiGraphics, relativeX + 1, chemicalCursor, bandHeight, innerWidth, bandHeight, icon, TilingDirection.UP_RIGHT);
                    MekanismRenderer.resetColor(guiGraphics);
                }
                chemicalCursor += bandHeight;
            }
        }
        drawBarOverlay(guiGraphics);
    }
}
