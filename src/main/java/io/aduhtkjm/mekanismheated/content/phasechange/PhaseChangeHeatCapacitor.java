package io.aduhtkjm.mekanismheated.content.phasechange;

import java.util.function.DoubleSupplier;
import mekanism.api.IContentsListener;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import org.jetbrains.annotations.Nullable;

/**
 * A heat capacitor that melts at a fixed temperature. Below the melting point it stores heat like a normal capacitor,
 * so its temperature rises with the heat it holds. Once it reaches the melting point, additional heat is stored as
 * latent heat: the temperature stays pinned at the melting point until {@code bufferCapacity} worth of heat has been
 * absorbed, after which the temperature resumes rising as normal. Losing heat runs the same process backwards, so the
 * latent heat is released before the temperature starts dropping again.
 * <p>
 * The latent heat is not tracked in a separate field: it is simply the part of the stored heat that exceeds
 * {@code meltingPoint * heatCapacity}. Only {@link #getTemperature()} has to know about the distinction, because the
 * stored heat itself is still the quantity that gets transferred in and out.
 */
public class PhaseChangeHeatCapacitor extends BasicHeatCapacitor {

    public static PhaseChangeHeatCapacitor create(double heatCapacity, double inverseConductionCoefficient, double inverseInsulationCoefficient,
          double meltingPoint, double bufferCapacity, @Nullable DoubleSupplier ambientTempSupplier, @Nullable IContentsListener listener) {
        //Mirror the validation BasicHeatCapacitor#create performs, as we cannot route through it.
        if (heatCapacity < 1) {
            throw new IllegalArgumentException("Heat capacity must be at least one");
        }
        if (inverseConductionCoefficient < 1) {
            throw new IllegalArgumentException("Inverse conduction coefficient must be at least one");
        }
        if (meltingPoint < 0) {
            throw new IllegalArgumentException("Melting point must not be negative");
        }
        if (bufferCapacity < 0) {
            throw new IllegalArgumentException("Buffer capacity must not be negative");
        }
        return new PhaseChangeHeatCapacitor(heatCapacity, inverseConductionCoefficient, inverseInsulationCoefficient, meltingPoint, bufferCapacity,
              ambientTempSupplier, listener);
    }

    private final double meltingPoint;
    private final double bufferCapacity;

    protected PhaseChangeHeatCapacitor(double heatCapacity, double inverseConductionCoefficient, double inverseInsulationCoefficient,
          double meltingPoint, double bufferCapacity, @Nullable DoubleSupplier ambientTempSupplier, @Nullable IContentsListener listener) {
        super(heatCapacity, inverseConductionCoefficient, inverseInsulationCoefficient, ambientTempSupplier, listener);
        this.meltingPoint = meltingPoint;
        this.bufferCapacity = bufferCapacity;
    }

    public double getMeltingPoint() {
        return meltingPoint;
    }

    /**
     * Amount of latent heat this block can absorb at its melting point before its temperature starts rising again.
     */
    public double getBufferCapacity() {
        return bufferCapacity;
    }

    /**
     * Heat that has to be stored for the block to reach its melting point.
     */
    private double getMeltingHeat() {
        return meltingPoint * getHeatCapacity();
    }

    /**
     * Latent heat currently held in the phase-change buffer, i.e. the part of the stored heat that is being absorbed or
     * released at the melting point. Never exceeds {@link #getBufferCapacity()}.
     */
    public double getBufferedHeat() {
        return Math.clamp(getHeat() - getMeltingHeat(), 0, bufferCapacity);
    }

    @Override
    public double getTemperature() {
        double heat = getHeat();
        double meltingHeat = getMeltingHeat();
        if (heat <= meltingHeat) {
            //Below the melting point we behave exactly like a normal heat capacitor.
            return heat / getHeatCapacity();
        }
        //Everything between the melting heat and the top of the buffer is latent heat and does not change the
        //temperature. Past the buffer the temperature rises again, as though the latent heat were not there.
        double latentHeat = Math.min(heat - meltingHeat, bufferCapacity);
        return (heat - latentHeat) / getHeatCapacity();
    }
}
