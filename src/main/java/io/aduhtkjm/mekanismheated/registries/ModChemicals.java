package io.aduhtkjm.mekanismheated.registries;

import io.aduhtkjm.mekanismheated.Mod;
import mekanism.api.chemical.Chemical;
import mekanism.common.registration.impl.ChemicalDeferredRegister;
import mekanism.common.registration.impl.DeferredChemical;

public class ModChemicals {

    private ModChemicals() {
    }

    public static final ChemicalDeferredRegister CHEMICALS = new ChemicalDeferredRegister(Mod.MODID);

    public static final DeferredChemical<Chemical> CARBON_MONOXIDE = CHEMICALS.register("carbon_monoxide", 0x4A4A4A);
    public static final DeferredChemical<Chemical> CARBON_DIOXIDE = CHEMICALS.register("carbon_dioxide", 0x2F2F2F);
    public static final DeferredChemical<Chemical> NITROGEN = CHEMICALS.register("nitrogen", 0x85CBEE);
    public static final DeferredChemical<Chemical> METHANOL = CHEMICALS.register("gas_methanol", 0xCDCDB2);
    public static final DeferredChemical<Chemical> UNSTABLE_LAVA = CHEMICALS.register("gas_unstable_lava", 0xFF8C00);
}
