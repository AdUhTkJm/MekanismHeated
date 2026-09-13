package io.aduhtkjm.mekanismheated.tile;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.client.renderer.TileEntityTemperatureControllerRenderer;
import io.aduhtkjm.mekanismheated.content.expression.Expr;
import io.aduhtkjm.mekanismheated.content.expression.ExpressionParseException;
import io.aduhtkjm.mekanismheated.content.expression.ExpressionParser;
import io.aduhtkjm.mekanismheated.content.expression.ExpressionRuntimeError;
import io.aduhtkjm.mekanismheated.content.expression.ExpressionRuntimeException;
import io.aduhtkjm.mekanismheated.content.expression.OutputMode;
import io.aduhtkjm.mekanismheated.content.expression.Side;
import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import java.nio.charset.StandardCharsets;

import mekanism.api.heat.HeatAPI;
import mekanism.api.heat.IHeatHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableByte;
import mekanism.common.inventory.container.sync.SyncableByteArray;
import mekanism.common.inventory.container.sync.SyncableDouble;
import mekanism.common.inventory.container.sync.SyncableEnum;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.machine.TileEntityResistiveHeater;
import mekanism.common.util.EnumUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.NBTUtils;
import mekanism.common.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.NonnullDefault;

/**
 * Reads the ambient temperature and the temperature of the heat capacitors of the six blocks around it, evaluates a
 * player-written expression over those readings every tick, and pushes the result out either as the energy/tick of
 * adjacent coolers and resistive heaters or as a redstone signal strength.
 *
 * <p>The expression language lives in {@code content.expression} and is deliberately free of Minecraft types, so the
 * client can re-parse the same text locally to build a localised syntax error without any of it crossing the wire. Only
 * runtime failures — which depend on the world the client cannot see — are synced, as a
 * {@link ExpressionRuntimeError} plus the {@link Side} it applies to.
 */
@NonnullDefault
public class TileEntityTemperatureController extends TileEntityMekanism {

    private static final String TAG_EXPRESSION = "Expression";
    private static final String TAG_OUTPUT_MODE = "OutputMode";
    private static final String TAG_DISPLAY_LEVEL = "DisplayLevel";

    /**
     * Instead of comparing at most 1024 chars from {@link #compiledFrom} and {@link #expression} per
     * tick, we first do a quick hash check.
     */
    private record HashedString(String expr, long hash) {
        HashedString(String expr) {
            this(expr, expr.hashCode());
        }
    }

    private HashedString expression = new HashedString("");
    private int ticks = 0;
    /**
     * The compiled form of {@link #expression}. {@code null} whenever the expression is empty or does not parse, which
     * is exactly the "produces no output" state.
     */
    @Nullable
    private Expr compiled;
    /**
     * The text {@link #compiled} was built from, while {@link #expression} is the current expression. <p>
     *
     * When they're different then we rebuild.
     */
    private HashedString compiledFrom = new HashedString("");

    private OutputMode outputMode = OutputMode.REDSTONE;
    private ExpressionRuntimeError runtimeError = ExpressionRuntimeError.NONE;
    @Nullable
    private Side errorSide;
    /**
     * The last value the expression evaluated to, in the player's configured energy unit (or as a raw number in
     * redstone mode). Synced for the GUI's "Output" line.
     */
    private double output;
    /**
     * Current redstone signal strength, 0-15. Only ever non-zero in {@link OutputMode#REDSTONE}, and read by the
     * block's {@code AttributeRedstoneEmitter}.
     */
    private int redstoneOutput;
    /**
     * How many rows of the front window are lit, 0-{@link #DISPLAY_ROWS}. Computed server side because only the server
     * sees the per-chunk ambient temperature delta, and synced through the reduced update tag.
     */
    private byte displayLevel;
    /**
     * Ambient temperature in Kelvin as of the last server tick. On the client this is the synced copy, which is what
     * the GUI's "Ambient" line shows: the client's own calculation would be missing the chunk delta.
     */
    private double lastAmbientTemperature = HeatAPI.AMBIENT_TEMP;

    public TileEntityTemperatureController(BlockPos pos, BlockState state) {
        super(ModBlocks.TEMPERATURE_CONTROLLER, pos, state);
        updateLastAmbientTemperature(getLevel());
    }

    private void updateLastAmbientTemperature(@Nullable Level level) {
        lastAmbientTemperature = level == null ? HeatAPI.AMBIENT_TEMP : HeatAPI.getAmbientTemp(level, getBlockPos());
    }

    @Override
    protected boolean onUpdateServer() {
        boolean needsPacket = super.onUpdateServer();
        // Compare hash first for optimization. Always re-evaluate when expression changes.
        if (expression.hash() != compiledFrom.hash() || !expression.expr().equals(compiledFrom.expr())) {
            recompile();
            ticks = Config.TemperatureController.INTERVAL.get();
        }

        // Make sure the controller detects only once per interval.
        if (ticks-- > 0) {
            return needsPacket;
        }
        ticks = Config.TemperatureController.INTERVAL.get();

        // Evaluate and emit.
        updateLastAmbientTemperature(getLevel());
        double value = 0;
        runtimeError = ExpressionRuntimeError.NONE;
        errorSide = null;
        if (canFunction() && compiled != null) {
            // An empty or unparsable expression leaves the value at 0.
            try {
                value = Expr.evaluate(compiled, this::resolveVariable);
            } catch (ExpressionRuntimeException e) {
                runtimeError = e.kind();
                errorSide = e.side();
            }
        }
        output = value;
        emit(value);
        setActive(isWorking());

        // Front window level.
        byte level = computeDisplayLevel(lastAmbientTemperature);
        if (level != displayLevel) {
            displayLevel = level;
            needsPacket = true;
        }
        return needsPacket;
    }

    /**
     * Whether the controller is doing its job right now: the redstone gate is open, the expression parsed, and its
     * evaluation came back clean.
     */
    public boolean isWorking() {
        return canFunction() && compiled != null && runtimeError == ExpressionRuntimeError.NONE;
    }

    /**
     * Compiles {@link #expression}, leaving {@link #compiled} {@code null} when the text is empty or malformed. The
     * failure itself is not stored: the client parses the same text to describe it.
     */
    private void recompile() {
        compiledFrom = expression;
        runtimeError = ExpressionRuntimeError.NONE;
        errorSide = null;
        compiled = null;
        if (!expression.expr().isEmpty()) {
            try {
                compiled = ExpressionParser.parse(expression.expr());
            } catch (ExpressionParseException ignored) {
                // Deliberately ignored
            }
        }
    }

    /**
     * Pushes the evaluated value to wherever the current {@link OutputMode} sends it.
     */
    private void emit(double value) {
        if (outputMode == OutputMode.REDSTONE) {
            // Runs unconditionally, so that closing the redstone gate (or hitting an error) actively drops the signal
            // with a neighbour update rather than leaving it latched.
            int signal = clampToSignal(value);
            if (signal != redstoneOutput) {
                redstoneOutput = signal;
                Level world = getLevel();
                if (world != null) {
                    world.updateNeighborsAt(getBlockPos(), getBlockState().getBlock());
                }
            }
        } else if (canFunction() && runtimeError == ExpressionRuntimeError.NONE && compiled != null) {
            // Energy mode only writes while the gate is open and the expression is clean; on a gated, empty, malformed
            // or erroring expression the neighbours are left alone rather than being forced to 0.
            Level world = getLevel();
            if (world == null) {
                return;
            }
            long joules = MekanismUtils.convertToJoules(clampToEnergyUsage(value));
            for (Direction side : EnumUtils.DIRECTIONS) {
                BlockPos neighbourPos = getBlockPos().relative(side);
                BlockEntity neighbour = world.getBlockEntity(neighbourPos);
                // TODO: Shall we somewhat abstract this ability of setting the packet, e.g. with reflection?
                if (neighbour instanceof TileEntityCooler cooler) {
                    // setEnergyUsageFromPacket marks the neighbour for save unconditionally, so compare first: an
                    // unguarded per-tick write would keep the chunk dirty forever for no reason.
                    if (cooler.getEnergyContainer().getEnergyPerTick() != joules) {
                        cooler.setEnergyUsageFromPacket(joules);
                    }
                } else if (neighbour instanceof TileEntityResistiveHeater heater) {
                    if (heater.getEnergyContainer().getEnergyPerTick() != joules) {
                        heater.setEnergyUsageFromPacket(joules);
                    }
                }
            }
        }
    }

    /**
     * Supplies a variable to the expression being evaluated. The parser guarantees only built-in names reach here.
     */
    private double resolveVariable(String name) throws ExpressionRuntimeException {
        if (Side.AMBIENT_VARIABLE.equals(name)) {
            return lastAmbientTemperature;
        }
        Side side = Side.byVariable(name);
        if (side == null) {
            //Unreachable: ExpressionParser.BUILT_IN_VARIABLES is built from Side, so every accepted name maps.
            throw new IllegalStateException("Not a built-in expression variable: " + name);
        }
        return readSideTemperature(side);
    }

    /**
     * Reads the total temperature of the heat capacitors of the block on the given side.
     *
     * @throws ExpressionRuntimeException with {@link ExpressionRuntimeError#NO_HEAT_CAPACITOR} when that block has no
     *         heat capacitor at all — a runtime rather than parse failure, because the neighbour can change at any
     *         time while the expression text stays the same
     */
    private double readSideTemperature(Side side) throws ExpressionRuntimeException {
        Direction direction = toDirection(side);
        //Read through the neighbour's heat capability, the same way Mekanism's own heat simulation reads it: the
        //context is the side of the neighbour that faces us. The helper also copes with an unloaded neighbour.
        IHeatHandler handler = WorldUtils.getCapability(getLevel(), Capabilities.HEAT, getBlockPos().relative(direction), direction.getOpposite());
        if (handler != null && handler.getHeatCapacitorCount() > 0) {
            return handler.getTotalTemperature();
        }
        throw new ExpressionRuntimeException(ExpressionRuntimeError.NO_HEAT_CAPACITOR, side);
    }

    private static Direction toDirection(Side side) {
        return switch (side) {
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case EAST -> Direction.EAST;
            case WEST -> Direction.WEST;
            case UP -> Direction.UP;
            case DOWN -> Direction.DOWN;
        };
    }

    /**
     * Maps an ambient temperature onto a number of lit rows, with {@code floor} rather than {@code round} so that the
     * window starts lighting up as soon as the temperature is above the configured minimum and reaches every row
     * exactly at the maximum.
     */
    public static byte computeDisplayLevel(double temperature) {
        byte rows = TileEntityTemperatureControllerRenderer.DISPLAY_ROWS;
        double min = Config.TemperatureController.DISPLAY_MIN_TEMPERATURE.get();
        double max = Config.TemperatureController.DISPLAY_MAX_TEMPERATURE.get();
        if (temperature <= min || max <= min) {
            return 0;
        }
        if (temperature >= max) {
            return rows;
        }
        return (byte) Math.floor((temperature - min) / (max - min) * rows);
    }

    /**
     * Rounds half-up and then clamps to a redstone signal strength.
     */
    public static int clampToSignal(double value) {
        return (int) Math.clamp(Math.round(value), 0L, 15L);
    }

    /**
     * Rounds half-up and then clamps to the configured maximum, still in the player's configured energy unit per tick
     * (the caller converts to Joules).
     */
    public static long clampToEnergyUsage(double value) {
        return Math.clamp(Math.round(value), 0L, Config.TemperatureController.MAX_ENERGY_OUTPUT.get());
    }

    public String getExpression() {
        return expression.expr();
    }

    /**
     * Replaces the expression and recompiles it immediately, so a submitted expression is reflected in the GUI before
     * the next tick rather than after it.
     */
    public void setExpression(String expression) {
        if (!this.expression.expr().equals(expression)) {
            this.expression = new HashedString(expression);
            recompile();
            markForSave();
        }
    }

    public void setExpressionFromPacket(String expression) {
        setExpression(expression.length() > Config.TemperatureController.MAX_EXPRESSION_LENGTH.get()
              ? expression.substring(0, Config.TemperatureController.MAX_EXPRESSION_LENGTH.get())
              : expression);
    }

    public OutputMode getOutputMode() {
        return outputMode;
    }

    public void setOutputMode(OutputMode mode) {
        if (outputMode != mode) {
            outputMode = mode;
            if (mode == OutputMode.ENERGY && redstoneOutput != 0) {
                redstoneOutput = 0;
                Level world = getLevel();
                if (world != null) {
                    world.updateNeighborsAt(getBlockPos(), getBlockState().getBlock());
                }
            }
            markForSave();
        }
    }

    public void setOutputModeFromPacket(OutputMode mode) {
        OutputMode previous = outputMode;
        setOutputMode(mode);
        if (outputMode != previous) {
            sendUpdatePacket();
        }
    }

    public ExpressionRuntimeError getRuntimeError() {
        return runtimeError;
    }

    @Nullable
    public Side getErrorSide() {
        return errorSide;
    }

    public double getOutput() {
        return output;
    }

    public double getLastAmbientTemperature() {
        return lastAmbientTemperature;
    }

    public int getRedstoneOutput() {
        return redstoneOutput;
    }

    public byte getDisplayLevel() {
        return displayLevel;
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        // Mekanism syncs strings with a UTF-8 byte array.
        container.track(SyncableByteArray.create(
              () -> expression.expr().getBytes(StandardCharsets.UTF_8),
              bytes -> expression = new HashedString(new String(bytes, StandardCharsets.UTF_8))));
        container.track(SyncableEnum.create(OutputMode::byIndex, OutputMode.REDSTONE, this::getOutputMode, mode -> outputMode = mode));
        container.track(SyncableEnum.create(ExpressionRuntimeError::byIndex, ExpressionRuntimeError.NONE, this::getRuntimeError, error -> runtimeError = error));
        container.track(SyncableByte.create(this::getErrorSideOrdinal, this::setErrorSideOrdinal));
        container.track(SyncableDouble.create(this::getOutput, value -> output = value));
        container.track(SyncableDouble.create(this::getLastAmbientTemperature, value -> lastAmbientTemperature = value));
    }

    private byte getErrorSideOrdinal() {
        return errorSide == null ? -1 : (byte) errorSide.ordinal();
    }

    private void setErrorSideOrdinal(byte ordinal) {
        errorSide = ordinal < 0 ? null : Side.byIndex(ordinal);
    }

    @Override
    public void saveAdditional(CompoundTag nbt, HolderLookup.Provider provider) {
        super.saveAdditional(nbt, provider);
        nbt.putString(TAG_EXPRESSION, expression.expr());
        nbt.putString(TAG_OUTPUT_MODE, outputMode.name());
    }

    @Override
    public void loadAdditional(CompoundTag nbt, HolderLookup.Provider provider) {
        super.loadAdditional(nbt, provider);
        NBTUtils.setStringIfPresent(nbt, TAG_EXPRESSION, value -> expression = new HashedString(value));
        NBTUtils.setStringIfPresent(nbt, TAG_OUTPUT_MODE, value -> outputMode = outputModeByName(value));
    }

    /**
     * Resolves a stored mode name, falling back to the default for text that is not one (e.g. from an older or hand
     * edited save).
     */
    private static OutputMode outputModeByName(String name) {
        for (OutputMode mode : OutputMode.values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return OutputMode.REDSTONE;
    }

    @Override
    public CompoundTag getReducedUpdateTag(HolderLookup.Provider provider) {
        CompoundTag updateTag = super.getReducedUpdateTag(provider);
        updateTag.putByte(TAG_DISPLAY_LEVEL, displayLevel);
        return updateTag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider provider) {
        super.handleUpdateTag(tag, provider);
        displayLevel = tag.getByte(TAG_DISPLAY_LEVEL);
    }

    @Override
    public CompoundTag getConfigurationData(HolderLookup.Provider provider, Player player) {
        CompoundTag data = super.getConfigurationData(provider, player);
        data.putString(TAG_EXPRESSION, expression.expr());
        data.putString(TAG_OUTPUT_MODE, outputMode.name());
        return data;
    }

    @Override
    public void setConfigurationData(HolderLookup.Provider provider, Player player, CompoundTag data) {
        super.setConfigurationData(provider, player, data);
        NBTUtils.setStringIfPresent(data, TAG_EXPRESSION, this::setExpression);
        NBTUtils.setStringIfPresent(data, TAG_OUTPUT_MODE, value -> setOutputMode(outputModeByName(value)));
    }
}
