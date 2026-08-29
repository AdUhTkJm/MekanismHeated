package io.aduhtkjm.mekanismheated.integration.jade;

import mekanism.api.SerializationConstants;
import mekanism.api.chemical.ChemicalStack;
import mekanism.client.render.IFancyFontRenderer.TextAlignment;
import mekanism.common.integration.lookingat.ChemicalElement;
import mekanism.common.integration.lookingat.EnergyElement;
import mekanism.common.integration.lookingat.FluidElement;
import mekanism.common.integration.lookingat.LookingAtElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.neoforged.neoforge.fluids.FluidStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.Element;

public enum FusedPipeMekRenderer implements IComponentProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public ResourceLocation getUid() {
        return FusedPipeMekDataProvider.UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag serverData = accessor.getServerData();
        if (!serverData.contains(FusedPipeMekDataProvider.KEY, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag mhData = serverData.getCompound(FusedPipeMekDataProvider.KEY);

        // Energy
        if (mhData.contains("energy", Tag.TAG_COMPOUND)) {
            CompoundTag energyTag = mhData.getCompound("energy");
            long energy = energyTag.getLong(SerializationConstants.ENERGY);
            long capacity = energyTag.getLong(SerializationConstants.MAX);
            if (capacity > 0) {
                tooltip.add(new MekElement(
                      Component.translatable("mekanismheated.jade.energy"),
                      new EnergyElement(energy, capacity)));
            }
        }

        // Fluid
        if (mhData.contains("fluid", Tag.TAG_COMPOUND)) {
            CompoundTag fluidTag = mhData.getCompound("fluid");
            FluidStack fluid = FluidStack.parseOptional(
                  accessor.getLevel().registryAccess(),
                  fluidTag.getCompound(SerializationConstants.FLUID));
            int capacity = fluidTag.getInt(SerializationConstants.MAX);
            if (capacity > 0) {
                tooltip.add(new MekElement(
                      Component.translatable("mekanismheated.jade.fluid"),
                      new FluidElement(fluid, capacity)));
            }
        }

        // Chemical
        if (mhData.contains("chemical", Tag.TAG_COMPOUND)) {
            CompoundTag chemTag = mhData.getCompound("chemical");
            ChemicalStack chemical = ChemicalStack.parseOptional(
                  accessor.getLevel().registryAccess(),
                  chemTag.getCompound(SerializationConstants.CHEMICAL));
            long capacity = chemTag.getLong(SerializationConstants.MAX);
            if (capacity > 0) {
                tooltip.add(new MekElement(
                      Component.translatable("mekanismheated.jade.chemical"),
                      new ChemicalElement(chemical, capacity)));
            }
        }
    }

    private static class MekElement extends Element {

        private final Component text;
        private final LookingAtElement element;

        public MekElement(Component text, LookingAtElement element) {
            this.text = text;
            this.element = element;
        }

        @Override
        public Vec2 getSize() {
            int width = Math.max(element.getWidth(), 96);
            int height = element.getHeight() + 2 + 14;
            return new Vec2(width, height);
        }

        @Override
        public void render(GuiGraphics guiGraphics, float rawX, float rawY, float maxX, float maxY) {
            int x = Mth.floor(rawX);
            int y = Mth.floor(rawY);
            //Jade passes the element's position in as render coordinates rather than as a pose
            //translation. Mekanism's drawScrollingString computes its scissor rectangle from the
            //pose translation, so unless we set it (by translating the pose to the element's origin)
            //the scissor is misplaced and any *scrolling* text (i.e. a value string longer than the
            //element, which upgraded pipes' large summed capacities produce) gets clipped away and
            //vanishes. Short strings (like fluid) never scroll, which is why only energy/chemical
            //text was disappearing.
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(x, y, 0);
            element.drawScrollingString(guiGraphics, text, 0, 3, TextAlignment.LEFT, 0xFFFFFF, 4, false);
            element.render(guiGraphics, 0, 14);
            guiGraphics.pose().popPose();
        }
    }
}
