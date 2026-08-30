package io.aduhtkjm.mekanismheated.block;

import io.aduhtkjm.mekanismheated.registries.ModTileEntityTypes;
import io.aduhtkjm.mekanismheated.tile.TileEntityFusedPipe;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import mekanism.api.tier.BaseTier;
import mekanism.common.lib.transmitter.ConnectionType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.NonnullDefault;

/**
 * The fused pipe block. Renders like a universal cable: the center core plus one arm per
 * side, using the mod's own fused pipe textures and geometry (the arm models were converted
 * from Mekanism's {@code transmitter_small.obj.mek}). Arms reflect the *effective* per-side
 * state pushed into the blockstate by the tile: {@code none} when the side is unconfigured or
 * has nothing to connect to, otherwise the configured {@link ConnectionType} (push and pull
 * have slightly different arm meshes, just like vanilla transmitters). The {@link #TIER}
 * property selects the texture set, matching the highest tier among the enabled functions
 * (basic when none is enabled). The energy glow of vanilla cables is not replicated.
 */
@NonnullDefault
public class BlockFusedPipe extends Block implements EntityBlock {

    public static final EnumProperty<ConnectionType> NORTH = EnumProperty.create("north", ConnectionType.class);
    public static final EnumProperty<ConnectionType> SOUTH = EnumProperty.create("south", ConnectionType.class);
    public static final EnumProperty<ConnectionType> WEST = EnumProperty.create("west", ConnectionType.class);
    public static final EnumProperty<ConnectionType> EAST = EnumProperty.create("east", ConnectionType.class);
    public static final EnumProperty<ConnectionType> UP = EnumProperty.create("up", ConnectionType.class);
    public static final EnumProperty<ConnectionType> DOWN = EnumProperty.create("down", ConnectionType.class);

    /**
     * The texture tier to render. Only the four non-creative tiers are valid, since the pipe
     * never displays the creative tier (a creative function is clamped to ultimate).
     */
    public static final EnumProperty<BaseTier> TIER = EnumProperty.create("tier", BaseTier.class,
          BaseTier.BASIC, BaseTier.ADVANCED, BaseTier.ELITE, BaseTier.ULTIMATE);

    private static final Direction[] PROPERTY_ORDER = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN};

    /**
     * Outline/collision shape per packed connection state (2 bits per direction); arms only exist
     * on sides that are configured to something other than {@link ConnectionType#NONE}.
     */
    private static final Map<Integer, VoxelShape> SHAPE_CACHE = new Int2ObjectOpenHashMap<>();

    private static final VoxelShape CORE_SHAPE = Block.box(5, 5, 5, 11, 11, 11);

    public BlockFusedPipe(Properties properties) {
        super(properties.pushReaction(PushReaction.BLOCK));
        //Avoid flickering.
        // The default state here does not affect any logic or connection features of the pipe;
        // these are solely computed inside the tile entity.
        //
        // The pipe's visual state updates on the next tick after it's placed by `onUpdateServer`.
        // It would transition more smoothly if we start from no arm rather than all arms,
        // hence these NONEs.
        registerDefaultState(defaultBlockState()
              .setValue(NORTH, ConnectionType.NONE).setValue(SOUTH, ConnectionType.NONE)
              .setValue(WEST, ConnectionType.NONE).setValue(EAST, ConnectionType.NONE)
              .setValue(UP, ConnectionType.NONE).setValue(DOWN, ConnectionType.NONE)
              .setValue(TIER, BaseTier.BASIC));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NORTH, SOUTH, WEST, EAST, UP, DOWN, TIER);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int packed = 0;
        for (Direction side : PROPERTY_ORDER) {
            packed |= state.getValue(propertyFor(side)).ordinal() << (side.ordinal() * 2);
        }
        return SHAPE_CACHE.computeIfAbsent(packed, BlockFusedPipe::computeShape);
    }

    private static VoxelShape computeShape(int packed) {
        VoxelShape shape = CORE_SHAPE;
        for (Direction side : PROPERTY_ORDER) {
            ConnectionType type = ConnectionType.BY_ID.apply(packed >>> (side.ordinal() * 2));
            if (type != ConnectionType.NONE) {
                shape = Shapes.or(shape, armShape(side, type));
            }
        }
        return shape.optimize();
    }

    private static VoxelShape armShape(Direction side, ConnectionType type) {
        //Pull arms are slightly wider than normal/push arms, mirroring the collar mesh
        int a = type == ConnectionType.PULL ? 4 : 5;
        int b = type == ConnectionType.PULL ? 12 : 11;
        return switch (side) {
            case NORTH -> Block.box(a, a, 0, b, b, 6);
            case SOUTH -> Block.box(a, a, 10, b, b, 16);
            case WEST -> Block.box(0, a, a, 6, b, b);
            case EAST -> Block.box(10, a, a, 16, b, b);
            case UP -> Block.box(a, 10, a, b, 16, b);
            case DOWN -> Block.box(a, 0, a, b, 6, b);
        };
    }

    /**
     * Packs the given config into the matching blockstate properties so clients can render the
     * current side configuration and texture tier. No-op if nothing changed.
     */
    public BlockState applyVisualState(BlockState state, ConnectionType[] connectionTypes, BaseTier tier) {
        BlockState changed = state;
        for (Direction side : PROPERTY_ORDER) {
            changed = changed.setValue(propertyFor(side), connectionTypes[side.ordinal()]);
        }
        return changed.setValue(TIER, tier);
    }

    public static EnumProperty<ConnectionType> propertyFor(Direction side) {
        return switch (side) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            case UP -> UP;
            case DOWN -> DOWN;
        };
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModTileEntityTypes.FUSED_PIPE.get().create(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("all") // Unchecked class cast
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == ModTileEntityTypes.FUSED_PIPE.get()
              ? (BlockEntityTicker<T>) ModTileEntityTypes.FUSED_PIPE.getTicker(level.isClientSide)
              : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TileEntityFusedPipe pipe) {
            pipe.onWorldJoin();
            //NBT from the item (if any) has been applied by now, so arms render immediately
            pipe.refreshVisualState();
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        Direction side = Direction.getNearest(neighborPos.getX() - pos.getX(), neighborPos.getY() - pos.getY(), neighborPos.getZ() - pos.getZ());
        if (level.getBlockEntity(pos) instanceof TileEntityFusedPipe pipe) {
            pipe.onNeighborBlockChange(side);
        }
    }

    /**
     * Drops the block item carrying this pipe's config so it is preserved on break. Every
     * destruction path (player, explosion, ...) funnels through here, and the loot params carry
     * the block entity when one is present. On placement vanilla applies the BLOCK_ENTITY_DATA
     * component back into the created block entity.
     */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof TileEntityFusedPipe pipe && !pipe.isRemoved()) {
            ItemStack drop = new ItemStack(this);
            CompoundTag data = pipe.saveForDrop(new CompoundTag(), params.getLevel().registryAccess());
            drop.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(data));
            return List.of(drop);
        }
        return List.of(new ItemStack(this));
    }
}
