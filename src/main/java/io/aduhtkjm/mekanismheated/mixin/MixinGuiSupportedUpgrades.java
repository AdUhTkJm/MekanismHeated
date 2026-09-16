package io.aduhtkjm.mekanismheated.mixin;

import io.aduhtkjm.mekanismheated.content.upgrade.HeatedUpgrade;
import io.aduhtkjm.mekanismheated.content.upgrade.HeatedUpgrades;
import io.aduhtkjm.mekanismheated.registries.ModItems;
import io.aduhtkjm.mekanismheated.util.IGuiSupportedUpgradesHook;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiElementHolder;
import mekanism.client.gui.element.custom.GuiSupportedUpgrades;
import mekanism.common.lib.Color;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.util.EnumUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes the "Supported" line display our new upgrades.
 */
@Mixin(GuiSupportedUpgrades.class)
public abstract class MixinGuiSupportedUpgrades extends GuiElement implements IGuiSupportedUpgradesHook {
    @Final
    @Shadow
    private int firstRowRoom;

    @Final
    @Shadow
    private int firstRowStart;

    @Final
    @Shadow
    private static int ROW_ROOM;

    @Final
    @Shadow
    private static int ELEMENT_SIZE;

    /**
     * The machine that needs this supported-upgrades panel.
     */
    @Unique
    private TileEntityMekanism mekanismheated$machine;

    private MixinGuiSupportedUpgrades(IGuiWrapper gui, int x, int y, int width, int height) {
        super(gui, x, y, width, height);
    }

    // It is simply annoying to mixin a private method that returns a private inner class.
    // So let's duplicate a bit of the code.
    //
    // Moreover, since we cannot initialize an ordinary `record UpdatePos`, let's pack it inside a single int.
    @Unique
    private int mekanismHeated$getUpgradePos(int index) {
        int row = index < firstRowRoom ? 0 : 1 + (index - firstRowRoom) / ROW_ROOM;
        if (row == 0) {
            return (firstRowStart + (index % firstRowRoom) * ELEMENT_SIZE) << 16;
        }
        index -= firstRowRoom;
        return ((index % ROW_ROOM) * ELEMENT_SIZE) << 16 | (row * ELEMENT_SIZE);
    }

    /**
     * Replace the original one to add our own upgrades. <p>
     *
     * There might be conflicts, but we can pretend there isn't until we meet one.
     */
    @Inject(method = "drawBackground", at = @At("TAIL"))
    public void mekanismheated$drawBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        int backgroundColor = Color.argb(GuiElementHolder.getBackgroundColor()).alpha(0.5).argb();
        var heatedUpgrades = HeatedUpgrade.values();
        boolean invalid = !HeatedUpgrades.supports(mekanismheated$machine);
        for (int i = 0; i < heatedUpgrades.length; i++) {
            HeatedUpgrade upgrade = heatedUpgrades[i];
            int pos = mekanismHeated$getUpgradePos(i + EnumUtils.UPGRADES.length);
            int xPos = relativeX + 1 + (pos >> 16);
            int yPos = relativeY + 1 + (pos & 0xFFFF);
            gui().renderItem(guiGraphics, new ItemStack(ModItems.HEATED_UPGRADES.get(upgrade).get()), xPos, yPos, 0.75F);
            // Make the upgrade appear faded if it is not supported
            if (invalid) {
                guiGraphics.fill(RenderType.guiGhostRecipeOverlay(), xPos, yPos, xPos + ELEMENT_SIZE, yPos + ELEMENT_SIZE, backgroundColor);
            }
        }
    }

    @Override
    public void mekanismheated$setMachine(TileEntityMekanism machine) {
        this.mekanismheated$machine = machine;
    }

    @Override
    public TileEntityMekanism mekanismheated$getMachine() {
        return mekanismheated$machine;
    }
}
