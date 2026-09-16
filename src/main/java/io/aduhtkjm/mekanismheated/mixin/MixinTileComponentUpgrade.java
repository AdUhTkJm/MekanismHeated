package io.aduhtkjm.mekanismheated.mixin;

import io.aduhtkjm.mekanismheated.content.upgrade.HeatedUpgrade;
import io.aduhtkjm.mekanismheated.content.upgrade.HeatedUpgradeAware;
import io.aduhtkjm.mekanismheated.content.upgrade.HeatedUpgrades;
import io.aduhtkjm.mekanismheated.content.upgrade.IHeatedUpgradeComponent;
import io.aduhtkjm.mekanismheated.content.upgrade.IHeatedUpgradeSlot;
import io.aduhtkjm.mekanismheated.content.upgrade.IHeatedUpgradeTile;
import io.aduhtkjm.mekanismheated.registries.ModDataComponents;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.common.inventory.container.sync.ISyncableData;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.slot.UpgradeInventorySlot;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.util.MekanismUtils;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Teaches Mekanism's upgrade component about this mod's heat upgrades, so they live in the same upgrade slot, take the
 * same install time, are saved the same way and can be seen and removed from the same (extended) upgrade window.
 *
 * <p>{@link HeatedUpgrade} cannot be added to Mekanism's closed {@code Upgrade} enum, so the counts are kept in a second
 * map next to Mekanism's own one and the install, removal, NBT and sync paths are mirrored one for one. The component of
 * a machine that does not implement {@link IHeatedUpgradeTile} is left completely untouched, so Mekanism's own machines
 * behave exactly as before.</p>
 */
@Mixin(value = TileComponentUpgrade.class, remap = false)
public abstract class MixinTileComponentUpgrade implements IHeatedUpgradeComponent {

    @Shadow
    @Final
    private TileEntityMekanism tile;
    @Shadow
    @Final
    private UpgradeInventorySlot upgradeSlot;
    @Shadow
    @Final
    private UpgradeInventorySlot upgradeOutputSlot;
    @Shadow
    private int upgradeTicks;
    @Shadow
    private boolean canCheckUpgrades;

    @Unique
    private Map<HeatedUpgrade, Integer> mekanismheated$heatedUpgrades = new EnumMap<>(HeatedUpgrade.class);

    /**
     * Widens the upgrade input and output slots of machines that accept heat upgrades, so the items used to install them
     * can be placed in the input slot and uninstalled upgrades can be moved to the output slot. Mekanism's slots only
     * know its own upgrades, so those keep working through the widened predicates while ours are added alongside them.
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void mekanismheated$allowHeatedUpgradeItems(TileEntityMekanism tileEntity, CallbackInfo ci) {
        //Note: this.tile is assigned before the upgrade slots are created, so reading the shadowed field is fine here
        if (HeatedUpgrades.supports(tile)) {
            //The input slot is where our items are installed from, the output slot is where uninstalled ones are put
            ((IHeatedUpgradeSlot) upgradeSlot).mekanismheated$allowHeatedUpgradeInstall();
            ((IHeatedUpgradeSlot) upgradeOutputSlot).mekanismheated$allowHeatedUpgradeItems();
        }
    }

    /**
     * Installs the heat upgrade sitting in the upgrade slot, taking the same time Mekanism takes for its own upgrades
     * (and reusing its install progress field, so the window's installing progress bar works for ours as well).
     */
    @Inject(method = "tickServer", at = @At("HEAD"), cancellable = true)
    private void mekanismheated$tickHeatedUpgrade(CallbackInfo ci) {
        HeatedUpgrade type = HeatedUpgrades.getType(upgradeSlot.getStack());
        if (type == null) {
            //Not one of our upgrades: let Mekanism's own logic handle it
            return;
        }
        //Our item must never reach Mekanism's logic, which expects Upgrade values
        ci.cancel();
        if (!HeatedUpgrades.supports(tile) || !canCheckUpgrades) {
            return;
        }
        int installed = mekanismheated$heatedUpgrades.getOrDefault(type, 0);
        if (installed < type.getMax()) {
            if (upgradeTicks < SharedConstants.TICKS_PER_SECOND) {
                upgradeTicks++;
                return;
            }
            if (upgradeTicks == SharedConstants.TICKS_PER_SECOND) {
                int added = mekanismheated$addHeatedUpgrades(type, upgradeSlot.getCount());
                if (added > 0) {
                    MekanismUtils.logMismatchedStackSize(upgradeSlot.shrinkStack(added, Action.EXECUTE), added);
                }
            }
        }
        upgradeTicks = 0;
        canCheckUpgrades = false;
    }

    @Override
    public Map<HeatedUpgrade, Integer> mekanismheated$getHeatedUpgrades() {
        return mekanismheated$heatedUpgrades;
    }

    @Override
    public int mekanismheated$getHeatedUpgrades(HeatedUpgrade upgrade) {
        return mekanismheated$heatedUpgrades.getOrDefault(upgrade, 0);
    }

    @Override
    public int mekanismheated$addHeatedUpgrades(HeatedUpgrade upgrade, int maxAvailable) {
        int installed = mekanismheated$heatedUpgrades.getOrDefault(upgrade, 0);
        int toAdd = Math.min(upgrade.getMax() - installed, maxAvailable);
        if (toAdd > 0) {
            mekanismheated$heatedUpgrades.put(upgrade, installed + toAdd);
            ((IHeatedUpgradeTile) tile).mekanismheated$recalculateHeatedUpgrades();
            tile.markForSave();
            return toAdd;
        }
        return 0;
    }

    @Override
    public void mekanismheated$removeHeatedUpgrades(HeatedUpgrade upgrade, boolean removeAll) {
        int installed = mekanismheated$heatedUpgrades.getOrDefault(upgrade, 0);
        if (installed > 0) {
            int toRemove = removeAll ? installed : 1;
            ItemStack simulatedRemainder = upgradeOutputSlot.insertItem(HeatedUpgrades.getStack(upgrade, toRemove), Action.SIMULATE, AutomationType.INTERNAL);
            if (simulatedRemainder.getCount() < toRemove) {
                //We can fit at least one in the output slot
                toRemove -= simulatedRemainder.getCount();
                if (installed == toRemove) {
                    mekanismheated$heatedUpgrades.remove(upgrade);
                } else {
                    mekanismheated$heatedUpgrades.put(upgrade, installed - toRemove);
                }
                ((IHeatedUpgradeTile) tile).mekanismheated$recalculateHeatedUpgrades();
                upgradeOutputSlot.insertItem(HeatedUpgrades.getStack(upgrade, toRemove), Action.EXECUTE, AutomationType.INTERNAL);
                tile.markForSave();
                //If we have some upgrades in the input slot, mark that we check if they can be transferred
                canCheckUpgrades = !upgradeSlot.isEmpty();
            }
        }
    }

    /**
     * Adds the installed heat upgrade counts to the data synced to an open upgrade window, so the window can show them.
     */
    @Inject(method = "getSpecificSyncableData", at = @At("RETURN"))
    private void mekanismheated$syncHeatedUpgrades(CallbackInfoReturnable<List<ISyncableData>> cir) {
        if (HeatedUpgrades.supports(tile)) {
            //Note: Like Mekanism does for its own upgrades, the order has to be identical on both sides, so we always
            // add our upgrades in enum order after Mekanism's own
            List<ISyncableData> data = cir.getReturnValue();
            for (HeatedUpgrade upgrade : HeatedUpgrade.values()) {
                data.add(SyncableInt.create(() -> mekanismheated$heatedUpgrades.getOrDefault(upgrade, 0), value -> {
                    if (value <= 0) {
                        mekanismheated$heatedUpgrades.remove(upgrade);
                    } else {
                        mekanismheated$heatedUpgrades.put(upgrade, Math.min(value, upgrade.getMax()));
                    }
                }));
            }
        }
    }

    @Inject(method = "serialize", at = @At("RETURN"))
    private void mekanismheated$saveHeatedUpgrades(HolderLookup.Provider provider, CallbackInfoReturnable<CompoundTag> cir) {
        if (!mekanismheated$heatedUpgrades.isEmpty()) {
            cir.getReturnValue().put(HeatedUpgrades.NBT_KEY, HeatedUpgrades.write(mekanismheated$heatedUpgrades));
        }
    }

    @Inject(method = "deserialize", at = @At("TAIL"))
    private void mekanismheated$loadHeatedUpgrades(CompoundTag upgradeNBT, HolderLookup.Provider provider, CallbackInfo ci) {
        mekanismheated$heatedUpgrades.clear();
        mekanismheated$heatedUpgrades.putAll(HeatedUpgrades.read(upgradeNBT));
        if (HeatedUpgrades.supports(tile)) {
            ((IHeatedUpgradeTile) tile).mekanismheated$recalculateHeatedUpgrades();
        }
    }

    /**
     * Stores the installed heat upgrades on the machine's item when it is picked up, mirroring the attachment Mekanism
     * uses for its own upgrades.
     */
    @Inject(method = "collectImplicitComponents", at = @At("TAIL"))
    private void mekanismheated$collectHeatedUpgrades(DataComponentMap.Builder builder, CallbackInfo ci) {
        if (!mekanismheated$heatedUpgrades.isEmpty()) {
            builder.set(ModDataComponents.HEATED_UPGRADES.get(), new HeatedUpgradeAware(new EnumMap<>(mekanismheated$heatedUpgrades)));
        }
    }

    @Inject(method = "applyImplicitComponents", at = @At("TAIL"))
    private void mekanismheated$applyHeatedUpgrades(BlockEntity.DataComponentInput input, CallbackInfo ci) {
        HeatedUpgradeAware upgrades = input.get(ModDataComponents.HEATED_UPGRADES.get());
        if (upgrades != null) {
            mekanismheated$heatedUpgrades.clear();
            mekanismheated$heatedUpgrades.putAll(upgrades.upgrades());
            if (HeatedUpgrades.supports(tile)) {
                ((IHeatedUpgradeTile) tile).mekanismheated$recalculateHeatedUpgrades();
            }
        }
    }
}
