package io.aduhtkjm.mekanismheated.block.fractionation;

import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public class DistillationTrayBlock extends IronBarsBlock {

    public DistillationTrayBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(5.0F, 6.0F)
              .sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.DESTROY));
    }
}
