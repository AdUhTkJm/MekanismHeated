package io.aduhtkjm.mekanismheated.registries;

import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.content.unstablelava.UnstableLavaFluid;
import io.aduhtkjm.mekanismheated.content.unstablelava.UnstableLavaVariant;
import io.aduhtkjm.mekanismheated.item.ItemAsphaltBucket;
import io.aduhtkjm.mekanismheated.item.ItemChemicalFormulaBucket;
import io.aduhtkjm.mekanismheated.item.ItemFe2O3Dust;
import java.util.List;
import java.util.function.UnaryOperator;
import mekanism.common.registration.impl.FluidDeferredRegister;
import mekanism.common.registration.impl.FluidDeferredRegister.MekanismFluidType;
import mekanism.common.registration.impl.FluidRegistryObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid.Flowing;
import net.neoforged.neoforge.fluids.BaseFlowingFluid.Source;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

public class ModFluids {

    private ModFluids() {
    }

    public static final FluidDeferredRegister FLUIDS = new FluidDeferredRegister(Mod.MODID);

    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, BucketItem> WOOD_TAR =
        FLUIDS.register("wood_tar", renderProperties -> renderProperties.tint(0xFF513721));
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, ItemAsphaltBucket> ASPHALT =
        //Asphalt is a fluid, but pouring it out in the world sets a solid asphalt block, so its bucket is a
        //custom item rather than a plain BucketItem.
        FLUIDS.register("asphalt", ItemAsphaltBucket::new,
            UnaryOperator.identity(), renderProperties -> renderProperties.tint(0xFF234623));
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, ItemChemicalFormulaBucket> METHANOL =
        FLUIDS.register("methanol", (fluid, properties) -> new ItemChemicalFormulaBucket(fluid, properties, "CH\u2083OH"),
            UnaryOperator.identity(), renderProperties -> renderProperties.tint(0xFFCDCDB2));
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, ItemChemicalFormulaBucket> ACETIC_ACID =
        FLUIDS.register("acetic_acid", (fluid, properties) -> new ItemChemicalFormulaBucket(fluid, properties, "CH\u2083COOH"),
            UnaryOperator.identity(), renderProperties -> renderProperties.tint(0xFFEEF0C7));

    // Liquid from gases
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, ItemChemicalFormulaBucket> LIQUID_NITROGEN =
        FLUIDS.register("liquid_nitrogen", (fluid, properties) -> new ItemChemicalFormulaBucket(fluid, properties, "N\u2082"),
            UnaryOperator.identity(), renderProperties -> renderProperties.tint(0xFF85CBEE));
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, BucketItem> LIQUID_AIR_REMNANT =
        FLUIDS.register("liquid_air_remnant", renderProperties -> renderProperties.tint(0xFFF7F7F7));

    // Molten fluids
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, ItemChemicalFormulaBucket> MOLTEN_IRON =
        FLUIDS.register("molten_iron", (fluid, properties) -> new ItemChemicalFormulaBucket(fluid, properties, "Fe"),
            UnaryOperator.identity(), renderProperties -> renderProperties.tint(0xFFCECECE));
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, ItemChemicalFormulaBucket> MOLTEN_COPPER =
        FLUIDS.register("molten_copper", (fluid, properties) -> new ItemChemicalFormulaBucket(fluid, properties, "Cu"),
            UnaryOperator.identity(), renderProperties -> renderProperties.tint(0xFFEBAD41));
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, ItemChemicalFormulaBucket> MOLTEN_TIN =
        FLUIDS.register("molten_tin", (fluid, properties) -> new ItemChemicalFormulaBucket(fluid, properties, "Sn"),
            UnaryOperator.identity(), renderProperties -> renderProperties.tint(0xFFC9CBDC));
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, ItemChemicalFormulaBucket> MOLTEN_BRONZE =
        FLUIDS.register("molten_bronze", (fluid, properties) -> new ItemChemicalFormulaBucket(fluid, properties, "Cu-Sn"),
            UnaryOperator.identity(), renderProperties -> renderProperties.tint(0xFFF9A648));
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, ItemChemicalFormulaBucket> MOLTEN_OSMIUM =
        FLUIDS.register("molten_osmium", (fluid, properties) -> new ItemChemicalFormulaBucket(fluid, properties, "Os"),
            UnaryOperator.identity(), renderProperties -> renderProperties.tint(0xFFC8CCF3));
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, ItemChemicalFormulaBucket> MOLTEN_LEAD =
        FLUIDS.register("molten_lead", (fluid, properties) -> new ItemChemicalFormulaBucket(fluid, properties, "Pb"),
            UnaryOperator.identity(), renderProperties -> renderProperties.tint(0xFF807F7D));
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, ItemChemicalFormulaBucket> MOLTEN_THERMOENERGETIC_ALLOY =
        FLUIDS.register("molten_thermoenergetic_alloy", (fluid, properties) -> new ItemChemicalFormulaBucket(fluid, properties, "Fe-Cu"),
            UnaryOperator.identity(), renderProperties -> renderProperties.tint(0xFFFC7E11));
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, ItemChemicalFormulaBucket> MOLTEN_CASING_ALLOY =
        FLUIDS.register("molten_casing_alloy", (fluid, properties) -> new ItemChemicalFormulaBucket(fluid, properties, "Fe-Cu-Os"),
            UnaryOperator.identity(), renderProperties -> renderProperties.tint(0xFFF0973A));
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, BucketItem> MOLTEN_INFUSED_ALLOY =
        FLUIDS.register("molten_infused_alloy", renderProperties -> renderProperties.tint(0xFFE64141));
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, BucketItem> MOLTEN_REINFORCED_ALLOY =
        FLUIDS.register("molten_reinforced_alloy", renderProperties -> renderProperties.tint(0xFF58D7F0));
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, BucketItem> MOLTEN_ANCIENT_DEBRIS =
        FLUIDS.register("molten_ancient_debris", renderProperties -> renderProperties.tint(0xFF704F0C));

    public static final List<FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, ? extends BucketItem>> MOLTEN_FLUID_VARIANTS =
        List.of(MOLTEN_IRON, MOLTEN_COPPER, MOLTEN_TIN, MOLTEN_BRONZE, MOLTEN_OSMIUM, MOLTEN_LEAD,
            MOLTEN_THERMOENERGETIC_ALLOY, MOLTEN_CASING_ALLOY, MOLTEN_INFUSED_ALLOY, MOLTEN_REINFORCED_ALLOY,
            MOLTEN_ANCIENT_DEBRIS);

    // Slurry
    public static final FluidRegistryObject<MekanismFluidType, Source, Flowing, LiquidBlock, ItemChemicalFormulaBucket> SLURRY_FE2O3 =
        FLUIDS.register("slurry_fe2o3", (fluid, properties) -> new ItemChemicalFormulaBucket(fluid, properties, "Fe\u2082O\u2083"),
            UnaryOperator.identity(), renderProperties -> renderProperties.tint(ItemFe2O3Dust.TINT));

    //Unstable lava
    //Registered separately from the fluids above because Mekanism's FluidDeferredRegister only ever creates plain
    //BaseFlowingFluid instances with water-like flow settings, while unstable lava needs vanilla lava's flow and
    //fire-spreading behaviour. UnstableLavaVariant wraps that hand-rolled registration.
    public static final DeferredRegister<FluidType> UNSTABLE_LAVA_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Mod.MODID);
    public static final DeferredRegister<Fluid> UNSTABLE_LAVA_FLUID_REGISTER = DeferredRegister.create(Registries.FLUID, Mod.MODID);
    public static final DeferredRegister<Block> UNSTABLE_LAVA_BLOCK_REGISTER = DeferredRegister.create(Registries.BLOCK, Mod.MODID);
    public static final DeferredRegister<Item> UNSTABLE_LAVA_ITEM_REGISTER = DeferredRegister.create(Registries.ITEM, Mod.MODID);

    /** The registries every unstable lava variant registers into. */
    private static final UnstableLavaVariant.Registers UNSTABLE_LAVA_REGISTERS = new UnstableLavaVariant.Registers(
          UNSTABLE_LAVA_TYPES, UNSTABLE_LAVA_FLUID_REGISTER, UNSTABLE_LAVA_BLOCK_REGISTER, UNSTABLE_LAVA_ITEM_REGISTER);

    /** Plain unstable lava: the fluid ambient melting turns blocks into. */
    public static final UnstableLavaVariant UNSTABLE_LAVA =
          UnstableLavaVariant.register(UNSTABLE_LAVA_REGISTERS, "unstable_lava", 0xFFFF8C00);
    /** Unstable lava with different resources melted into it. */
    public static final UnstableLavaVariant UNSTABLE_LAVA_IRON =
        UnstableLavaVariant.register(UNSTABLE_LAVA_REGISTERS, "unstable_lava_iron", 0xFFE0A46B);
    public static final UnstableLavaVariant UNSTABLE_LAVA_COPPER =
        UnstableLavaVariant.register(UNSTABLE_LAVA_REGISTERS, "unstable_lava_copper", 0xFFFFA13B);
    public static final UnstableLavaVariant UNSTABLE_LAVA_TIN =
        UnstableLavaVariant.register(UNSTABLE_LAVA_REGISTERS, "unstable_lava_tin", 0xFFE0A46B);
    public static final UnstableLavaVariant UNSTABLE_LAVA_OSMIUM =
        UnstableLavaVariant.register(UNSTABLE_LAVA_REGISTERS, "unstable_lava_osmium", 0xFFFFA13B);
    public static final UnstableLavaVariant UNSTABLE_LAVA_GOLD =
        UnstableLavaVariant.register(UNSTABLE_LAVA_REGISTERS, "unstable_lava_gold", 0xFFFFA13B);
    public static final UnstableLavaVariant UNSTABLE_LAVA_LEAD =
        UnstableLavaVariant.register(UNSTABLE_LAVA_REGISTERS, "unstable_lava_lead", 0xFFFFA13B);

    /** Every unstable lava variant, in registration order. */
    public static final List<UnstableLavaVariant> UNSTABLE_LAVA_VARIANTS =
          List.of(UNSTABLE_LAVA, UNSTABLE_LAVA_IRON, UNSTABLE_LAVA_COPPER, UNSTABLE_LAVA_TIN,  UNSTABLE_LAVA_OSMIUM,
              UNSTABLE_LAVA_GOLD, UNSTABLE_LAVA_LEAD);

    /** Registers all four unstable lava registries with the mod event bus. */
    public static void registerUnstableLavaRegisters(IEventBus bus) {
        UNSTABLE_LAVA_REGISTERS.register(bus);
    }

    /**
     * Registers the dispense behaviour for every unstable lava bucket. {@link FluidDeferredRegister} does this for
     * the fluids it manages, but unstable lava is registered by hand, so it needs its own copy of vanilla's bucket
     * dispensing logic.
     */
    public static void registerUnstableLavaDispenserBehavior() {
        UNSTABLE_LAVA_VARIANTS.forEach(UnstableLavaVariant::registerDispenserBehavior);
    }
}
