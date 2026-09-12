package io.aduhtkjm.mekanismheated.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.lwjgl.system.NonnullDefault;

/**
 * An ingot of thermoenergetic alloy, the iron-copper alloy this mod also has as a molten fluid. Like the other
 * alloy/dust items it draws the shared ingot texture tinted orange (see {@code ModClient}'s item colour handlers) and
 * shows its composition as a tooltip.
 */
@NonnullDefault
public class ItemThermoenergeticAlloyIngot extends Item {
    public static final int TINT = 0xFFE68E50;
    private static final Component TOOLTIP = Component.literal("Fe-Cu").withStyle(ChatFormatting.GOLD);

    public ItemThermoenergeticAlloyIngot(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(TOOLTIP);
    }
}
