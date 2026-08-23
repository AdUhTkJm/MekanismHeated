package io.aduhtkjm.mekanismheated.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.lwjgl.system.NonnullDefault;

import java.util.List;

@NonnullDefault
public class FeS2DustItem extends Item {
    public static final int TINT = 0xFFE9EE67;
    private static final Component TOOLTIP = Component.literal("FeS₂").withStyle(ChatFormatting.GOLD);

    public FeS2DustItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(TOOLTIP);
    }
}
