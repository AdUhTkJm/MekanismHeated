package io.aduhtkjm.mekanismheated.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.lwjgl.system.NonnullDefault;

import io.aduhtkjm.mekanismheated.ModLang;
import java.util.List;

@NonnullDefault
public class PureFe2O3DustItem extends Item {
    public static final int TINT = 0xFF650E0E;

    private static final Component TOOLTIP = Component.literal("Fe₂O₃ (")
          .withStyle(ChatFormatting.GOLD)
          .append(ModLang.PURE.translate())
          .append(Component.literal(")"));

    public PureFe2O3DustItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(TOOLTIP);
    }
}
