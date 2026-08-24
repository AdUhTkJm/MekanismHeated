package io.aduhtkjm.mekanismheated.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluid;
import org.lwjgl.system.NonnullDefault;

@NonnullDefault
public class ChemicalFormulaBucketItem extends BucketItem {
    private final Component formula;

    public ChemicalFormulaBucketItem(Fluid fluid, Properties properties, String formula) {
        super(fluid, properties);
        this.formula = Component.literal(formula).withStyle(ChatFormatting.GOLD);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(formula);
    }
}
