package io.aduhtkjm.mekanismheated.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.lwjgl.system.NonnullDefault;

import java.util.List;

@NonnullDefault
public class Fe2O3DustItem extends Item {
    public static final int TINT = 0xFF972020;
    private static final Component TOOLTIP = Component.literal("Fe₂O₃").withStyle(ChatFormatting.GOLD);

    public Fe2O3DustItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(TOOLTIP);
    }
}
