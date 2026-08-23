package io.aduhtkjm.mekanismheated.client.gui.element;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.client.gui.GuiUtils.TilingDirection;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.MekanismRenderer.FluidTextureType;
import mekanism.common.MekanismLang;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.util.text.TextUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * A fluid gauge that renders several tanks stacked inside a single gauge window, mirroring the physical layering of the
 * fractionation tower: the feed sump forms the bottom-most band and every output bank stacks above it. Band heights are
 * proportional to the tanks' capacities, so taller compartments also take more space in the gauge.
 */
public class GuiMixedFluidGauge extends GuiGauge<Void> {

    /**
     * Supplies the tanks to display, ordered bottom to top.
     */
    private final Supplier<List<IExtendedFluidTank>> layersSupplier;

    public GuiMixedFluidGauge(Supplier<List<IExtendedFluidTank>> layersSupplier, GaugeType type, IGuiWrapper gui, int x, int y) {
        super(type, gui, x, y);
        this.layersSupplier = layersSupplier;
    }

    private List<IExtendedFluidTank> getLayers() {
        List<IExtendedFluidTank> layers = layersSupplier.get();
        return layers == null ? List.of() : layers;
    }

    @Override
    public int getScaledLevel() {
        //Not used: we override renderContents to draw every layer ourselves
        return height - 2;
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
        boolean anyContent = false;
        //List from the top of the gauge downwards so the tooltip mirrors what is rendered
        List<IExtendedFluidTank> layers = getLayers();
        for (int i = layers.size() - 1; i >= 0; i--) {
            IExtendedFluidTank tank = layers.get(i);
            if (tank.getFluidAmount() > 0) {
                anyContent = true;
                ret.add(MekanismLang.GENERIC_STORED_MB.translate(tank.getFluid(), TextUtils.format(tank.getFluidAmount())));
            }
        }
        if (!anyContent) {
            ret.add(MekanismLang.EMPTY.translate());
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
        List<IExtendedFluidTank> layers = getLayers();
        long totalCapacity = 0;
        for (IExtendedFluidTank tank : layers) {
            totalCapacity += Math.max(0, tank.getCapacity());
        }
        if (totalCapacity > 0) {
            int innerHeight = height - 2;
            int innerWidth = width - 2;
            //Bands stack from the bottom of the gauge upwards, sized proportionally to their share of the total capacity.
            // Cumulative rounding keeps the total band height exactly equal to the inner height.
            int previousBandTop = relativeY + 1 + innerHeight;
            for (IExtendedFluidTank tank : layers) {
                int capacity = Math.max(0, tank.getCapacity());
                int bandHeight = (int) Math.floor((double) capacity * innerHeight / totalCapacity);
                if (bandHeight <= 0) {
                    continue;
                }
                int bandTop = previousBandTop - bandHeight;
                previousBandTop = bandTop;
                FluidStack fluid = tank.getFluid();
                if (!fluid.isEmpty() && tank.getCapacity() > 0) {
                    double fillFraction = Math.min(1, fluid.getAmount() / (double) tank.getCapacity());
                    int fillHeight = Math.max(1, (int) Math.round(fillFraction * bandHeight));
                    TextureAtlasSprite icon = MekanismRenderer.getFluidTexture(fluid, FluidTextureType.STILL);
                    if (icon != null) {
                        MekanismRenderer.color(guiGraphics, fluid);
                        drawTiledSprite(guiGraphics, relativeX + 1, bandTop, bandHeight, innerWidth, fillHeight, icon, TilingDirection.UP_RIGHT);
                        MekanismRenderer.resetColor(guiGraphics);
                    }
                }
            }
        }
        //Draw the gauge window overlay on top of everything
        drawBarOverlay(guiGraphics);
    }
}
