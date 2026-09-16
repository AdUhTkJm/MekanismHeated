package io.aduhtkjm.mekanismheated.tile.prefab;

import io.aduhtkjm.mekanismheated.content.upgrade.IHeatedUpgradeTile;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import mekanism.api.IConfigurable;
import mekanism.api.SerializationConstants;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.api.text.EnumColor;
import mekanism.client.SparkleAnimation;
import mekanism.common.MekanismLang;
import mekanism.common.attachments.containers.ContainerType;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.heat.IHeatCapacitorHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.computer.BoundMethodHolder;
import mekanism.common.integration.computer.FactoryRegistry;
import mekanism.common.integration.computer.MethodRestriction;
import mekanism.common.integration.computer.annotation.ComputerMethod;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.dynamic.SyncMapper;
import mekanism.common.lib.multiblock.FormationProtocol.FormationResult;
import mekanism.common.lib.multiblock.IMultiblock;
import mekanism.common.lib.multiblock.IStructuralMultiblock;
import mekanism.common.lib.multiblock.MultiblockData;
import mekanism.common.lib.multiblock.Structure;
import mekanism.common.tile.prefab.TileEntityProgressMachine;
import mekanism.common.util.EnumUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.NBTUtils;
import mekanism.common.util.WorldUtils;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.NonnullDefault;

/**
 * Shared base for a tile that works both as a standalone {@link TileEntityProgressMachine} and, once enough of its
 * kind are placed adjacent, as a single {@link IMultiblock}.
 *
 * <p>This is the recipe-machine analogue of Mekanism's {@code TileEntityMultiblock}: it ports the {@link IMultiblock}
 * structure-handling glue (formation, master election, update-tag syncing, capability invalidation, etc.) on top of
 * {@link TileEntityProgressMachine}, so a concrete tile presents a normal single-block machine while unformed and a
 * shared brain while formed.</p>
 *
 * <p><b>Dual-mode ticking:</b> this base intentionally does <em>not</em> override {@code onUpdateServer()}. A concrete
 * tile drives the tick from its own {@code onUpdateServer()} override: first {@code super.onUpdateServer()} (base tick),
 * then {@link #tickMultiblock(MultiblockData)} <em>every tick</em> (formed or not, so formation is detected), and finally
 * its own standalone machine logic <em>only while unformed</em>. This keeps the per-block recipe logic dormant once the
 * multiblock has formed, since the shared brain then handles all processing.</p>
 *
 * <p><b>Contents:</b> the per-block container holders created by the final one-argument {@code getInitial*} hooks are the
 * standalone ones. When formed, a concrete tile should instead return the holders provided here (see
 * {@link #multiblockInventorySlotHolder()}, {@link #multiblockFluidTankHolder()}, {@link #multiblockHeatCapacitorHolder()})
 * from its three-argument {@code getInitial*} overrides.</p>
 */
@NonnullDefault
public abstract class TileEntityProgressMultiblockMachine<T extends MultiblockData, RECIPE extends MekanismRecipe<?>> extends TileEntityProgressMachine<RECIPE> implements IMultiblock<T>, IConfigurable {

    private Structure structure = Structure.INVALID;

    private final T defaultMultiblock = createMultiblock();

    /**
     * This multiblock's previous "has structure" state.
     */
    private boolean prevStructure;

    /**
     * Client-side copy of the previous tick's formed state, used to detect the formed -> unformed transition so GUIs
     * are only force-closed when a structure breaks, never while a machine is legitimately running standalone.
     * ({@link #prevStructure} cannot be reused for this: on the client it is overwritten by {@link #handleUpdateTag}
     * before the unformed state would be observed here.)
     */
    private boolean clientPrevFormed;

    /**
     * Whether this multiblock segment is rendering the structure.
     */
    private boolean isMaster;

    /**
     * This multiblock segment's cached inventory ID
     */
    @Nullable
    private UUID cachedID = null;

    // start at 100 to make sure we run the animation
    private long unformedTicks = 5L * SharedConstants.TICKS_PER_SECOND;

    protected TileEntityProgressMultiblockMachine(Holder<Block> blockProvider, BlockPos pos, BlockState state, List<RecipeError> errorTypes, int baseTicksRequired) {
        super(blockProvider, pos, state, errorTypes, baseTicksRequired);
        cacheCoord();
    }

    @Override
    public void setStructure(Structure structure) {
        this.structure = structure;
    }

    @Override
    public Structure getStructure() {
        return structure;
    }

    @Override
    public T getDefaultData() {
        return defaultMultiblock;
    }

    @Override
    protected void onUpdateClient() {
        super.onUpdateClient();
        boolean formed = getMultiblock().isFormed();
        if (!formed) {
            unformedTicks++;
            // Only close the GUI when a previously formed structure just broke. A standalone machine is legitimately
            // unformed, and closing on every unformed tick would instantly close its GUI right after opening it.
            if (clientPrevFormed && !playersUsing.isEmpty()) {
                for (Player player : new HashSet<>(playersUsing)) {
                    player.closeContainer();
                }
            }
        } else {
            unformedTicks = 0;
        }
        clientPrevFormed = formed;
    }

    /**
     * Runs the multiblock structure tick and all associated bookkeeping. A concrete tile must call this from its own
     * {@code onUpdateServer()} override <em>every tick</em> (formed or not), after calling {@code super.onUpdateServer()}
     * for the base tick, so that formation is detected and the structure stays validated.
     *
     * @param multiblock the current multiblock data (formed or not)
     * @return whether an update packet is needed
     */
    protected boolean tickMultiblock(T multiblock) {
        boolean needsPacket = false;
        if (ticker >= 3) {
            structure.tick(this, ticker % MekanismUtils.TICKS_PER_HALF_SECOND == 0);
        }
        if (isMaster() && multiblock.isFormed() && multiblock.recheckStructure) {
            multiblock.recheckStructure = false;
            getStructure().doImmediateUpdate(this, ticker % MekanismUtils.TICKS_PER_HALF_SECOND == 0);
            T newMultiblock = getMultiblock();
            if (newMultiblock != multiblock && !newMultiblock.isFormed()) {
                //force it to sync if it just unformed
                getManager().handleDirtyMultiblock(multiblock);
            }
            multiblock = newMultiblock;
        }
        if (multiblock.isFormed()) {
            if (!prevStructure) {
                structureChanged(multiblock);
                prevStructure = true;
                needsPacket = true;
            }
            if (multiblock.inventoryID != null) {
                UUID oldCachedID = cachedID;
                cachedID = multiblock.inventoryID;
                if (oldCachedID != cachedID) {
                    markForSave();
                }
                if (isMaster()) {
                    if (multiblock.tick(level)) {
                        needsPacket = true;
                    }
                    getManager().markTicked(multiblock);
                }
            }
        } else {
            //Only close the GUI when a previously formed structure just broke. A standalone machine is legitimately
            // unformed, and closing on every unformed tick would instantly close its GUI right after opening it.
            if (prevStructure) {
                if (!playersUsing.isEmpty()) {
                    playersUsing.forEach(Player::closeContainer);
                }
                structureChanged(multiblock);
                prevStructure = false;
                needsPacket = true;
            }
            isMaster = false;
        }
        needsPacket |= onUpdateServer(multiblock);
        return needsPacket;
    }

    /**
     * Per-tick hook for the concrete tile's multiblock behavior, called from {@link #tickMultiblock(MultiblockData)}.
     *
     * @return if we need an update packet
     */
    protected boolean onUpdateServer(T multiblock) {
        return false;
    }

    @Override
    public void resetForFormed() {
        // Clear this multiblock being master, and also mark it as we don't have a structure
        // as this method is only called when we have a formed multiblock so we want to just
        // treat it as us unforming if formed and then reforming
        isMaster = false;
        prevStructure = false;
    }

    protected void structureChanged(T multiblock) {
        invalidateCapabilitiesFull();
        if (multiblock.isFormed() && !multiblock.hasMaster && canBeMaster()) {
            multiblock.hasMaster = true;
            isMaster = true;
            //Force update the structure's comparator level as it may be incorrect due to not having a capacity while unformed
            multiblock.forceUpdateComparatorLevel();
            //If we are the block that is rendering the structure make sure to tell all the valves to update their comparator levels
            multiblock.notifyAllUpdateComparator(level);
        }
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        BlockPos pos = getBlockPos();
        for (Direction side : EnumUtils.DIRECTIONS) {
            mutable.setWithOffset(pos, side);
            if (!multiblock.isFormed() || !multiblock.isKnownLocation(mutable)) {
                BlockEntity tile = WorldUtils.getTileEntity(level, mutable);
                if (!level.isEmptyBlock(mutable) && (tile == null || tile.getClass() != getClass()) && !(tile instanceof IStructuralMultiblock || tile instanceof IMultiblock)) {
                    WorldUtils.notifyNeighborOfChange(level, mutable, pos);
                }
            }
        }
        if (!multiblock.isFormed()) {
            if (this instanceof IHeatedUpgradeTile heated) {
                //The per-block capacitors are live again now that the structure is gone, while they were dormant (and
                //so unscaled) while formed: re-apply this block's own heat upgrades to them
                heated.mekanismheated$recalculateHeatedUpgrades();
            }
            //If we have no structure just mark the comparator as dirty for each block,
            // this will only perform neighbor updates if the block supports comparators
            markDirtyComparator();
        }
    }

    @Override
    protected boolean makesComparatorDirty(ContainerType<?, ?, ?> type) {
        //Comparators are handled via the multiblock, no special listeners are needed
        return false;
    }

    @Override
    public boolean canBeMaster() {
        return true;
    }

    @Override
    public ItemInteractionResult onActivate(Player player, InteractionHand hand, ItemStack stack) {
        if (player.isShiftKeyDown() || !getMultiblock().isFormed()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        InteractionResult result = openGui(player);
        return switch (result) {
            case SUCCESS, SUCCESS_NO_ITEM_USED -> ItemInteractionResult.SUCCESS;
            case CONSUME -> ItemInteractionResult.CONSUME;
            case CONSUME_PARTIAL -> ItemInteractionResult.CONSUME_PARTIAL;
            case PASS -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            case FAIL -> ItemInteractionResult.FAIL;
        };
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (!isRemote()) {
            structure.invalidate(level);
        }
    }

    @Override
    public boolean shouldDumpRadiation() {
        return false;
    }

    @Override
    public void resetCache() {
        cachedID = null;
    }

    @Nullable
    @Override
    public UUID getCacheID() {
        return cachedID;
    }

    @Override
    public boolean isMaster() {
        return isMaster;
    }

    @Override
    public CompoundTag getReducedUpdateTag(HolderLookup.Provider provider) {
        CompoundTag updateTag = super.getReducedUpdateTag(provider);
        updateTag.putBoolean(SerializationConstants.RENDERING, isMaster());
        T multiblock = getMultiblock();
        updateTag.putBoolean(SerializationConstants.HAS_STRUCTURE, multiblock.isFormed());
        if (multiblock.isFormed() && isMaster()) {
            multiblock.writeUpdateTag(updateTag, provider);
        }
        return updateTag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider provider) {
        super.handleUpdateTag(tag, provider);
        NBTUtils.setBooleanIfPresent(tag, SerializationConstants.RENDERING, value -> isMaster = value);
        T multiblock = getMultiblock();
        NBTUtils.setBooleanIfPresent(tag, SerializationConstants.HAS_STRUCTURE, multiblock::setFormedForce);
        if (isMaster()) {
            if (multiblock.isFormed()) {
                multiblock.readUpdateTag(tag, provider);
                doMultiblockSparkle(multiblock);
            } else {
                // this will consecutively be set on the server
                isMaster = false;
            }
        }
        prevStructure = multiblock.isFormed();
    }

    /**
     * Only call on the client
     */
    private void doMultiblockSparkle(T multiblock) {
        if (isRemote() && multiblock.renderLocation != null && !prevStructure && unformedTicks >= 5) {
            //If player is within 40 blocks (1,600 = 40^2), show the status message/sparkles
            //Note: Do not change this from LocalPlayer to Player, or it will cause class loading issues on the server
            // due to trying to validate if the value is actually a Player
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null && worldPosition.distSqr(player.blockPosition()) <= 1_600) {
                if (MekanismConfig.client.enableMultiblockFormationParticles.get()) {
                    new SparkleAnimation(this, multiblock.renderLocation, multiblock.length() - 1, multiblock.width() - 1, multiblock.height() - 1).run();
                } else {
                    player.displayClientMessage(MekanismLang.MULTIBLOCK_FORMED_CHAT.translateColored(EnumColor.INDIGO), true);
                }
            }
        }
    }

    @Override
    public void loadAdditional(CompoundTag nbt, HolderLookup.Provider provider) {
        super.loadAdditional(nbt, provider);
        if (!getMultiblock().isFormed()) {
            NBTUtils.setUUIDIfPresent(nbt, SerializationConstants.INVENTORY_ID, id -> cachedID = id);
        }
    }

    @Override
    public void saveAdditional(CompoundTag nbtTags, HolderLookup.Provider provider) {
        super.saveAdditional(nbtTags, provider);
        if (cachedID != null) {
            //Note: We don't bother validating here the cache still exists as it is irrelevant and unused until attempting to form the multiblock
            // at which point it will gracefully handle multiblock tiles with stale ids and clear them
            nbtTags.putUUID(SerializationConstants.INVENTORY_ID, cachedID);
        }
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        SyncMapper.INSTANCE.setup(container, getMultiblock().getClass(), this::getMultiblock);
    }

    @Override
    public boolean persists(ContainerType<?, ?, ?> type) {
        if (type == ContainerType.ITEM || type == ContainerType.FLUID || type == ContainerType.HEAT) {
            // Only persist these while unformed. While formed they live in the shared brain, which is saved through the
            // manager's multiblock cache.
            return !getMultiblock().isFormed();
        }
        return super.persists(type);
    }

    @Override
    public boolean syncs(ContainerType<?, ?, ?> type) {
        if (type == ContainerType.FLUID || type == ContainerType.HEAT) {
            // While formed the shared containers are what the open GUI has to mirror, so keep tracking them even though
            // the per-block containers are no longer persisted.
            return type.canHandle(this);
        }
        return super.syncs(type);
    }

    /**
     * Holder exposing the formed multiblock's inventory slots. A concrete tile should return this from its
     * three-argument {@code getInitialInventory} override while formed.
     */
    protected IInventorySlotHolder multiblockInventorySlotHolder() {
        return side -> getMultiblock().getInventorySlots(side);
    }

    /**
     * Holder exposing the formed multiblock's fluid tanks. A concrete tile should return this from its
     * three-argument {@code getInitialFluidTanks} override while formed.
     */
    protected IFluidTankHolder multiblockFluidTankHolder() {
        return side -> getMultiblock().getFluidTanks(side);
    }

    /**
     * Holder exposing the formed multiblock's heat capacitors. A concrete tile should return this from its
     * three-argument {@code getInitialHeatCapacitors} override while formed.
     */
    protected IHeatCapacitorHolder multiblockHeatCapacitorHolder() {
        return side -> getMultiblock().getHeatCapacitors(side);
    }

    @Override
    @SuppressWarnings("all") // npe: level cannot be null
    public void onNeighborChange(Block block, BlockPos neighborPos) {
        super.onNeighborChange(block, neighborPos);
        if (isRemote())
            return;

        T multiblock = getMultiblock();
        if (multiblock.isPositionInsideBounds(getStructure(), neighborPos)) {
            //If the neighbor change happened from inside the bounds of the multiblock,
            if (level.isEmptyBlock(neighborPos) || !multiblock.internalLocations.contains(neighborPos)) {
                //And we are not already an internal part of the structure, or we are changing an internal part to air
                // then we mark the structure as needing to be re-validated
                //Note: This isn't a super accurate check as if a node gets replaced by command or mod with say dirt
                // it won't know to invalidate it but oh well. (See java docs on internalLocations for more caveats)
                getStructure().markForUpdate(level, true);
            }
        }
    }

    @Override
    public InteractionResult onRightClick(Player player) {
        if (!isRemote() && !getMultiblock().isFormed()) {
            FormationResult result = getStructure().runUpdate(this);
            if (!result.isFormed() && result.getResultText() != null) {
                player.sendSystemMessage(result.getResultText());
                return InteractionResult.sidedSuccess(isRemote());
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult onSneakRightClick(Player player) {
        return InteractionResult.PASS;
    }

    //Methods relating to IComputerTile
    public boolean exposesMultiblockToComputer() {
        return true;
    }

    @Override
    public boolean isComputerCapabilityPersistent() {
        //We are not persistent regardless of if our tile has support, unless we don't expose the multiblock itself to the computer
        return !exposesMultiblockToComputer() && super.isComputerCapabilityPersistent();
    }

    @Override
    public void getComputerMethods(BoundMethodHolder holder) {
        super.getComputerMethods(holder);
        if (exposesMultiblockToComputer()) {
            T multiblock = getMultiblock();
            if (multiblock.isFormed()) {
                //Only expose the multiblock's methods if we are formed, when the formation state changes
                // our capabilities are invalidated, so should end up getting rechecked and this called by
                // the various computer integration mods, and allow us to only expose the multiblock's methods
                // as even existing if the multiblock is complete
                FactoryRegistry.bindTo(holder, multiblock);
            }
        }
    }

    @ComputerMethod(restriction = MethodRestriction.MULTIBLOCK)
    boolean isFormed() {
        return getMultiblock().isFormed();
    }
    //End methods IComputerTile
}
