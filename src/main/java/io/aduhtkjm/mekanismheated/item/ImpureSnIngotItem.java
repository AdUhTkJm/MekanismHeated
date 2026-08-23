package io.aduhtkjm.mekanismheated.item;

import io.aduhtkjm.mekanismheated.ModLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.lwjgl.system.NonnullDefault;

import java.util.List;

@NonnullDefault
public class ImpureSnIngotItem extends Item {
    public static final int TINT = 0xFFCBDDFC;
    private static final Component TOOLTIP = Component.literal("Fe (")
        .withStyle(ChatFormatting.GOLD)
        .append(ModLang.IMPURE.translate())
        .append(Component.literal(")"));

    public ImpureSnIngotItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(TOOLTIP);
    }
}
