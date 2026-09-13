package io.aduhtkjm.mekanismheated.integration.jade;

import io.aduhtkjm.mekanismheated.ModLang;
import io.aduhtkjm.mekanismheated.client.TemperatureControllerText;
import io.aduhtkjm.mekanismheated.content.expression.ExpressionParseException;
import io.aduhtkjm.mekanismheated.content.expression.ExpressionParser;
import io.aduhtkjm.mekanismheated.content.expression.ExpressionRuntimeError;
import io.aduhtkjm.mekanismheated.content.expression.OutputMode;
import io.aduhtkjm.mekanismheated.content.expression.Side;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Shows what a temperature controller is doing: the mode it is in and then either its current output or, when there is
 * one, the error that is stopping it — a syntax error the client finds by parsing the same expression text the server
 * has, or a runtime failure the server reported because only it can see the neighbours.
 *
 * <p>The expression itself is deliberately not shown; it is a long piece of text that the player who wrote it already
 * knows, and it is the one thing the GUI is for.
 */
public enum TemperatureControllerMekRenderer implements IComponentProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public ResourceLocation getUid() {
        return TemperatureControllerMekDataProvider.UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag serverData = accessor.getServerData();
        if (!serverData.contains(TemperatureControllerMekDataProvider.KEY, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag mhData = serverData.getCompound(TemperatureControllerMekDataProvider.KEY);
        OutputMode mode = OutputMode.byIndex(mhData.getByte(TemperatureControllerMekDataProvider.MODE));
        //Labelled rather than bare, so that "Energy" cannot be mistaken for a reading.
        tooltip.add(ModLang.GUI_TEMPERATURE_CONTROLLER_MODE.translate(TemperatureControllerText.mode(mode)));

        String expression = mhData.getString(TemperatureControllerMekDataProvider.EXPRESSION);
        if (expression.isEmpty()) {
            //Not an error, but "Output: 0" would be a misleading way to describe a controller nobody has set up.
            tooltip.add(TemperatureControllerText.colored(ModLang.GUI_TEMPERATURE_CONTROLLER_STATUS_EMPTY.translate(), ChatFormatting.YELLOW));
            return;
        }
        ExpressionParseException parseError = parse(expression);
        if (parseError != null) {
            tooltip.add(TemperatureControllerText.parseError(parseError));
            return;
        }
        ExpressionRuntimeError runtimeError = ExpressionRuntimeError.byIndex(mhData.getByte(TemperatureControllerMekDataProvider.ERROR));
        if (runtimeError != ExpressionRuntimeError.NONE) {
            tooltip.add(TemperatureControllerText.runtimeError(runtimeError, errorSide(mhData)));
            return;
        }
        tooltip.add(TemperatureControllerText.output(mode, mhData.getDouble(TemperatureControllerMekDataProvider.OUTPUT)));
    }

    @Nullable
    private static ExpressionParseException parse(String expression) {
        try {
            ExpressionParser.parse(expression);
            return null;
        } catch (ExpressionParseException e) {
            return e;
        }
    }

    /**
     * The side a runtime failure applies to, or {@code null} when the server did not report one.
     */
    @Nullable
    private static Side errorSide(CompoundTag mhData) {
        byte ordinal = mhData.getByte(TemperatureControllerMekDataProvider.ERROR_SIDE);
        return ordinal < 0 ? null : Side.byIndex(ordinal);
    }
}
