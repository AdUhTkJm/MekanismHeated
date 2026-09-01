package io.aduhtkjm.mekanismheated.integration.jade;

import mekanism.api.SerializationConstants;
import mekanism.common.integration.lookingat.FluidElement;
import mekanism.common.integration.lookingat.LookingAtElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.neoforged.neoforge.fluids.FluidStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.Element;

public enum HeatSmelterMekRenderer implements IComponentProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public ResourceLocation getUid() {
        return HeatSmelterMekDataProvider.UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag serverData = accessor.getServerData();
        if (!serverData.contains(HeatSmelterMekDataProvider.KEY, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag mhData = serverData.getCompound(HeatSmelterMekDataProvider.KEY);
        int capacity = mhData.getInt(SerializationConstants.MAX);
        ListTag fluidList = mhData.getList("fluids", Tag.TAG_COMPOUND);
        for (int i = 0; i < fluidList.size(); i++) {
            FluidStack fluid = FluidStack.parseOptional(
                  accessor.getLevel().registryAccess(),
                  fluidList.getCompound(i));
            if (!fluid.isEmpty()) {
                tooltip.add(new MekElement(new FluidElement(fluid, capacity)));
            }
        }
    }

    private static class MekElement extends Element {

        private final LookingAtElement element;

        public MekElement(LookingAtElement element) {
            this.element = element;
        }

        @Override
        public Vec2 getSize() {
            int width = Math.max(element.getWidth(), 96);
            int height = element.getHeight() + 2;
            return new Vec2(width, height);
        }

        @Override
        public void render(GuiGraphics guiGraphics, float rawX, float rawY, float maxX, float maxY) {
            int x = Mth.floor(rawX);
            int y = Mth.floor(rawY);
            //Jade passes the element's position in as render coordinates rather than as a pose
            //translation. Mekanism's drawScrollingString computes its scissor rectangle from the
            //pose translation, so unless we set it (by translating the pose to the element's origin)
            //the scissor is misplaced and any *scrolling* text (a value string longer than the
            //element) gets clipped away and vanishes.
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(x, y, 0);
            element.render(guiGraphics, 0, 1);
            guiGraphics.pose().popPose();
        }
    }
}
