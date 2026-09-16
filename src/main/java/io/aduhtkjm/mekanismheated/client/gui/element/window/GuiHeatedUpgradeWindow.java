package io.aduhtkjm.mekanismheated.client.gui.element.window;

import io.aduhtkjm.mekanismheated.ModLang;
import io.aduhtkjm.mekanismheated.content.upgrade.HeatedUpgrade;
import io.aduhtkjm.mekanismheated.content.upgrade.HeatedUpgrades;
import io.aduhtkjm.mekanismheated.content.upgrade.IHeatedUpgradeComponent;
import io.aduhtkjm.mekanismheated.network.PacketRemoveHeatedUpgrade;
import java.util.ArrayList;
import java.util.List;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElementHolder;
import mekanism.client.gui.element.button.DigitalButton;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.window.GuiUpgradeWindow;
import mekanism.client.render.IFancyFontRenderer.TextAlignment;
import mekanism.common.MekanismLang;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.network.PacketUtils;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

/**
 * Mekanism's upgrade window with an extra section listing this mod's heat upgrades.
 *
 * <p>Mekanism's upgrade window is hard-typed to its own {@code Upgrade} enum, so the three heat upgrades cannot be added
 * to its scroll list. Instead this appends a fixed section below the vanilla one: one row per heat upgrade showing its
 * item, name and installed count, plus a button uninstalling it (or all of them, when shift is held). Installing them
 * needs no UI of its own — they go into the same upgrade slot, and the vanilla installing progress bar covers them.</p>
 */
public class GuiHeatedUpgradeWindow extends GuiUpgradeWindow {

    private static final int HEADER_HEIGHT = 12;
    private static final int ROW_HEIGHT = 14;
    private static final int ROW_COUNT = HeatedUpgrade.values().length;
    /**
     * Height this section adds to the window.
     */
    private static final int SECTION_HEIGHT = HEADER_HEIGHT + ROW_COUNT * ROW_HEIGHT + 2;

    /** Row layout, relative to the left edge of the section and to its top. */
    private static final int SECTION_X = 6;
    private static final int SECTION_WIDTH = 186;
    private static final int ICON_X = 3;
    private static final int NAME_X = 18;
    private static final int NAME_WIDTH = 74;
    private static final int AMOUNT_X = 94;
    private static final int AMOUNT_WIDTH = 34;
    private static final int UNINSTALL_X = 130;
    private static final int UNINSTALL_WIDTH = 56;
    private static final int UNINSTALL_HEIGHT = 12;

    private final TileEntityMekanism tile;
    /**
     * Offset of the heat upgrade section from the window's top, in the same coordinates the window lays its children out
     * in.
     */
    private final int sectionTop;
    private final List<MekanismButton> uninstallButtons = new ArrayList<>();
    private final List<ItemStack> icons = new ArrayList<>();

    public GuiHeatedUpgradeWindow(IGuiWrapper gui, int x, int y, TileEntityMekanism tile, SelectedWindowData windowData) {
        super(gui, x, y, tile, windowData);
        this.tile = tile;
        //The vanilla window sized itself for its own content only, so grow it to fit the section appended below it
        this.sectionTop = height;
        this.height += SECTION_HEIGHT;
        for (int i = 0; i < ROW_COUNT; i++) {
            HeatedUpgrade upgrade = HeatedUpgrade.values()[i];
            icons.add(HeatedUpgrades.getStack(upgrade, 1));
            DigitalButton button = addChild(new DigitalButton(gui, relativeX + SECTION_X + UNINSTALL_X, relativeY + rowY(i), UNINSTALL_WIDTH, UNINSTALL_HEIGHT,
                  MekanismLang.UPGRADE_UNINSTALL, (element, mouseX, mouseY) ->
                  //Shift removes every installed upgrade of that type, matching Mekanism's own uninstall button
                  PacketUtils.sendToServer(new PacketRemoveHeatedUpgrade(tile.getBlockPos(), upgrade.ordinal(), Screen.hasShiftDown()))));
            button.setTooltip(MekanismLang.UPGRADE_UNINSTALL_TOOLTIP);
            uninstallButtons.add(button);
        }
        updateEnabledButtons();
    }

    private int rowY(int index) {
        return sectionTop + HEADER_HEIGHT + index * ROW_HEIGHT;
    }

    @Override
    public void tick() {
        super.tick();
        updateEnabledButtons();
    }

    private void updateEnabledButtons() {
        for (int i = 0; i < uninstallButtons.size(); i++) {
            uninstallButtons.get(i).active = getInstalled(HeatedUpgrade.values()[i]) > 0;
        }
    }

    private int getInstalled(HeatedUpgrade upgrade) {
        return tile.getComponent() instanceof IHeatedUpgradeComponent component ? component.mekanismheated$getHeatedUpgrades(upgrade) : 0;
    }

    @Override
    public void renderForeground(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderForeground(guiGraphics, mouseX, mouseY);
        drawScrollingString(guiGraphics, ModLang.GUI_HEAT_UPGRADES.translate(), relativeX + SECTION_X, relativeY + sectionTop + 3, TextAlignment.LEFT, titleTextColor(), 0,
              false);
        for (int i = 0; i < ROW_COUNT; i++) {
            HeatedUpgrade upgrade = HeatedUpgrade.values()[i];
            int rowY = relativeY + rowY(i);
            GuiUtils.renderBackgroundTexture(guiGraphics, GuiElementHolder.HOLDER, GuiElementHolder.HOLDER_SIZE, GuiElementHolder.HOLDER_SIZE,
                  relativeX + SECTION_X, rowY, SECTION_WIDTH, ROW_HEIGHT, 256, 256);
            gui().renderItem(guiGraphics, icons.get(i), relativeX + SECTION_X + ICON_X, rowY + 1, 0.75F);
            drawScaledScrollingString(guiGraphics, MekanismLang.UPGRADE_TYPE.translate(upgrade.getTranslatedName()), relativeX + SECTION_X + NAME_X, rowY + 3,
                  TextAlignment.LEFT, upgrade.getColor().getPackedColor(), NAME_WIDTH, 0, false, 0.7F);
            drawScaledScrollingString(guiGraphics, ModLang.GUI_UPGRADE_AMOUNT.translate(getInstalled(upgrade), upgrade.getMax()),
                  relativeX + SECTION_X + AMOUNT_X, rowY + 3, TextAlignment.CENTER, screenTextColor(), AMOUNT_WIDTH, 0, false, 0.7F);
        }
    }
}
