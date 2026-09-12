package io.aduhtkjm.mekanismheated.client.gui.machine;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.ModLang;
import io.aduhtkjm.mekanismheated.content.expression.ExpressionParseException;
import io.aduhtkjm.mekanismheated.content.expression.ExpressionParser;
import io.aduhtkjm.mekanismheated.content.expression.ExpressionRuntimeError;
import io.aduhtkjm.mekanismheated.content.expression.OutputMode;
import io.aduhtkjm.mekanismheated.content.expression.Side;
import io.aduhtkjm.mekanismheated.network.PacketSetTemperatureControllerMode;
import io.aduhtkjm.mekanismheated.network.PacketSetTemperatureExpression;
import io.aduhtkjm.mekanismheated.tile.TileEntityTemperatureController;
import java.util.List;
import mekanism.api.functions.CharPredicate;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.text.BackgroundType;
import mekanism.client.gui.element.text.ButtonType;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.network.PacketUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import mekanism.common.util.text.EnergyDisplay;
import mekanism.common.util.text.InputValidator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * GUI of the Temperature Controller: a status screen showing the ambient temperature, the current output and whether
 * the controller is actually working, a button switching between the redstone and energy output modes, and the text
 * field holding the expression.
 *
 * <p>Syntax errors are reported by parsing the same text locally with the expression engine the server uses, so they
 * cost no round trip and follow the client's language. Only runtime failures — which depend on a world the client
 * cannot see — come from the server, as a small enum plus the side it applies to.
 */
public class GuiTemperatureController extends GuiMekanismTile<TileEntityTemperatureController, MekanismTileContainer<TileEntityTemperatureController>> {

    /**
     * A keyboard filter only: the real validation is the parser. It just keeps obviously impossible characters out of
     * the field (and therefore out of the packet).
     */
    private static final CharPredicate EXPRESSION_CHARS = InputValidator.LETTER_OR_DIGIT.or(
          InputValidator.from('+', '-', '*', '/', '>', '<', '=', '!', '?', ':', '(', ')', '.', '_', ' '));

    private static final int NORMAL_TEXT_COLOR = 0xFFE0E0E0;
    private static final int ERROR_TEXT_COLOR = 0xFFFF5555;

    private GuiTextField expressionField;
    private MekanismButton modeButton;
    /**
     * The mode the button is currently showing, so it is only rebuilt when it actually changes rather than every tick.
     */
    @Nullable
    private OutputMode shownMode;

    /**
     * The text the cached {@link #parseError} was produced from.
     */
    @Nullable
    private String parsedText;
    @Nullable
    private ExpressionParseException parseError;

    public GuiTemperatureController(MekanismTileContainer<TileEntityTemperatureController> container, Inventory inv, Component title) {
        super(container, inv, title);
        //The window is 20 pixels wider than the default and the player inventory is shifted 10 pixels right (see
        //ModContainerTypes), so that the extra width lands on both sides of the slots.
        imageWidth += 20;
        inventoryLabelX += 10;
        inventoryLabelY += 2;
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addRenderableWidget(new GuiInnerScreen(this, 48, 19, 130, 40, this::screenLines)).clearFormat();

        OutputMode mode = tile.getOutputMode();
        modeButton = addRenderableWidget(new MekanismButton(this, 8, 61, 36, 14, modeLabel(mode), (element, mouseX, mouseY) -> {
            toggleOutputMode();
            return true;
        }));
        modeButton.setTooltip(Tooltip.create(modeTooltip(mode)));
        shownMode = mode;

        expressionField = addRenderableWidget(new GuiTextField(this, 48, 61, 130, 12));
        expressionField.setMaxLength(Config.TemperatureController.MAX_EXPRESSION_LENGTH.get());
        expressionField.setInputValidator(EXPRESSION_CHARS)
              .setEnterHandler(this::submitExpression)
              .addCheckmarkButton(ButtonType.NORMAL, this::submitExpression)
              .setBackground(BackgroundType.DEFAULT);
        //Deliberately not focused on open: the controller has nothing else to type into, so grabbing the keyboard would
        //swallow the player's shortcuts for no benefit. Clicking the field focuses it.
        expressionField.setTooltip(ModLang.GUI_TEMPERATURE_CONTROLLER_INPUT_HINT);
    }

    @Override
    public void containerTick() {
        super.containerTick();

        //Mirror the server's expression into the field, but only while the player is not editing it: a slow round trip
        //must not clobber characters typed since submitting. Condition two keeps two players' GUIs in sync.
        String displayed = expressionField.getText();
        String expression = tile.getExpression();
        if (!expressionField.isTextFieldFocused() && !expression.equals(displayed)) {
            expressionField.setText(expression);
            displayed = expression;
        }

        //Colour the field from its own text so that a typo shows up as the player types it, before submitting.
        boolean error = (!displayed.isEmpty() && parseError(displayed) != null) || tile.getRuntimeError() != ExpressionRuntimeError.NONE;
        expressionField.setTextColor(error ? ERROR_TEXT_COLOR : NORMAL_TEXT_COLOR);

        OutputMode mode = tile.getOutputMode();
        if (mode != shownMode) {
            shownMode = mode;
            modeButton.setMessage(modeLabel(mode));
            modeButton.setTooltip(Tooltip.create(modeTooltip(mode)));
        }
    }

    private List<Component> screenLines() {
        return List.of(
              ModLang.GUI_TEMPERATURE_CONTROLLER_AMBIENT.translate(
                    MekanismUtils.getTemperatureDisplay(tile.getLastAmbientTemperature(), TemperatureUnit.KELVIN, true)),
              outputLine(),
              statusLine()
        );
    }

    private Component outputLine() {
        if (tile.getOutputMode() == OutputMode.ENERGY) {
            long joules = MekanismUtils.convertToJoules(TileEntityTemperatureController.clampToEnergyUsage(tile.getLastOutput()));
            return ModLang.GUI_TEMPERATURE_CONTROLLER_OUTPUT.translate(EnergyDisplay.of(joules));
        }
        return ModLang.GUI_TEMPERATURE_CONTROLLER_OUTPUT.translate(ModLang.GUI_TEMPERATURE_CONTROLLER_OUTPUT_REDSTONE.translate(
              TileEntityTemperatureController.clampToSignal(tile.getLastOutput())));
    }

    /**
     * The status line. It describes the text currently in the field, which is the synced expression whenever the player
     * is not editing it, so an invalid expression is flagged as it is typed instead of after a round trip.
     */
    private Component statusLine() {
        String expression = expressionField.getText();
        if (expression.isEmpty()) {
            return status(colored(ModLang.GUI_TEMPERATURE_CONTROLLER_STATUS_EMPTY.translate(), ChatFormatting.YELLOW));
        }
        ExpressionParseException error = parseError(expression);
        if (error != null) {
            return status(colored(ModLang.GUI_TEMPERATURE_CONTROLLER_ERROR_SYNTAX.translate(error.column(), describe(error)), ChatFormatting.RED));
        }
        return switch (tile.getRuntimeError()) {
            case NO_HEAT_CAPACITOR -> {
                Side side = tile.getErrorSide();
                //The tile only ever reports this kind together with the side it applies to; the placeholder keeps a
                //corrupt sync from taking the GUI down.
                yield status(colored(ModLang.GUI_TEMPERATURE_CONTROLLER_ERROR_NO_HEAT.translate(side == null ? "?" : side.displayName()), ChatFormatting.RED));
            }
            case RESULT_NOT_FINITE -> status(colored(ModLang.GUI_TEMPERATURE_CONTROLLER_ERROR_NOT_FINITE.translate(), ChatFormatting.RED));
            case NONE -> tile.isRedstoneActivated()
                  ? status(colored(ModLang.GUI_TEMPERATURE_CONTROLLER_STATUS_OK.translate(), ChatFormatting.GREEN))
                  : status(colored(ModLang.GUI_TEMPERATURE_CONTROLLER_STATUS_GATED.translate(), ChatFormatting.YELLOW));
        };
    }

    private static Component status(Component status) {
        return ModLang.GUI_TEMPERATURE_CONTROLLER_STATUS.translate(status);
    }

    /**
     * Explains a parse failure using the client's own language, from the kind, column and offending text of the
     * exception.
     */
    private static Component describe(ExpressionParseException error) {
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
        return message.translate(error.detail());
    }

    /**
     * Parses the given text unless it is the one already parsed, so the (comparatively expensive) parse runs on a change
     * instead of every frame.
     */
    @Nullable
    private ExpressionParseException parseError(String expression) {
        if (!expression.equals(parsedText)) {
            parsedText = expression;
            parseError = null;
            try {
                ExpressionParser.parse(expression);
            } catch (ExpressionParseException e) {
                parseError = e;
            }
        }
        return parseError;
    }

    private static Component colored(Component component, ChatFormatting color) {
        return component.copy().withStyle(color);
    }

    private static Component modeLabel(OutputMode mode) {
        return switch (mode) {
            case ENERGY -> ModLang.GUI_TEMPERATURE_CONTROLLER_MODE_ENERGY.translate();
            case REDSTONE -> ModLang.GUI_TEMPERATURE_CONTROLLER_MODE_REDSTONE.translate();
        };
    }

    private static Component modeTooltip(OutputMode mode) {
        return switch (mode) {
            case ENERGY -> ModLang.GUI_TEMPERATURE_CONTROLLER_MODE_ENERGY_TOOLTIP.translate();
            case REDSTONE -> ModLang.GUI_TEMPERATURE_CONTROLLER_MODE_REDSTONE_TOOLTIP.translate();
        };
    }

    private void toggleOutputMode() {
        OutputMode next = tile.getOutputMode() == OutputMode.REDSTONE ? OutputMode.ENERGY : OutputMode.REDSTONE;
        PacketUtils.sendToServer(new PacketSetTemperatureControllerMode(tile.getBlockPos(), (byte) next.ordinal()));
    }

    /**
     * Sends the text verbatim, even when it is empty or does not parse. The focus stays in the field and the text is
     * not cleared, unlike the numeric fields elsewhere in the mod: re-typing a long expression after a typo would be
     * hostile.
     */
    private void submitExpression() {
        PacketUtils.sendToServer(new PacketSetTemperatureExpression(tile.getBlockPos(), expressionField.getText()));
    }

    @Override
    protected void drawForegroundText(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        //Offset by the same 10 pixels the player inventory was shifted by, so the title stays where it looks like it is.
        renderTitleTextWithOffset(guiGraphics, 18);
        renderInventoryText(guiGraphics);
        super.drawForegroundText(guiGraphics, mouseX, mouseY);
    }
}
