package io.aduhtkjm.mekanismheated.client;

import io.aduhtkjm.mekanismheated.ModLang;
import io.aduhtkjm.mekanismheated.content.expression.ExpressionParseException;
import io.aduhtkjm.mekanismheated.content.expression.ExpressionRuntimeError;
import io.aduhtkjm.mekanismheated.content.expression.OutputMode;
import io.aduhtkjm.mekanismheated.content.expression.Side;
import io.aduhtkjm.mekanismheated.tile.TileEntityTemperatureController;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.text.EnergyDisplay;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.NonnullDefault;

/**
 * The sentences that describe a Temperature Controller's state, shared by its GUI and its Jade tooltip so that the two
 * can never say different things about the same controller.
 *
 * <p>All of it is derived on the client: the mode, the last output and any runtime failure come from the synced state,
 * while a syntax error is reported by parsing the expression text with the same parser the server uses — which is what
 * keeps the message in the client's own language and off the wire.
 */
@NonnullDefault
public final class TemperatureControllerText {

    private TemperatureControllerText() {
    }

    /**
     * The mode's own name, e.g. {@code "Energy"}, for use as the argument of a line describing it.
     */
    public static Component mode(OutputMode mode) {
        return switch (mode) {
            case ENERGY -> ModLang.GUI_TEMPERATURE_CONTROLLER_MODE_ENERGY.translate();
            case REDSTONE -> ModLang.GUI_TEMPERATURE_CONTROLLER_MODE_REDSTONE.translate();
        };
    }

    /**
     * The current output, in whatever unit the mode emits it: an energy rate in {@link OutputMode#ENERGY} and a signal
     * strength in {@link OutputMode#REDSTONE}. The value is the expression's raw result, so the same clamping the tile
     * applies before emitting has to be applied here before showing it.
     */
    public static Component output(OutputMode mode, double value) {
        if (mode == OutputMode.ENERGY) {
            long joules = MekanismUtils.convertToJoules(TileEntityTemperatureController.clampToEnergyUsage(value));
            return ModLang.GUI_TEMPERATURE_CONTROLLER_OUTPUT.translate(EnergyDisplay.of(joules));
        }
        return ModLang.GUI_TEMPERATURE_CONTROLLER_OUTPUT.translate(ModLang.GUI_TEMPERATURE_CONTROLLER_OUTPUT_REDSTONE.translate(
              TileEntityTemperatureController.clampToSignal(value)));
    }

    /**
     * Explains a parse failure, in red, from the kind, column and offending text of the exception.
     */
    public static Component parseError(ExpressionParseException error) {
        ModLang message = switch (error.kind()) {
            case UNEXPECTED_CHARACTER -> ModLang.GUI_TEMPERATURE_CONTROLLER_ERROR_UNEXPECTED_CHARACTER;
            case UNEXPECTED_TOKEN -> ModLang.GUI_TEMPERATURE_CONTROLLER_ERROR_UNEXPECTED_TOKEN;
            case UNEXPECTED_END -> ModLang.GUI_TEMPERATURE_CONTROLLER_ERROR_UNEXPECTED_END;
            case UNKNOWN_VARIABLE -> ModLang.GUI_TEMPERATURE_CONTROLLER_ERROR_UNKNOWN_VARIABLE;
            case TOO_DEEP -> ModLang.GUI_TEMPERATURE_CONTROLLER_ERROR_TOO_DEEP;
            case TOO_LARGE -> ModLang.GUI_TEMPERATURE_CONTROLLER_ERROR_TOO_LARGE;
        };
        //The kinds that have no detail of their own have no placeholder in their message either, so the empty string
        //is simply unused.
        return colored(ModLang.GUI_TEMPERATURE_CONTROLLER_ERROR_SYNTAX.translate(error.column(), message.translate(error.detail())), ChatFormatting.RED);
    }

    /**
     * Explains a runtime failure, in red. Only {@link ExpressionRuntimeError#NO_HEAT_CAPACITOR} names the side it
     * applies to; the tile never reports it without one, and the placeholder keeps a corrupt sync from taking the
     * caller down.
     *
     * <p>Callers check for {@link ExpressionRuntimeError#NONE} themselves, because "it worked" is not a failure and
     * what to say instead depends on where the line is being drawn.
     */
    public static Component runtimeError(ExpressionRuntimeError error, @Nullable Side side) {
        return switch (error) {
            case NO_HEAT_CAPACITOR -> colored(ModLang.GUI_TEMPERATURE_CONTROLLER_ERROR_NO_HEAT.translate(
                  side == null ? "?" : side.displayName()), ChatFormatting.RED);
            case RESULT_NOT_FINITE -> colored(ModLang.GUI_TEMPERATURE_CONTROLLER_ERROR_NOT_FINITE.translate(), ChatFormatting.RED);
            case NONE -> Component.empty();
        };
    }

    public static Component colored(Component component, ChatFormatting color) {
        return component.copy().withStyle(color);
    }
}
