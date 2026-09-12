package io.aduhtkjm.mekanismheated;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.text.ILangEntry;
import net.minecraft.Util;

@NothingNullByDefault
public enum ModLang implements ILangEntry {
    DESCRIPTION_HEAT_SMELTER("block", "heat_smelter"),
    DESCRIPTION_SHAKER("block", "shaker"),
    DESCRIPTION_THERMAL_FRACTIONATION_CONTROLLER("block", "thermal_fractionation_controller"),
    DESCRIPTION_THERMAL_FRACTIONATION_CASING("block", "thermal_fractionation_casing"),
    DESCRIPTION_THERMAL_FRACTIONATION_VALVE("block", "thermal_fractionation_valve"),
    MULTIBLOCK_INVALID_INCOMPLETE_TRAY_LAYER("multiblock", "invalid.incomplete_tray_layer"),
    MULTIBLOCK_INVALID_TRAY_TOP("multiblock", "invalid.tray_top"),
    MULTIBLOCK_INVALID_TRAY_SPACING("multiblock", "invalid.tray_spacing"),
    GUI_FRACTIONATION_HEIGHT("gui", "fractionation.height"),
    GUI_FRACTIONATION_LAYERS("gui", "fractionation.layers"),
    GUI_ALLOYING("gui", "alloying"),
    HEAT_SMELTER_TEMPERATURE("gui", "heat_smelter.temperature"),
    HEAT_SMELTER_BUFFER("gui", "heat_smelter.buffer"),
    MIN_TEMPERATURE("gui", "min_temperature"),
    HEAT_CONSUMED("gui", "heat_consumed"),
    TEMPERATURE_RANGE("gui", "temperature_range"),
    REACTION_DURATION("gui", "reaction_duration"),
    PURE("tooltip", "pure"),
    IMPURE("tooltip", "impure"),
    DESCRIPTION_CREATIVE_HEAT_BLOCK("block", "creative_heat_block"),
    DESCRIPTION_CREATIVE_CHUNK_HEATER("block", "creative_chunk_heater"),
    GUI_TARGET_TEMPERATURE("gui", "target_temperature"),
    GUI_CHUNK_AMBIENT("gui", "chunk_ambient"),
    GUI_CHUNK_DELTA("gui", "chunk_delta"),
    DESCRIPTION_COOLER("block", "cooler"),
    COOLER_HOT_TEMPERATURE("gui", "cooler.hot_temperature"),
    COOLER_COLD_TEMPERATURE("gui", "cooler.cold_temperature"),
    COOLER_USAGE("gui", "cooler.usage"),
    DESCRIPTION_CONDENSER("block", "condenser"),
    DESCRIPTION_QUENCHING_ENRICHMENT_CHAMBER("block", "quenching_enrichment_chamber"),
    DESCRIPTION_REACTION_CHAMBER("block", "reaction_chamber"),
    DESCRIPTION_ATMOSPHERE_HEATER("block", "atmosphere_heater"),
    ATMOSPHERE_HEATER_REDUCTION("gui", "atmosphere_heater.reduction"),
    ATMOSPHERE_HEATER_CONSUMPTION("gui", "atmosphere_heater.consumption");

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
