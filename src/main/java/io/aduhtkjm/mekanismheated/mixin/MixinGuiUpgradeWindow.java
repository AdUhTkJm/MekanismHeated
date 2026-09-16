package io.aduhtkjm.mekanismheated.mixin;

import io.aduhtkjm.mekanismheated.util.IGuiSupportedUpgradesHook;
import mekanism.api.Upgrade;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.custom.GuiSupportedUpgrades;
import mekanism.client.gui.element.window.GuiUpgradeWindow;
import mekanism.common.tile.base.TileEntityMekanism;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;

/**
 * See {@link MixinGuiSupportedUpgrades}. We need to initialize the "machine" field there.
 */
@Mixin(value = GuiUpgradeWindow.class, remap = false)
public abstract class MixinGuiUpgradeWindow {

    @Final
    @Shadow
    private TileEntityMekanism tile;

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "mekanism/client/gui/element/custom/GuiSupportedUpgrades"))
    private GuiSupportedUpgrades mekanismheated$createUpgradeWindow(IGuiWrapper gui, int x, int y, Set<Upgrade> supportedUpgrades) {
        var panel = new GuiSupportedUpgrades(gui, x, y, supportedUpgrades);
        ((IGuiSupportedUpgradesHook) panel).mekanismheated$setMachine(tile);
        return panel;
    }
}
