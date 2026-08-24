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
    HEAT_SMELTER_TEMPERATURE("gui", "heat_smelter.temperature"),
    HEAT_SMELTER_BUFFER("gui", "heat_smelter.buffer"),
    MIN_TEMPERATURE("gui", "min_temperature"),
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
