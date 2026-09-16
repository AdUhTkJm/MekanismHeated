package io.aduhtkjm.mekanismheated.item;

import io.aduhtkjm.mekanismheated.content.upgrade.HeatedUpgrade;
import io.aduhtkjm.mekanismheated.content.upgrade.HeatedUpgrades;
import io.aduhtkjm.mekanismheated.content.upgrade.IHeatedUpgradeComponent;
import java.util.List;
import mekanism.api.text.APILang;
import mekanism.api.text.EnumColor;
import mekanism.client.key.MekKeyHandler;
import mekanism.client.key.MekanismKeyHandler;
import mekanism.common.MekanismLang;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.util.WorldUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

/**
 * An upgrade that scales a machine's heat capacitor, installed by placing it in a machine's upgrade slot (exactly like
 * Mekanism's own upgrades) or by shift right-clicking the machine with it, and removed from the upgrade window.
 *
 * @see HeatedUpgrade
 */
public class ItemHeatedUpgrade extends Item {

    private final HeatedUpgrade type;

    public ItemHeatedUpgrade(HeatedUpgrade type, Properties properties) {
        super(properties.rarity(Rarity.UNCOMMON));
        this.type = type;
    }

    public HeatedUpgrade getHeatedUpgradeType() {
        return type;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull Item.TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        if (MekKeyHandler.isKeyPressed(MekanismKeyHandler.detailsKey)) {
            tooltip.add(type.getDescription());
            tooltip.add(APILang.UPGRADE_MAX_INSTALLED.translate(type.getMax()));
        } else {
            tooltip.add(MekanismLang.HOLD_FOR_DETAILS.translateColored(EnumColor.GRAY, EnumColor.INDIGO, MekanismKeyHandler.detailsKey.getTranslatedKeyMessage()));
        }
    }

    @NotNull
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            Level level = context.getLevel();
            BlockEntity tile = WorldUtils.getTileEntity(level, context.getClickedPos());
            if (tile instanceof TileEntityMekanism mekTile && HeatedUpgrades.supports(mekTile)
                  && mekTile.getComponent() instanceof IHeatedUpgradeComponent component) {
                if (!level.isClientSide) {
                    ItemStack stack = context.getItemInHand();
                    int added = component.mekanismheated$addHeatedUpgrades(type, stack.getCount());
                    if (added > 0) {
                        stack.shrink(added);
                    }
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return InteractionResult.PASS;
    }
}
