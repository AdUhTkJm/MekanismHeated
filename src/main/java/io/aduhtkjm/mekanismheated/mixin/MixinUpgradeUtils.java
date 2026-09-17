package io.aduhtkjm.mekanismheated.mixin;

import io.aduhtkjm.mekanismheated.content.upgrade.HeatedUpgrade;
import io.aduhtkjm.mekanismheated.registries.ModItems;
import mekanism.api.Upgrade;
import mekanism.common.util.UpgradeUtils;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@link UpgradeUtils#getItem(Upgrade)} is a switch over Mekanism's seven upgrade constants with no default, so a heat
 * upgrade would fall through to the compiler generated {@code MatchException}. This maps the heat upgrades to their
 * items instead.
 *
 * <p>This is the only place Mekanism hard-codes the set of upgrades besides the enum itself, so between it and
 * {@code MixinUpgrade} the heat upgrades are fully integrated: anything that asks for an upgrade's item (the upgrade
 * window's installed list, the uninstall output slot, the "supported" panel) gets the right stack.</p>
 */
@Mixin(value = UpgradeUtils.class, remap = false)
public abstract class MixinUpgradeUtils {

    @Inject(method = "getItem", at = @At("HEAD"), cancellable = true)
    private static void mekanismheated$getHeatUpgradeItem(Upgrade upgrade, CallbackInfoReturnable<Holder<Item>> cir) {
        if (upgrade == HeatedUpgrade.CONDUCTION) {
            cir.setReturnValue(ModItems.UPGRADE_CONDUCTION);
        } else if (upgrade == HeatedUpgrade.INSULATION) {
            cir.setReturnValue(ModItems.UPGRADE_INSULATION);
        } else if (upgrade == HeatedUpgrade.CAPACITY) {
            cir.setReturnValue(ModItems.UPGRADE_CAPACITY);
        }
    }
}
