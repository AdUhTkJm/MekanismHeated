package io.aduhtkjm.mekanismheated.client.gui.element;

import io.aduhtkjm.mekanismheated.tank.MultiFluidTank;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
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
 * A fluid gauge that renders multiple fluids stacked together in a single gauge window.
 * Unlike {@link GuiMixedFluidGauge} which sizes bands proportionally to each tank's capacity,
 * this gauge sizes each fluid's band proportionally to its actual amount relative to the total
 * capacity, so the combined fill level represents the total usage.
 */
public class GuiStackedFluidGauge extends GuiGauge<Void> {

    private final Supplier<MultiFluidTank> tankSupplier;
    @Nullable
    private BooleanSupplier warningSupplier;

    public GuiStackedFluidGauge(Supplier<MultiFluidTank> tankSupplier, GaugeType type, IGuiWrapper gui, int x, int y) {
        super(type, gui, x, y);
        this.tankSupplier = tankSupplier;
    }

    @Override
    public GuiStackedFluidGauge warning(@NotNull WarningType type, @NotNull BooleanSupplier warningSupplier) {
        this.warningSupplier = warningSupplier;
        return (GuiStackedFluidGauge) super.warning(type, warningSupplier);
    }

    @Override
    public int getScaledLevel() {
        MultiFluidTank tank = tankSupplier.get();
        if (tank == null || tank.getTotalCapacity() <= 0) {
            return 0;
        }
        //Clamp so a tank transiently reporting more than its capacity can never yield a fill taller than the window
        return Math.min(height - 2, (int) Math.round((double) tank.getTotalAmount() / tank.getTotalCapacity() * (height - 2)));
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
        MultiFluidTank tank = tankSupplier.get();
        if (tank == null || tank.isEmpty()) {
            ret.add(MekanismLang.EMPTY.translate());
            return ret;
        }
        List<FluidStack> fluids = tank.getFluids();
        //List from top of gauge downwards (reverse of rendering order)
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
        MultiFluidTank tank = tankSupplier.get();
        if (tank != null && tank.getTotalCapacity() > 0) {
            int innerHeight = height - 2;
            int innerWidth = width - 2;
            int totalCapacity = tank.getTotalCapacity();

            //Render each fluid as a band stacked from the bottom, sized by its share of total capacity. The band edges
            // are derived from the running total instead of summing per-band heights, so per-band rounding cannot
            // accumulate: the top edge of the stack is always rounded from the full stack height, which keeps the
            // drawing inside the window even when many fluids are present.
            int bandBottom = relativeY + 1 + innerHeight;
            long cumulative = 0;
            for (FluidStack fluid : tank.getFluids()) {
                cumulative += fluid.getAmount();
                //Clamp to the window as a safety net: if the tank ever reports more than its capacity (for example while
                // a synced capacity is still catching up), the excess must be clipped rather than drawn above the gauge.
                int bandTop = relativeY + 1 + innerHeight - (int) Math.round((double) cumulative / totalCapacity * innerHeight);
                bandTop = Math.max(relativeY + 1, Math.min(bandTop, bandBottom));
                int bandHeight = bandBottom - bandTop;
                bandBottom = bandTop;
                if (bandHeight <= 0) {
                    continue;
                }
                TextureAtlasSprite icon = MekanismRenderer.getFluidTexture(fluid, FluidTextureType.STILL);
                if (icon != null) {
                    MekanismRenderer.color(guiGraphics, fluid);
                    drawTiledSprite(guiGraphics, relativeX + 1, bandTop, bandHeight, innerWidth, bandHeight, icon, TilingDirection.UP_RIGHT);
                    MekanismRenderer.resetColor(guiGraphics);
                }
            }
        }
        drawBarOverlay(guiGraphics);
    }
}
