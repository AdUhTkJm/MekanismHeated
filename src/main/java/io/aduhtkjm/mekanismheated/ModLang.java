package io.aduhtkjm.mekanismheated;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.text.ILangEntry;
import net.minecraft.Util;

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

    // Strings used in GUI.
    GUI_ALLOYING("gui", "alloying"),
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
    ATMOSPHERE_HEATER_REDUCTION("gui", "atmosphere_heater.reduction"),
    ATMOSPHERE_HEATER_CONSUMPTION("gui", "atmosphere_heater.consumption"),

    // Miscellanous.
    MULTIBLOCK_INVALID_INCOMPLETE_TRAY_LAYER("multiblock", "invalid.incomplete_tray_layer"),
    MULTIBLOCK_INVALID_TRAY_TOP("multiblock", "invalid.tray_top"),
    MULTIBLOCK_INVALID_TRAY_SPACING("multiblock", "invalid.tray_spacing"),
    PURE("tooltip", "pure"),
    IMPURE("tooltip", "impure");

    private final String key;

    ModLang(String type, String path) {
        this(Util.makeDescriptionId(type, Mod.rl(path)));
    }

    ModLang(String key) {
        this.key = key;
    }

    @Override
    public String getTranslationKey() {
        return key;
    }
}
