package io.aduhtkjm.mekanismheated.tile;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.block.phasechange.PhaseChangeBlock;
import io.aduhtkjm.mekanismheated.content.phasechange.PhaseChangeHeatCapacitor;
import io.aduhtkjm.mekanismheated.content.phasechange.PhaseChangeTier;
import mekanism.api.IContentsListener;
import mekanism.api.heat.HeatAPI.HeatTransfer;
import mekanism.common.capabilities.heat.CachedAmbientTemperature;
import mekanism.common.capabilities.holder.heat.HeatCapacitorHelper;
import mekanism.common.capabilities.holder.heat.IHeatCapacitorHolder;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableDouble;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * The tile shared by all three phase-change blocks. The block it belongs to carries the tier, and therefore the
 * melting point; everything else is identical between tiers.
 */
public class TileEntityPhaseChangeBlock extends TileEntityMekanism {

    private PhaseChangeHeatCapacitor heatCapacitor;
    private double lastTransferLoss;
    private double lastEnvironmentLoss;

    public TileEntityPhaseChangeBlock(BlockPos pos, BlockState state) {
        //The tile class is shared by all three blocks, so the registry holder has to come from the state rather than
        //from a fixed ModBlocks constant.
        super(state.getBlockHolder(), pos, state);
    }

    /**
     * Reads this block's tier. It cannot be cached in a field because {@link #getInitialHeatCapacitors} is called from
     * the superclass constructor, before this class's fields are initialised.
     */
    public PhaseChangeTier getTier() {
        return getBlockState().getBlock() instanceof PhaseChangeBlock block ? block.getTier() : PhaseChangeTier.LOW;
    }

    @NotNull
    @Override
    protected IHeatCapacitorHolder getInitialHeatCapacitors(IContentsListener listener, CachedAmbientTemperature ambientTemperature) {
        HeatCapacitorHelper builder = HeatCapacitorHelper.forSide(facingSupplier);
        builder.addCapacitor(heatCapacitor = PhaseChangeHeatCapacitor.create(
              Config.PhaseChange.HEAT_CAPACITY.get(),
              Config.PhaseChange.INVERSE_CONDUCTION_COEFFICIENT.get(),
              Config.PhaseChange.INVERSE_INSULATION_COEFFICIENT.get(),
              getTier().getMeltingPoint(),
              Config.PhaseChange.BUFFER_CAPACITY.get(),
              ambientTemperature, listener));
        return builder.build();
    }

    @Override
    protected boolean onUpdateServer() {
        boolean sendUpdatePacket = super.onUpdateServer();
        //Treat holding or releasing latent heat as being active, which lights the block up.
        setActive(getBufferedHeat() > 0);
        HeatTransfer transfer = simulate();
        lastTransferLoss = transfer.adjacentTransfer();
        lastEnvironmentLoss = transfer.environmentTransfer();
        return sendUpdatePacket;
    }

    public double getMeltingPoint() {
        return heatCapacitor.getMeltingPoint();
    }

    public double getBufferCapacity() {
        return heatCapacitor.getBufferCapacity();
    }

    /**
     * Latent heat currently held in the buffer. Synced to the client indirectly: the base class syncs the capacitor's
     * stored heat, and this is derived from it plus the config-driven melting point and buffer capacity.
     */
    public double getBufferedHeat() {
        return heatCapacitor.getBufferedHeat();
    }

    public double getLastTransferLoss() {
        return lastTransferLoss;
    }

    public double getLastEnvironmentLoss() {
        return lastEnvironmentLoss;
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableDouble.create(this::getLastTransferLoss, value -> lastTransferLoss = value));
        container.track(SyncableDouble.create(this::getLastEnvironmentLoss, value -> lastEnvironmentLoss = value));
    }
}
