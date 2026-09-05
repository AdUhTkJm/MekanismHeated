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
}
