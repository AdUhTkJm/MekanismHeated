package io.aduhtkjm.mekanismheated.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.aduhtkjm.mekanismheated.content.upgrade.HeatedUpgrades;
import io.aduhtkjm.mekanismheated.content.upgrade.IHeatedHeatCapacitor;
import java.util.function.DoubleSupplier;
import mekanism.api.IContentsListener;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the heat upgrades of this mod scale a machine's capacitor values.
 *
 * <p>The inverse coefficients of {@link BasicHeatCapacitor} are final and immutable in upstream code, so instead of
 * rewriting them this scales what the getters report. The heat capacity is a mutable field: it is set to
 * {@code base * multiplier}, where the base is the value the capacitor was constructed with, or a capacity its owner
 * explicitly passed in (for example a multiblock scaling by volume). Capacities restored from NBT are deliberately
 * ignored as a source for the base, so reloading a machine can never compound the multipliers.</p>
 *
 * <p>{@code VariableHeatCapacitor} overrides both coefficient getters with its own suppliers, so it gets its own mixin
 * ({@link MixinVariableHeatCapacitor}) that applies the same multipliers.</p>
 */
@Mixin(value = BasicHeatCapacitor.class, remap = false)
public abstract class MixinBasicHeatCapacitor implements IHeatedHeatCapacitor {

    @Unique
    private double mekanismheated$baseHeatCapacity = 1;
    @Unique
    private double mekanismheated$conductionDivisor = 1;
    @Unique
    private double mekanismheated$insulationMultiplier = 1;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mekanismheated$captureBaseCapacity(double heatCapacity, double inverseConductionCoefficient, double inverseInsulationCoefficient,
          DoubleSupplier ambientTempSupplier, IContentsListener listener, CallbackInfo ci) {
        mekanismheated$baseHeatCapacity = heatCapacity;
    }

    @Override
    public void mekanismheated$applyMultipliers(HeatedUpgrades.Multipliers multipliers) {
        mekanismheated$applyMultipliers(multipliers, mekanismheated$baseHeatCapacity);
    }

    @Override
    public void mekanismheated$applyMultipliers(HeatedUpgrades.Multipliers multipliers, double baseHeatCapacity) {
        mekanismheated$baseHeatCapacity = baseHeatCapacity;
        mekanismheated$conductionDivisor = multipliers.conductionDivisor();
        mekanismheated$insulationMultiplier = multipliers.insulationMultiplier();
        mekanismheated$setCapacity(baseHeatCapacity * multipliers.capacityMultiplier());
    }

    @Override
    public double mekanismheated$getConductionDivisor() {
        return mekanismheated$conductionDivisor;
    }

    @Override
    public double mekanismheated$getInsulationMultiplier() {
        return mekanismheated$insulationMultiplier;
    }

    @ModifyReturnValue(method = "getInverseConduction", at = @At("RETURN"))
    private double mekanismheated$scaleInverseConduction(double original) {
        return original / mekanismheated$conductionDivisor;
    }

    @ModifyReturnValue(method = "getInverseInsulation", at = @At("RETURN"))
    private double mekanismheated$scaleInverseInsulation(double original) {
        return original * mekanismheated$insulationMultiplier;
    }

    @Unique
    private void mekanismheated$setCapacity(double newCapacity) {
        BasicHeatCapacitor self = (BasicHeatCapacitor) (Object) this;
        double oldCapacity = self.getHeatCapacity();
        if (oldCapacity != newCapacity) {
            if (oldCapacity > 0) {
                self.setHeat(self.getHeat() * (newCapacity / oldCapacity));
            }
            self.setHeatCapacity(newCapacity, false);
        }
    }
}
