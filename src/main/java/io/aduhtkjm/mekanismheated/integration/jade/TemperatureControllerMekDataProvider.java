package io.aduhtkjm.mekanismheated.integration.jade;

import io.aduhtkjm.mekanismheated.content.expression.Side;
import io.aduhtkjm.mekanismheated.tile.TileEntityTemperatureController;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Sends a temperature controller's output mode, last evaluated value, runtime failure and expression text to Jade, for
 * {@link TemperatureControllerMekRenderer} to display.
 *
 * <p>None of that reaches a client that merely looks at the block — the container trackers only run while a GUI is
 * open, and the reduced update tag carries nothing but the window's level — so the tooltip has to bring its own copy.
 * The expression goes over as text rather than as a pre-formatted error so that the renderer can parse it with the same
 * parser the server uses and report a syntax error in the client's own language, exactly as the GUI does.
 */
public enum TemperatureControllerMekDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("mekanismheated", "temperature_controller_mek_data");
    static final String KEY = "mh_temperature_controller";

    static final String MODE = "mode";
    static final String OUTPUT = "output";
    static final String ERROR = "error";
    static final String ERROR_SIDE = "errorSide";
    static final String EXPRESSION = "expression";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof TileEntityTemperatureController controller)) {
            return;
        }
        CompoundTag mhData = new CompoundTag();
        mhData.putByte(MODE, (byte) controller.getOutputMode().ordinal());
        mhData.putDouble(OUTPUT, controller.getOutput());
        mhData.putByte(ERROR, (byte) controller.getRuntimeError().ordinal());
        //Negative for "not a side-specific failure", matching how the tile's own trackers encode it.
        Side errorSide = controller.getErrorSide();
        mhData.putByte(ERROR_SIDE, errorSide == null ? -1 : (byte) errorSide.ordinal());
        mhData.putString(EXPRESSION, controller.getExpression());
        data.put(KEY, mhData);
    }
}
