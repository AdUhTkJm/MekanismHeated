package io.aduhtkjm.mekanismheated.item;

import io.aduhtkjm.mekanismheated.block.BlockFusedPipe;
import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedFunction;
import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedPipeConfig;
import java.util.List;
import mekanism.api.tier.BaseTier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import org.lwjgl.system.NonnullDefault;

/**
 * The fused pipe item. Carries its {@link FusedPipeConfig} in the vanilla BLOCK_ENTITY_DATA
 * component so it is applied to the placed block automatically and preserved on break.
 */
@NonnullDefault
public class ItemBlockFusedPipe extends BlockItem {

    public ItemBlockFusedPipe(BlockFusedPipe block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data == null) {
            return;
        }
        CompoundTag tag = data.copyTag();
        if (!tag.contains(FusedPipeConfig.TAG_CONFIG, CompoundTag.TAG_COMPOUND)) {
            return;
        }
        FusedPipeConfig config = FusedPipeConfig.read(context.registries(), tag.getCompound(FusedPipeConfig.TAG_CONFIG));
        for (FusedFunction function : FusedFunction.VALUES) {
            BaseTier tier = config.getTier(function);
            if (tier != null) {
                tooltip.add(Component.translatable("tooltip.mekanismheated.fused_pipe.function",
                      pretty(function.name()), pretty(tier.name())));
            }
        }
    }

    private static String pretty(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
