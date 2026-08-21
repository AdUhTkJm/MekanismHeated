package io.aduhtkjm.mekanismheated;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.text.ILangEntry;
import net.minecraft.Util;

@NothingNullByDefault
public enum ModLang implements ILangEntry {
    DESCRIPTION_HEAT_SMELTER("block", "heat_smelter"),
    HEAT_SMELTER_TEMPERATURE("gui", "heat_smelter.temperature"),
    HEAT_SMELTER_BUFFER("gui", "heat_smelter.buffer");

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