package io.aduhtkjm.mekanismheated.content.phasechange;

import io.aduhtkjm.mekanismheated.Config;
import java.util.function.DoubleSupplier;

/**
 * The three phase-change blocks. They behave identically except for the temperature at which they melt: below it they
 * are ordinary heat capacitors, at it they absorb heat as latent heat without warming up, and once their buffer is full
 * they warm up again.
 */
public enum PhaseChangeTier {

    LOW(() -> Config.PhaseChange.LOW_MELTING_POINT.get()),
    MEDIUM(() -> Config.PhaseChange.MEDIUM_MELTING_POINT.get()),
    HIGH(() -> Config.PhaseChange.HIGH_MELTING_POINT.get());

    private final DoubleSupplier meltingPoint;

    PhaseChangeTier(DoubleSupplier meltingPoint) {
        this.meltingPoint = meltingPoint;
    }

    /**
     * Temperature in Kelvin at which the block starts turning incoming heat into latent heat instead of warming up.
     */
    public double getMeltingPoint() {
        return meltingPoint.getAsDouble();
    }
}
