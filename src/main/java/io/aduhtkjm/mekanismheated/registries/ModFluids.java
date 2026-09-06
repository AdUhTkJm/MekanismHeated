package io.aduhtkjm.mekanismheated.registries;

import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.item.ChemicalFormulaBucketItem;
import java.util.function.UnaryOperator;
import mekanism.common.registration.impl.FluidDeferredRegister;
import mekanism.common.registration.impl.FluidDeferredRegister.MekanismFluidType;
import mekanism.common.registration.impl.FluidRegistryObject;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.block.LiquidBlock;
import net.neoforged.neoforge.fluids.BaseFlowingFluid.Flowing;
import net.neoforged.neoforge.fluids.BaseFlowingFluid.Source;

public class ModFluids {

    private ModFluids() {
    }

    public static final FluidDeferredRegister FLUIDS = new FluidDeferredRegister(Mod.MODID);

    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, BucketItem> WOOD_TAR =
        FLUIDS.register("wood_tar", renderProperties -> renderProperties.tint(0xFF513721));
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, BucketItem> ASPHALT =
        FLUIDS.register("asphalt", renderProperties -> renderProperties.tint(0xFF234623));
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, ChemicalFormulaBucketItem> METHANOL =
        FLUIDS.register("methanol", (fluid, properties) -> new ChemicalFormulaBucketItem(fluid, properties, "CH\u2083OH"),
            UnaryOperator.identity(), renderProperties -> renderProperties.tint(0xFFCDCDB2));
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, ChemicalFormulaBucketItem> ACETIC_ACID =
        FLUIDS.register("acetic_acid", (fluid, properties) -> new ChemicalFormulaBucketItem(fluid, properties, "CH\u2083COOH"),
            UnaryOperator.identity(), renderProperties -> renderProperties.tint(0xFFEEF0C7));

    // Liquid from gases
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, ChemicalFormulaBucketItem> LIQUID_NITROGEN =
        FLUIDS.register("liquid_nitrogen", (fluid, properties) -> new ChemicalFormulaBucketItem(fluid, properties, "N\u2082"),
            UnaryOperator.identity(), renderProperties -> renderProperties.tint(0xFF85CBEE));
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, BucketItem> LIQUID_AIR_REMNANT =
        FLUIDS.register("liquid_air_remnant", renderProperties -> renderProperties.tint(0xFFF7F7F7));

    // Molten fluids
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, ChemicalFormulaBucketItem> MOLTEN_IRON =
        FLUIDS.register("molten_iron", (fluid, properties) -> new ChemicalFormulaBucketItem(fluid, properties, "Fe"),
            UnaryOperator.identity(), renderProperties -> renderProperties.tint(0xFFCECECE));
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, ChemicalFormulaBucketItem> MOLTEN_COPPER =
        FLUIDS.register("molten_copper", (fluid, properties) -> new ChemicalFormulaBucketItem(fluid, properties, "Cu"),
            UnaryOperator.identity(), renderProperties -> renderProperties.tint(0xFFEBAD41));
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, ChemicalFormulaBucketItem> MOLTEN_TIN =
        FLUIDS.register("molten_tin", (fluid, properties) -> new ChemicalFormulaBucketItem(fluid, properties, "Sn"),
            UnaryOperator.identity(), renderProperties -> renderProperties.tint(0xFFC9CBDC));
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, ChemicalFormulaBucketItem> MOLTEN_BRONZE =
        FLUIDS.register("molten_bronze", (fluid, properties) -> new ChemicalFormulaBucketItem(fluid, properties, "Cu-Sn"),
            UnaryOperator.identity(), renderProperties -> renderProperties.tint(0xFFF9A648));
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, ChemicalFormulaBucketItem> MOLTEN_OSMIUM =
        FLUIDS.register("molten_osmium", (fluid, properties) -> new ChemicalFormulaBucketItem(fluid, properties, "Os"),
            UnaryOperator.identity(), renderProperties -> renderProperties.tint(0xFFC8CCF3));
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, ChemicalFormulaBucketItem> MOLTEN_THERMOENERGETIC_ALLOY =
        FLUIDS.register("molten_thermoenergetic_alloy", (fluid, properties) -> new ChemicalFormulaBucketItem(fluid, properties, "Fe-Cu"),
            UnaryOperator.identity(), renderProperties -> renderProperties.tint(0xFFFC7E11));
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, BucketItem> MOLTEN_INFUSED_ALLOY =
        FLUIDS.register("molten_infused_alloy", renderProperties -> renderProperties.tint(0xFFE64141));
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, BucketItem> MOLTEN_REINFORCED_ALLOY =
        FLUIDS.register("molten_reinforced_alloy", renderProperties -> renderProperties.tint(0xFF58D7F0));

    // Placeholder. We deliberately do not display its bucket item in creative tab.
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, BucketItem> DUMMY_LIQUID =
        FLUIDS.register("dummy_liquid", renderProperties -> renderProperties.tint(0xFF010101));
}
