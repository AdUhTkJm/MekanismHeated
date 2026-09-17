package io.aduhtkjm.mekanismheated.mixin;

import io.aduhtkjm.mekanismheated.content.upgrade.HeatedUpgrade;
import io.aduhtkjm.mekanismheated.content.upgrade.HeatedUpgrades;
import mekanism.api.Upgrade;
import mekanism.common.tile.base.TileEntityMekanism;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks the heat upgrades into Mekanism's upgrade recalculation.
 *
 * <p>Mekanism calls {@code recalculateUpgrades(upgrade)} on a machine every time an upgrade is installed, removed,
 * loaded from NBT, restored from an item or re-applied after the machine type changes. That is exactly the moment a
 * machine's heat capacitor values have to be recomputed, so no component, slot or packet mixins are needed for the
 * install/uninstall paths: Mekanism's own {@code TileComponentUpgrade} already drives them.</p>
 *
 * <p>Only heat upgrades are handled, and no Mekanism machine supports them, so this is a no-op everywhere else.</p>
 */
@Mixin(value = TileEntityMekanism.class, remap = false)
public abstract class MixinTileEntityMekanism {

    @Inject(method = "recalculateUpgrades", at = @At("TAIL"))
    private void mekanismheated$recalculateHeatUpgrades(Upgrade upgrade, CallbackInfo ci) {
        if (HeatedUpgrade.isHeatUpgrade(upgrade)) {
            HeatedUpgrades.reapply((TileEntityMekanism) (Object) this);
        }
    }
}
