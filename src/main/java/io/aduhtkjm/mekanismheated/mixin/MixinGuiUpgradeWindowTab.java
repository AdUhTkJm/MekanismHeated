package io.aduhtkjm.mekanismheated.mixin;

import io.aduhtkjm.mekanismheated.client.gui.element.window.GuiHeatedUpgradeWindow;
import io.aduhtkjm.mekanismheated.content.upgrade.HeatedUpgrades;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.tab.window.GuiUpgradeWindowTab;
import mekanism.client.gui.element.window.GuiUpgradeWindow;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.tile.base.TileEntityMekanism;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Opens this mod's extended upgrade window ({@link GuiHeatedUpgradeWindow}) instead of Mekanism's plain one for the
 * machines that accept heat upgrades.
 */
@Mixin(value = GuiUpgradeWindowTab.class, remap = false)
public abstract class MixinGuiUpgradeWindowTab {

    @Redirect(method = "createWindow", at = @At(value = "NEW", target = "mekanism/client/gui/element/window/GuiUpgradeWindow"))
    private GuiUpgradeWindow mekanismheated$createUpgradeWindow(IGuiWrapper gui, int x, int y, TileEntityMekanism tile, SelectedWindowData windowData) {
        if (HeatedUpgrades.supports(tile)) {
            return new GuiHeatedUpgradeWindow(gui, x, y, tile, windowData);
        }
        return new GuiUpgradeWindow(gui, x, y, tile, windowData);
    }
}
