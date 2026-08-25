package io.aduhtkjm.mekanismheated.tile;

import java.util.Collections;
import java.util.List;
import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedFunction;
import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedNetwork;
import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedPipeConfig;
import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedPipeNode;
import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedPipeRegistry;
import io.aduhtkjm.mekanismheated.registries.ModTileEntityTypes;
import mekanism.api.IConfigurable;
import mekanism.api.SerializationConstants;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.text.EnumColor;
import mekanism.common.MekanismLang;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.energy.DynamicStrictEnergyHandler;
import mekanism.common.capabilities.proxy.ProxyConfigurable;
import mekanism.common.capabilities.resolver.BasicSidedCapabilityResolver;
import mekanism.common.capabilities.resolver.manager.EnergyHandlerManager;
import mekanism.common.integration.energy.EnergyCompatUtils;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.tile.base.CapabilityTileEntity;
import mekanism.common.util.EnumUtils;
import mekanism.common.util.NBTUtils;
import mekanism.common.util.WorldUtils;
import mekanism.common.util.text.BooleanStateDisplay.OnOff;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.NonnullDefault;

/**
 * A fused pipe: one block position providing up to five transmission functions
 * (energy, fluid, chemical, heat, items), each individually tiered or disabled via its config.
 * <p>
 * Unlike Mekanism's transmitters this tile is not part of their typed network machinery; it joins
 * unified {@link FusedNetwork}s managed by {@link FusedPipeRegistry}. Neighboring machines interact
 * through standard capabilities backed by the network's buffers.
 */
@NonnullDefault
public class TileEntityFusedPipe extends CapabilityTileEntity implements ProxyConfigurable.ISidedConfigurable {

    /**
     * Exposes the wrench side-config interface ({@link IConfigurable}) to Mekanism's configurator.
     */
    public static final ICapabilityProvider<TileEntityFusedPipe, @Nullable Direction, IConfigurable> CONFIGURABLE_PROVIDER =
          CapabilityTileEntity.capabilityProvider(Capabilities.CONFIGURABLE,
                (tile, cap) -> new BasicSidedCapabilityResolver<>(tile, cap, ProxyConfigurable::new));

    private static final String TAG_CONNECTION_TYPES = "connection_types";

    private final FusedPipeNode node = new FusedPipeNode(this);

    private FusedPipeConfig config = FusedPipeConfig.defaults();
    private final ConnectionType[] connectionTypes = {ConnectionType.NORMAL, ConnectionType.NORMAL, ConnectionType.NORMAL, ConnectionType.NORMAL,
                                                      ConnectionType.NORMAL, ConnectionType.NORMAL};

    private boolean redstoneReactive;
    private boolean redstonePowered;
    private boolean redstoneSet;

    private boolean loaded;
    private boolean markJoined;
    private boolean forceUpdate = true;

    public TileEntityFusedPipe(BlockPos pos, BlockState state) {
        super(ModTileEntityTypes.FUSED_PIPE, pos, state);
        var energyHandlerManager = new EnergyHandlerManager(this::getExposedEnergyContainers,
            new DynamicStrictEnergyHandler(this::getExposedEnergyContainers, this::canExtractEnergy, this::canInsertEnergy, null));
        addCapabilityResolvers(List.of(energyHandlerManager));
    }

    //Content access

    public FusedPipeNode getNode() {
        return node;
    }

    public FusedPipeConfig getConfig() {
        return config;
    }

    @Nullable
    public FusedNetwork getNetwork() {
        return node.getNetwork();
    }

    public boolean isLoaded() {
        return loaded;
    }

    //Connection configuration

    @Nullable
    public ConnectionType getConnectionTypeRaw(@Nullable Direction side) {
        return side == null ? null : connectionTypes[side.ordinal()];
    }

    public void setConnectionTypeRaw(Direction side, ConnectionType type) {
        int index = side.ordinal();
        ConnectionType old = connectionTypes[index];
        if (old != type) {
            connectionTypes[index] = type;
            sideChanged(side, old, type);
        }
    }

    /**
     * @return true if this pipe is currently disabled by an active redstone signal.
     */
    public boolean isRedstoneActivated() {
        if (!redstoneReactive || !hasLevel()) {
            return false;
        }
        if (!redstoneSet) {
            redstonePowered = WorldUtils.isGettingPowered(getLevel(), getBlockPos());
            redstoneSet = true;
        }
        return redstonePowered;
    }

    private void recheckRedstoneState() {
        if (redstoneReactive && redstoneSet) {
            boolean previouslyPowered = redstonePowered;
            redstoneSet = false;
            if (previouslyPowered != isRedstoneActivated()) {
                //Availability of capabilities changed towards the outside
                invalidateCapabilitiesAll(EnergyCompatUtils.getLoadedEnergyCapabilities());
                invalidateCapabilities();
            }
        }
    }

    //Ticking

    public static void tickServer(Level level, BlockPos pos, BlockState state, TileEntityFusedPipe tile) {
        tile.onUpdateServer();
    }

    protected void onUpdateServer() {
        if (markJoined) {
            onWorldJoin();
            markJoined = false;
        }
        if (forceUpdate) {
            recheckRedstoneState();
            forceUpdate = false;
        } else if (redstoneReactive) {
            recheckRedstoneState();
        }
    }

    //Lifecycle

    /**
     * Called when the block was placed ({@code Block#setPlacedBy}) or joined a freshly loaded chunk.
     */
    public void onWorldJoin() {
        if (!isRemote()) {
            loaded = true;
            FusedPipeRegistry.trackOrphan(node);
        }
    }

    public void onNeighborBlockChange(@Nullable Direction side) {
        recheckRedstoneState();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (isRemote()) {
            loaded = true;
        } else {
            //Deferred until the first tick so that neighboring tiles are all present
            markJoined = true;
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (!isRemote()) {
            loaded = false;
            FusedPipeRegistry.untrack(node);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (!isRemote()) {
            //Persist our share of the network buffer before leaving; only this node keeps a
            // claim, the others' shares go straight back into the buffer
            FusedNetwork network = getNetwork();
            if (network != null) {
                network.distributeSharesToNodes();
                network.reclaimSharesExcept(node);
            }
            loaded = false;
            FusedPipeRegistry.untrack(node);
        }
    }

    //Capabilities

    private List<IEnergyContainer> getExposedEnergyContainers(@Nullable Direction side) {
        if (!config.isEnabled(FusedFunction.ENERGY)) {
            return Collections.emptyList();
        }
        FusedNetwork network = node.getNetwork();
        if (network == null) {
            //No network yet (or no longer); expose nothing rather than a dead buffer
            return Collections.emptyList();
        }
        if (side != null && (getConnectionTypeRaw(side) == ConnectionType.NONE || isRedstoneActivated())) {
            return Collections.emptyList();
        }
        return network.getEnergyContainers(side);
    }

    private boolean canExtractEnergy(@Nullable Direction side) {
        if (side == null)
            return true;

        var connectionType = getConnectionTypeRaw(side);
        if (connectionType == null)
            return false;
        return connectionType != ConnectionType.NONE && connectionType.canSendTo();
    }

    private boolean canInsertEnergy(@Nullable Direction side) {
        var connectionType = getConnectionTypeRaw(side);
        if (connectionType == null)
            return false;
        return connectionType != ConnectionType.NONE && connectionType.canAccept();
    }

    //Side config interaction

    @Override
    public InteractionResult onSneakRightClick(Player player, Direction side) {
        if (!isRemote()) {
            ConnectionType current = getConnectionTypeRaw(side);
            ConnectionType next = current == null ? ConnectionType.NORMAL : current.getNext();
            setConnectionTypeRaw(side, next);
            setChanged();
            player.displayClientMessage(MekanismLang.CONNECTION_TYPE.translateColored(EnumColor.GRAY, next), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onRightClick(Player player, Direction side) {
        if (!isRemote()) {
            redstoneReactive = !redstoneReactive;
            redstoneSet = false;
            recheckRedstoneState();
            setChanged();
            player.displayClientMessage(MekanismLang.REDSTONE_SENSITIVITY.translateColored(EnumColor.GRAY, EnumColor.INDIGO, OnOff.of(redstoneReactive)), true);
        }
        return InteractionResult.SUCCESS;
    }

    private void sideChanged(Direction side, ConnectionType old, ConnectionType type) {
        //Make sure the world re-queries our capabilities for that side
        EnergyCompatUtils.getLoadedEnergyCapabilities().forEach(this::invalidateCapabilityAll);
        invalidateCapabilities();
    }

    //NBT

    @Override
    public void loadAdditional(CompoundTag nbt, HolderLookup.Provider provider) {
        super.loadAdditional(nbt, provider);
        if (nbt.contains(FusedPipeConfig.TAG_CONFIG, Tag.TAG_COMPOUND)) {
            config = FusedPipeConfig.read(provider, nbt.getCompound(FusedPipeConfig.TAG_CONFIG));
        }
        NBTUtils.setIntArrayIfPresent(nbt, TAG_CONNECTION_TYPES, raw -> {
            for (int i = 0; i < EnumUtils.DIRECTIONS.length && i < raw.length; i++) {
                connectionTypes[i] = ConnectionType.BY_ID.apply(raw[i]);
            }
        });
        redstoneReactive = nbt.getBoolean(SerializationConstants.REDSTONE);
        NBTUtils.setLongIfPresent(nbt, SerializationConstants.ENERGY, node::setSavedEnergy);
    }

    @Override
    public void saveAdditional(CompoundTag nbt, HolderLookup.Provider provider) {
        super.saveAdditional(nbt, provider);
        nbt.put(FusedPipeConfig.TAG_CONFIG, config.write(provider, new CompoundTag()));
        int[] raw = new int[EnumUtils.DIRECTIONS.length];
        for (int i = 0; i < EnumUtils.DIRECTIONS.length; i++) {
            raw[i] = connectionTypes[i].ordinal();
        }
        nbt.putIntArray(TAG_CONNECTION_TYPES, raw);
        nbt.putBoolean(SerializationConstants.REDSTONE, redstoneReactive);
        long savedEnergy = node.getSavedEnergy();
        if (savedEnergy > 0L) {
            nbt.putLong(SerializationConstants.ENERGY, savedEnergy);
        }
    }

    /**
     * Serializes everything an item drop needs to restore this pipe elsewhere.
     */
    public CompoundTag saveForDrop(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("id", Mod.MODID + ":fused_pipe");
        tag.put(FusedPipeConfig.TAG_CONFIG, config.write(provider, new CompoundTag()));
        int[] raw = new int[EnumUtils.DIRECTIONS.length];
        for (int i = 0; i < EnumUtils.DIRECTIONS.length; i++) {
            raw[i] = connectionTypes[i].ordinal();
        }
        tag.putIntArray(TAG_CONNECTION_TYPES, raw);
        return tag;
    }
}
