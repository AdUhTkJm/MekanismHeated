package io.aduhtkjm.mekanismheated.mixin;

import io.aduhtkjm.mekanismheated.content.upgrade.HeatedUpgrades;
import io.aduhtkjm.mekanismheated.content.upgrade.IHeatedUpgradeSlot;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import mekanism.api.AutomationType;
import mekanism.common.inventory.slot.BasicInventorySlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Wraps a slot's item validator and insert predicate, so that the upgrade slots of a machine that accepts this mod's
 * heat upgrades also accept the upgrade items used to install them (the input slot) and to hand them back on uninstall
 * (the output slot). Mekanism's {@code UpgradeInventorySlot} keeps both predicates in final fields that it only ever
 * builds from a set of {@link mekanism.api.Upgrade}s, so the only way to widen them is to replace the fields, which
 * {@code @Mutable} makes possible even though they are final upstream.
 */
@Mixin(value = BasicInventorySlot.class, remap = false)
public abstract class MixinBasicInventorySlot implements IHeatedUpgradeSlot {

    @Final
    @Shadow
    @Mutable
    private BiPredicate<ItemStack, AutomationType> canInsert;
    @Final
    @Shadow
    @Mutable
    private Predicate<ItemStack> validator;

    @Override
    public void mekanismheated$allowHeatedUpgradeInstall() {
        Predicate<ItemStack> originalValidator = validator;
        BiPredicate<ItemStack, AutomationType> originalCanInsert = canInsert;
        validator = stack -> originalValidator.test(stack) || HeatedUpgrades.isHeatedUpgrade(stack);
        canInsert = (stack, automationType) -> HeatedUpgrades.isHeatedUpgrade(stack) || originalCanInsert.test(stack, automationType);
    }

    @Override
    public void mekanismheated$allowHeatedUpgradeItems() {
        Predicate<ItemStack> originalValidator = validator;
        validator = stack -> originalValidator.test(stack) || HeatedUpgrades.isHeatedUpgrade(stack);
    }
}
