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
}
