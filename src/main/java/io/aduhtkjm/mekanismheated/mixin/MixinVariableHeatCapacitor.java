package io.aduhtkjm.mekanismheated.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.aduhtkjm.mekanismheated.content.upgrade.IHeatedHeatCapacitor;
import mekanism.common.capabilities.heat.VariableHeatCapacitor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@link VariableHeatCapacitor} reads its inverse coefficients from suppliers instead of the fields
 * {@link MixinBasicHeatCapacitor} scales, so both getters are scaled here as well. The heat capacity is inherited, and
 * is handled by the basic mixin.
 */
@Mixin(value = VariableHeatCapacitor.class, remap = false)
public abstract class MixinVariableHeatCapacitor {

    @ModifyReturnValue(method = "getInverseConduction", at = @At("RETURN"))
    private double mekanismheated$scaleInverseConduction(double original) {
        IHeatedHeatCapacitor self = (IHeatedHeatCapacitor) this;
        return original / self.mekanismheated$getConductionDivisor();
    }

    @ModifyReturnValue(method = "getInverseInsulation", at = @At("RETURN"))
    private double mekanismheated$scaleInverseInsulation(double original) {
        IHeatedHeatCapacitor self = (IHeatedHeatCapacitor) this;
        return original * self.mekanismheated$getInsulationMultiplier();
    }
}
