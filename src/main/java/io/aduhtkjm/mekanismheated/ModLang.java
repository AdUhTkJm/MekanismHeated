package io.aduhtkjm.mekanismheated;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.text.ILangEntry;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;

@NothingNullByDefault
public enum ModLang implements ILangEntry {
    // Descriptions on tooltip.
    DESCRIPTION_HEAT_SMELTER("description", "heat_smelter"),
    DESCRIPTION_SHAKER("description", "shaker"),
    DESCRIPTION_THERMAL_FRACTIONATION_CONTROLLER("description", "thermal_fractionation_controller"),
    DESCRIPTION_THERMAL_FRACTIONATION_CASING("description", "thermal_fractionation_casing"),
    DESCRIPTION_THERMAL_FRACTIONATION_VALVE("description", "thermal_fractionation_valve"),
    DESCRIPTION_CONDENSER("description", "condenser"),
    DESCRIPTION_QUENCHING_ENRICHMENT_CHAMBER("description", "quenching_enrichment_chamber"),
    DESCRIPTION_REACTION_CHAMBER("description", "reaction_chamber"),
    DESCRIPTION_ATMOSPHERE_HEATER("description", "atmosphere_heater"),
    DESCRIPTION_PHASE_CHANGE_LOW("description", "phase_change_block_low"),
    DESCRIPTION_PHASE_CHANGE_MEDIUM("description", "phase_change_block_medium"),
    DESCRIPTION_PHASE_CHANGE_HIGH("description", "phase_change_block_high"),
    DESCRIPTION_CREATIVE_HEAT_BLOCK("description", "creative_heat_block"),
    DESCRIPTION_CREATIVE_CHUNK_HEATER("description", "creative_chunk_heater"),
    DESCRIPTION_COOLER("description", "cooler"),
    DESCRIPTION_TEMPERATURE_CONTROLLER("description", "temperature_controller"),
    DESCRIPTION_RETROENTROPIC_ARRAY_CASING("description", "retroentropic_array_casing"),

    // Strings used in GUI.
    GUI_ALLOYING("gui", "alloying"),
    GUI_RETROENTROPIC_ARRAY("gui", "retroentropic_array"),
    GUI_FRACTIONATION_HEIGHT("gui", "fractionation.height"),
    GUI_FRACTIONATION_LAYERS("gui", "fractionation.layers"),
    GUI_PHASE_CHANGE_MELTING_POINT("gui", "phase_change.melting_point"),
    GUI_PHASE_CHANGE_BUFFER("gui", "phase_change.buffer"),
    GUI_PHASE_CHANGE_CAPACITY("gui", "phase_change.capacity"),
    GUI_TARGET_TEMPERATURE("gui", "target_temperature"),
    GUI_CHUNK_AMBIENT("gui", "chunk_ambient"),
    GUI_CHUNK_DELTA("gui", "chunk_delta"),

    COOLER_HOT_TEMPERATURE("gui", "cooler.hot_temperature"),
    COOLER_COLD_TEMPERATURE("gui", "cooler.cold_temperature"),
    COOLER_USAGE("gui", "cooler.usage"),
    HEAT_SMELTER_TEMPERATURE("gui", "heat_smelter.temperature"),
    HEAT_SMELTER_BUFFER("gui", "heat_smelter.buffer"),
    MIN_TEMPERATURE("gui", "min_temperature"),
    HEAT_CONSUMED("gui", "heat_consumed"),
    TEMPERATURE_RANGE("gui", "temperature_range"),
    REACTION_DURATION("gui", "reaction_duration"),
    RETROENTROPIC_ARRAY_DURATION("gui", "retroentropic_array_duration"),
    GUI_RETROENTROPIC_ARRAY_PROGRESS("gui", "retroentropic_array.progress"),
    GUI_RETROENTROPIC_ARRAY_STATUS_ACTIVE("gui", "retroentropic_array.status.active"),
    GUI_RETROENTROPIC_ARRAY_STATUS_IDLE("gui", "retroentropic_array.status.idle"),
    GUI_RETROENTROPIC_ARRAY_STATUS_TOO_WARM("gui", "retroentropic_array.status.too_warm"),
    ATMOSPHERE_HEATER_REDUCTION("gui", "atmosphere_heater.reduction"),
    ATMOSPHERE_HEATER_CONSUMPTION("gui", "atmosphere_heater.consumption"),

    GUI_TEMPERATURE_CONTROLLER_AMBIENT("gui", "temperature_controller.ambient"),
    GUI_TEMPERATURE_CONTROLLER_OUTPUT("gui", "temperature_controller.output"),
    GUI_TEMPERATURE_CONTROLLER_OUTPUT_REDSTONE("gui", "temperature_controller.output.redstone"),
    GUI_TEMPERATURE_CONTROLLER_STATUS("gui", "temperature_controller.status"),
    GUI_TEMPERATURE_CONTROLLER_STATUS_OK("gui", "temperature_controller.status.ok"),
    GUI_TEMPERATURE_CONTROLLER_STATUS_EMPTY("gui", "temperature_controller.status.empty"),
    GUI_TEMPERATURE_CONTROLLER_MODE("gui", "temperature_controller.mode"),
    GUI_TEMPERATURE_CONTROLLER_MODE_ENERGY("gui", "temperature_controller.mode.energy"),
    GUI_TEMPERATURE_CONTROLLER_MODE_REDSTONE("gui", "temperature_controller.mode.redstone"),
    GUI_TEMPERATURE_CONTROLLER_MODE_ENERGY_TOOLTIP("gui", "temperature_controller.mode.energy.tooltip"),
    GUI_TEMPERATURE_CONTROLLER_MODE_REDSTONE_TOOLTIP("gui", "temperature_controller.mode.redstone.tooltip"),
    GUI_TEMPERATURE_CONTROLLER_INPUT_HINT("gui", "temperature_controller.input_hint"),
    GUI_TEMPERATURE_CONTROLLER_ERROR_SYNTAX("gui", "temperature_controller.error.syntax"),
    GUI_TEMPERATURE_CONTROLLER_ERROR_NO_HEAT("gui", "temperature_controller.error.no_heat"),
    GUI_TEMPERATURE_CONTROLLER_ERROR_INFINITE("gui", "temperature_controller.error.infinite"),
    GUI_TEMPERATURE_CONTROLLER_ERROR_NAN("gui", "temperature_controller.error.nan"),
    GUI_TEMPERATURE_CONTROLLER_ERROR_UNEXPECTED_CHARACTER("gui", "temperature_controller.error.unexpected_character"),
    GUI_TEMPERATURE_CONTROLLER_ERROR_UNEXPECTED_TOKEN("gui", "temperature_controller.error.unexpected_token"),
    GUI_TEMPERATURE_CONTROLLER_ERROR_UNEXPECTED_END("gui", "temperature_controller.error.unexpected_end"),
    GUI_TEMPERATURE_CONTROLLER_ERROR_UNKNOWN_VARIABLE("gui", "temperature_controller.error.unknown_variable"),
    GUI_TEMPERATURE_CONTROLLER_ERROR_TOO_DEEP("gui", "temperature_controller.error.too_deep"),
    GUI_TEMPERATURE_CONTROLLER_ERROR_TOO_LARGE("gui", "temperature_controller.error.too_large"),

    // Heat upgrades.
    UPGRADE_CONDUCTION("upgrade", "conduction"),
    UPGRADE_CONDUCTION_DESCRIPTION("upgrade", "conduction.description"),
    UPGRADE_INSULATION("upgrade", "insulation"),
    UPGRADE_INSULATION_DESCRIPTION("upgrade", "insulation.description"),
    UPGRADE_CAPACITY("upgrade", "capacity"),
    UPGRADE_CAPACITY_DESCRIPTION("upgrade", "capacity.description"),
    UPGRADE_INV_CONDUCTION("upgrade", "inv_conduction"),
    UPGRADE_INV_CONDUCTION_DESCRIPTION("upgrade", "inv_conduction.description"),
    UPGRADE_INV_INSULATION("upgrade", "inv_insulation"),
    UPGRADE_INV_INSULATION_DESCRIPTION("upgrade", "inv_insulation.description"),
    UPGRADE_INV_CAPACITY("upgrade", "inv_capacity"),
    UPGRADE_INV_CAPACITY_DESCRIPTION("upgrade", "inv_capacity.description"),
    GUI_HEAT_UPGRADES("gui", "upgrades.heat"),
    GUI_UPGRADE_AMOUNT("gui", "upgrade.amount"),

    // Miscellanous.
    MULTIBLOCK_INVALID_INCOMPLETE_TRAY_LAYER("multiblock", "invalid.incomplete_tray_layer"),
    MULTIBLOCK_INVALID_TRAY_TOP("multiblock", "invalid.tray_top"),
    MULTIBLOCK_INVALID_TRAY_SPACING("multiblock", "invalid.tray_spacing"),
    PURE("tooltip", "pure"),
    IMPURE("tooltip", "impure");

    private final String key;

    ModLang(String type, String path) {
        //Use MODID directly instead of Mod.rl: this enum is initialized while Mekanism's Upgrade enum is being extended
        //(see MixinUpgrade), and calling a method on Mod from there would force Mod to initialize that early.
        this(Util.makeDescriptionId(type, ResourceLocation.fromNamespaceAndPath(Mod.MODID, path)));
    }

    ModLang(String key) {
        this.key = key;
    }

    @Override
    public String getTranslationKey() {
        return key;
    }
}
