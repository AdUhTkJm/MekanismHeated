package io.aduhtkjm.mekanismheated;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import io.aduhtkjm.mekanismheated.content.ambient.AmbientMeltingHandler;
import io.aduhtkjm.mekanismheated.content.ambient.BlockMeltFilter;
import java.util.List;

@EventBusSubscriber(modid = Mod.MODID)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static class HeatSmelter {
        public static ModConfigSpec.IntValue BASE_SPEED;
        public static ModConfigSpec.IntValue FLUID_CAPACITY;
        public static ModConfigSpec.DoubleValue FULL_SPEED_TEMPERATURE;
        public static ModConfigSpec.DoubleValue BASE_TEMPERATURE;
        public static ModConfigSpec.DoubleValue HEAT_CAPACITY;
        public static ModConfigSpec.DoubleValue INVERSE_CONDUCTION_COEFFICIENT;
        public static ModConfigSpec.DoubleValue INVERSE_INSULATION_COEFFICIENT;
        public static ModConfigSpec.DoubleValue MAX_FUEL_TEMPERATURE;
        public static ModConfigSpec.DoubleValue HEAT_PER_SMELT;
        public static ModConfigSpec.DoubleValue MAX_HEAT_MULTIPLIER;
    }

    public static class Shaker {
        public static ModConfigSpec.IntValue BASE_SPEED;
        public static ModConfigSpec.LongValue ENERGY_PER_TICK;
        public static ModConfigSpec.LongValue MAX_ENERGY;
    }

    public static class Condenser {
        public static ModConfigSpec.IntValue BASE_SPEED;
        public static ModConfigSpec.IntValue FLUID_CAPACITY;
        public static ModConfigSpec.DoubleValue MAX_TEMPERATURE;
        public static ModConfigSpec.DoubleValue FULL_SPEED_TEMPERATURE;
        public static ModConfigSpec.DoubleValue HEAT_CAPACITY;
        public static ModConfigSpec.DoubleValue INVERSE_CONDUCTION_COEFFICIENT;
        public static ModConfigSpec.DoubleValue INVERSE_INSULATION_COEFFICIENT;
    }

    public static class QuenchingEnrichmentChamber {
        public static ModConfigSpec.IntValue PROCESSING_TIME;
        public static ModConfigSpec.LongValue ENERGY_PER_TICK;
        public static ModConfigSpec.LongValue MAX_ENERGY;
        public static ModConfigSpec.IntValue INPUT_FLUID_CAPACITY;
        public static ModConfigSpec.IntValue OUTPUT_FLUID_CAPACITY;
    }

    public static class Fractionation {
        public static ModConfigSpec.IntValue TOWER_MAX_HEIGHT;
        public static ModConfigSpec.IntValue FLUID_PER_LAYER;
        public static ModConfigSpec.DoubleValue HEAT_CAPACITY_PER_HEIGHT;
        public static ModConfigSpec.DoubleValue HEAT_DISSIPATION;
    }

    public static class FusedNetwork {
        public static ModConfigSpec.IntValue ITEM_PULL_INTERVAL;
        public static ModConfigSpec.IntValue CHEMICAL_PULL_INTERVAL;
        public static ModConfigSpec.IntValue FLUID_PULL_INTERVAL;
        public static ModConfigSpec.IntValue ENERGY_PULL_INTERVAL;
        public static ModConfigSpec.IntValue HEAT_SIM_INTERVAL;
    }

    public static class Cooler {
        public static ModConfigSpec.LongValue MAX_ENERGY;
        public static ModConfigSpec.DoubleValue EFFICIENCY;
        public static ModConfigSpec.DoubleValue HEAT_CAPACITY;
        public static ModConfigSpec.DoubleValue INVERSE_CONDUCTION_COEFFICIENT;
        public static ModConfigSpec.DoubleValue INVERSE_INSULATION_COEFFICIENT;
    }

    public static class ReactionChamber {
        public static ModConfigSpec.IntValue DEFAULT_DURATION;
        public static ModConfigSpec.IntValue CAPACITY;
        public static ModConfigSpec.DoubleValue HEAT_CAPACITY;
        public static ModConfigSpec.DoubleValue INVERSE_CONDUCTION_COEFFICIENT;
        public static ModConfigSpec.DoubleValue INVERSE_INSULATION_COEFFICIENT;
    }

    public static class AtmosphereHeater {
        public static ModConfigSpec.IntValue WORK_INTERVAL;
        public static ModConfigSpec.LongValue ENERGY_PER_TICK;
        public static ModConfigSpec.LongValue MAX_ENERGY;
        public static ModConfigSpec.DoubleValue BASE_TEMP_RISE;
        public static ModConfigSpec.DoubleValue TEMPERATURE_SCALE;
        public static ModConfigSpec.DoubleValue CENTER_EFFECT;
        public static ModConfigSpec.DoubleValue OUTER_EFFECT;
        public static ModConfigSpec.IntValue CHUNK_RADIUS;
    }

    public static class PhaseChange {
        public static ModConfigSpec.DoubleValue LOW_MELTING_POINT;
        public static ModConfigSpec.DoubleValue MEDIUM_MELTING_POINT;
        public static ModConfigSpec.DoubleValue HIGH_MELTING_POINT;
        public static ModConfigSpec.DoubleValue BUFFER_CAPACITY;
        public static ModConfigSpec.DoubleValue HEAT_CAPACITY;
        public static ModConfigSpec.DoubleValue INVERSE_CONDUCTION_COEFFICIENT;
        public static ModConfigSpec.DoubleValue INVERSE_INSULATION_COEFFICIENT;
    }

    public static class AmbientMelting {
        public static ModConfigSpec.EnumValue<BlockMeltFilter.Mode> MODE;
        public static ModConfigSpec.ConfigValue<List<? extends String>> BLOCKS;
        public static ModConfigSpec.DoubleValue MELT_THRESHOLD;
        public static ModConfigSpec.IntValue SOURCE_INTERVAL;
        public static ModConfigSpec.IntValue SOURCE_SAMPLES;
        public static ModConfigSpec.IntValue MELT_INTERVAL;
        public static ModConfigSpec.IntValue MELT_SAMPLES;
    }

    public static class TemperatureController {
        public static ModConfigSpec.LongValue MAX_ENERGY_OUTPUT;
        public static ModConfigSpec.DoubleValue DISPLAY_MIN_TEMPERATURE;
        public static ModConfigSpec.DoubleValue DISPLAY_MAX_TEMPERATURE;
        public static ModConfigSpec.IntValue MAX_EXPRESSION_LENGTH;
        public static ModConfigSpec.IntValue INTERVAL;
    }

    public static class RetroentropicArray {
        public static ModConfigSpec.DoubleValue HEAT_CAPACITY;
        public static ModConfigSpec.DoubleValue INVERSE_CONDUCTION_COEFFICIENT;
        public static ModConfigSpec.DoubleValue INVERSE_INSULATION_COEFFICIENT;
    }

    private static String heatCapacity(String name) {
        return String.format("Heat capacity of the %s in J/K, controlling how quickly its temperature changes.", name);
    }

    private static String invCdt(String name) {
        return String.format("Inverse conduction coefficient of the %s, controlling how readily it exchanges heat with adjacent blocks.", name);
    }

    private static String invIns(String name) {
        return String.format("Inverse insulation coefficient of the %s, controlling how readily it loses heat to the environment.", name);
    }

    private static String maxEnergy(String name) {
        return String.format("Max energy in Joules that the %s can hold,", name);
    }

    public static ModConfigSpec SPEC;
    static {
        BUILDER.push("heatSmelter");
        HeatSmelter.BASE_SPEED = BUILDER
            .comment("Base number of game ticks the heat smelter takes to complete a recipe when running at full speed.")
            .defineInRange("baseSpeed", 100, 1, Integer.MAX_VALUE);
        HeatSmelter.FLUID_CAPACITY = BUILDER
            .comment("The capacity of the output fluid buffer in heat smelter, in buckets.")
            .defineInRange("fluidCapacity", 36, 1, Integer.MAX_VALUE);
        HeatSmelter.FULL_SPEED_TEMPERATURE = BUILDER
            .comment("Temperature in Kelvin the Heat Smelter must reach to process recipes at 100% base speed.")
            .defineInRange("fullSpeedTemperature", 1000, 0, Double.MAX_VALUE);
        HeatSmelter.BASE_TEMPERATURE = BUILDER
            .comment("Temperature in Kelvin below which the Heat Smelter cannot process recipes.")
            .defineInRange("baseTemperature", 300, 0, Double.MAX_VALUE);
        HeatSmelter.HEAT_CAPACITY = BUILDER
            .comment(heatCapacity("Heat Smelter"))
            .defineInRange("heatCapacity", 50, 1, Double.MAX_VALUE);
        HeatSmelter.INVERSE_CONDUCTION_COEFFICIENT = BUILDER
            .comment(invCdt("Heat Smelter"))
            .defineInRange("inverseConductionCoefficient", 5, 1, Double.MAX_VALUE);
        HeatSmelter.INVERSE_INSULATION_COEFFICIENT = BUILDER
            .comment(invIns("Heat Smelter"))
            .defineInRange("inverseInsulationCoefficient", 3, 1, Double.MAX_VALUE);
        HeatSmelter.MAX_FUEL_TEMPERATURE = BUILDER
            .comment("Temperature in Kelvin at which the Heat Smelter stops burning fuel. Note the temperature can be raised by, e.g., resistive heaters beyond this point.")
            .defineInRange("maxFuelTemperature", 1000, 0, Double.MAX_VALUE);
        HeatSmelter.HEAT_PER_SMELT = BUILDER
            .comment("Total heat consumed by the Heat Smelter per plain smelting recipe (spread over the recipe's processing ticks). Heated smelting and melting recipes can override their heat cost via their recipe's optional \"heat\" field; omitted values fall back to this.")
            .defineInRange("heatPerSmelt", 120, 0, Double.MAX_VALUE);
        HeatSmelter.MAX_HEAT_MULTIPLIER = BUILDER
            .comment("Cap on the large heat smelter's parallel processing heat multiplier. A batch of operations costs sqrt(operationCount)x one operation's heat (e.g. 8 operations cost sqrt(8)x, 64 operations cost 8x), making a large smelter more heat-efficient than the equivalent number of separate smelters. This caps the multiplier: 8 corresponds to sqrt(64), so structures larger than 64 blocks stop paying more heat while still processing everything at once.")
            .defineInRange("maxHeatMultiplier", 8, 1, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("shaker");
        Shaker.BASE_SPEED = BUILDER
            .comment("Base number of game ticks the shaker takes to complete a recipe.")
            .defineInRange("baseSpeed", 200, 1, Integer.MAX_VALUE);
        Shaker.ENERGY_PER_TICK = BUILDER
            .comment("Energy consumed per tick.")
            .defineInRange("energyPerTick", 40, 0, Long.MAX_VALUE);
        Shaker.MAX_ENERGY = BUILDER
            .comment(maxEnergy("Shaker"))
            .defineInRange("maxEnergy", 80000, 0, Long.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("condenser");
        Condenser.BASE_SPEED = BUILDER
            .comment("Base number of game ticks the condenser takes to complete a recipe when running at full speed (coldest).")
            .defineInRange("baseSpeed", 200, 1, Integer.MAX_VALUE);
        Condenser.FLUID_CAPACITY = BUILDER
            .comment("The capacity of the input fluid buffer in the condenser, in buckets.")
            .defineInRange("fluidCapacity", 10, 1, Integer.MAX_VALUE);
        Condenser.MAX_TEMPERATURE = BUILDER
            .comment("Temperature in Kelvin at which the Condenser cannot process recipes (0% speed).")
            .defineInRange("maxTemperature", 500, 0, Double.MAX_VALUE);
        Condenser.FULL_SPEED_TEMPERATURE = BUILDER
            .comment("Temperature in Kelvin at which the Condenser processes recipes at 100% base speed.")
            .defineInRange("fullSpeedTemperature", 100, 0, Double.MAX_VALUE);
        Condenser.HEAT_CAPACITY = BUILDER
            .comment(heatCapacity("Condenser"))
            .defineInRange("heatCapacity", 50, 1, Double.MAX_VALUE);
        Condenser.INVERSE_CONDUCTION_COEFFICIENT = BUILDER
            .comment(invCdt("Condenser"))
            .defineInRange("inverseConductionCoefficient", 5, 1, Double.MAX_VALUE);
        Condenser.INVERSE_INSULATION_COEFFICIENT = BUILDER
            .comment(invIns("Condenser"))
            .defineInRange("inverseInsulationCoefficient", 5, 1, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("quenchingEnrichmentChamber");
        QuenchingEnrichmentChamber.PROCESSING_TIME = BUILDER
            .comment("Base number of game ticks the Quenching Enrichment Chamber takes to complete a recipe, before speed upgrades.")
            .defineInRange("processingTime", 200, 1, Integer.MAX_VALUE);
        QuenchingEnrichmentChamber.ENERGY_PER_TICK = BUILDER
            .comment("Energy in Joules the Quenching Enrichment Chamber consumes per tick while processing a recipe. Each operation therefore costs energyPerTick * processingTime Joules.")
            .defineInRange("energyPerTick", 375, 0, Long.MAX_VALUE);
        QuenchingEnrichmentChamber.MAX_ENERGY = BUILDER
            .comment(maxEnergy("Quenching Enrichment Chamber"))
            .defineInRange("maxEnergy", 150_000, 0, Long.MAX_VALUE);
        QuenchingEnrichmentChamber.INPUT_FLUID_CAPACITY = BUILDER
            .comment("The capacity of the Quenching Enrichment Chamber's input fluid buffer, in buckets.")
            .defineInRange("inputFluidCapacity", 10, 1, Integer.MAX_VALUE);
        QuenchingEnrichmentChamber.OUTPUT_FLUID_CAPACITY = BUILDER
            .comment("The capacity of the Quenching Enrichment Chamber's output fluid buffer, in buckets.")
            .defineInRange("outputFluidCapacity", 10, 1, Integer.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("fractionation");
        Fractionation.TOWER_MAX_HEIGHT = BUILDER
            .comment("The maximum height of fractionation tower.")
            .defineInRange("towerMaxHeight", 27, 4, 127);
        Fractionation.FLUID_PER_LAYER = BUILDER
            .comment("Fluid capacity in mB each interior block of height contributes to the feed sump or an output bank.")
            .defineInRange("fluidPerLayer", 10_000, 1, Integer.MAX_VALUE);
        Fractionation.HEAT_CAPACITY_PER_HEIGHT = BUILDER
            .comment("Heat capacity in J/K added per block of tower height.")
            .defineInRange("heatCapacityPerHeight", 100, 1, Double.MAX_VALUE);
        Fractionation.HEAT_DISSIPATION = BUILDER
            .comment("Coefficient controlling how quickly the tower loses heat to the environment (larger means faster loss). Must be positive.")
            .defineInRange("heatDissipation", 1.0E-6, 0, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("fusedNetwork");
        FusedNetwork.ITEM_PULL_INTERVAL = BUILDER
            .comment("How often (in ticks) the fused network pulls items from pull sides. The pulled amount is scaled by this interval to keep the average rate constant. Pushing always happens every tick.")
            .defineInRange("itemPullInterval", 10, 1, Integer.MAX_VALUE);
        FusedNetwork.CHEMICAL_PULL_INTERVAL = BUILDER
            .comment("How often (in ticks) the fused network pulls chemicals from pull sides. The pulled amount is scaled by this interval to keep the average rate constant. Pushing always happens every tick.")
            .defineInRange("chemicalPullInterval", 2, 1, Integer.MAX_VALUE);
        FusedNetwork.FLUID_PULL_INTERVAL = BUILDER
            .comment("How often (in ticks) the fused network pulls fluids from pull sides. The pulled amount is scaled by this interval to keep the average rate constant. Pushing always happens every tick.")
            .defineInRange("fluidPullInterval", 2, 1, Integer.MAX_VALUE);
        FusedNetwork.ENERGY_PULL_INTERVAL = BUILDER
            .comment("How often (in ticks) the fused network pulls energy from pull sides. The pulled amount is scaled by this interval to keep the average rate constant. Pushing always happens every tick.")
            .defineInRange("energyPullInterval", 2, 1, Integer.MAX_VALUE);
        FusedNetwork.HEAT_SIM_INTERVAL = BUILDER
            .comment("How often (in ticks) the fused network runs its heat simulation. Heat transfers are scaled by this interval to keep the average rate constant.")
            .defineInRange("heatSimInterval", 1, 1, Integer.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("cooler");
        Cooler.MAX_ENERGY = BUILDER
            .comment("Maximum amount of energy the cooler can hold in Joules.")
            .defineInRange("maxenergy", 1_000_000, 1, Long.MAX_VALUE);
        Cooler.EFFICIENCY = BUILDER
            .comment("Heat pump coefficient of performance (COP). Heat moved per joule of energy consumed.")
            .defineInRange("efficiency", 2.0, 0, Double.MAX_VALUE);
        Cooler.HEAT_CAPACITY = BUILDER
            .comment(heatCapacity("Cooler"))
            .defineInRange("heatCapacity", 100, 1, Double.MAX_VALUE);
        Cooler.INVERSE_CONDUCTION_COEFFICIENT = BUILDER
            .comment(invCdt("the Cooler"))
            .defineInRange("inverseConductionCoefficient", 5, 1, Double.MAX_VALUE);
        Cooler.INVERSE_INSULATION_COEFFICIENT = BUILDER
            .comment(invIns("the Cooler"))
            .defineInRange("inverseInsulationCoefficient", 10, 1, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("reactionChamber");
        ReactionChamber.DEFAULT_DURATION = BUILDER
            .comment("How many game ticks the reaction chamber waits between two operations of the same reaction recipe, for recipes that do not declare a \"duration\" of their own. A recipe reacts as soon as its inputs are available (a content change ticks the chamber immediately) and then goes on this cooldown. Because the fallback is applied when recipes are parsed, changing it only affects recipes that omit the field, and only after reloading datapacks.")
            .defineInRange("defaultDuration", 10, 1, Integer.MAX_VALUE);
        ReactionChamber.CAPACITY = BUILDER
            .comment("The total capacity of the reaction chamber's mixed fluid/chemical buffer, in buckets. Fluids and chemicals share this pool.")
            .defineInRange("capacity", 16, 1, Integer.MAX_VALUE);
        ReactionChamber.HEAT_CAPACITY = BUILDER
            .comment(heatCapacity("Reaction Chamber"))
            .defineInRange("heatCapacity", 100, 1, Double.MAX_VALUE);
        ReactionChamber.INVERSE_CONDUCTION_COEFFICIENT = BUILDER
            .comment(invCdt("Reaction Chamber"))
            .defineInRange("inverseConductionCoefficient", 5, 1, Double.MAX_VALUE);
        ReactionChamber.INVERSE_INSULATION_COEFFICIENT = BUILDER
            .comment(invIns("Reaction Chamber"))
            .defineInRange("inverseInsulationCoefficient", 5, 1, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("atmosphereHeater");
        AtmosphereHeater.WORK_INTERVAL = BUILDER
            .comment("How often (in game ticks) the atmosphere heater performs a work cycle: it consumes fuel and energy and then warms the surrounding chunks.")
            .defineInRange("workInterval", 40, 1, Integer.MAX_VALUE);
        AtmosphereHeater.ENERGY_PER_TICK = BUILDER
            .comment("Base energy consumed per tick of a work cycle in Joules. Fuel inputs subtract from this per work cycle; the consumption never goes below zero, and any fuel reducing it beyond zero is still consumed in full.")
            .defineInRange("energyPerTick", 50_000, 0, Long.MAX_VALUE);
        AtmosphereHeater.MAX_ENERGY = BUILDER
            .comment("Maximum amount of energy the atmosphere heater can hold in Joules.")
            .defineInRange("maxEnergy", 1_000_000, 0, Long.MAX_VALUE);
        AtmosphereHeater.BASE_TEMP_RISE = BUILDER
            .comment("Ambient temperature rise in Kelvin per work cycle at 0 K ambient, following dT = baseTempRise / 2^(T / temperatureScale) where T is the current effective ambient temperature in Kelvin.")
            .defineInRange("baseTempRise", 5, 0, Double.MAX_VALUE);
        AtmosphereHeater.TEMPERATURE_SCALE = BUILDER
            .comment("Temperature scale (Kelvin) in the denominator of the heater's dT formula: larger values make the temperature rise fall off more slowly as the ambient temperature climbs. Must be greater than zero.")
            .defineInRange("temperatureScale", 1_000, 1.0E-9, Double.MAX_VALUE);
        AtmosphereHeater.CENTER_EFFECT = BUILDER
            .comment("Fraction of the temperature rise applied to the chunk containing the machine (1.0 = 100%).")
            .defineInRange("centerEffect", 1.0, 0, 1);
        AtmosphereHeater.OUTER_EFFECT = BUILDER
            .comment("Fraction of the temperature rise applied to each chunk surrounding the machine's chunk (0.5 = 50%).")
            .defineInRange("outerEffect", 0.5, 0, 1);
        AtmosphereHeater.CHUNK_RADIUS = BUILDER
            .comment("Radius in chunks around the machine's own chunk that receive the outer effect. 1 corresponds to a 3x3 chunk area.")
            .defineInRange("chunkRadius", 1, 0, 32);
        BUILDER.pop();

        BUILDER.push("phaseChange");
        PhaseChange.LOW_MELTING_POINT = BUILDER
            .comment("Melting point in Kelvin of the low-temperature phase-change block. Below it the block is an ordinary heat capacitor; at it the block absorbs heat into its latent heat buffer without warming up.")
            .defineInRange("lowMeltingPoint", 1_000, 0, Double.MAX_VALUE);
        PhaseChange.MEDIUM_MELTING_POINT = BUILDER
            .comment("Melting point in Kelvin of the medium-temperature phase-change block.")
            .defineInRange("mediumMeltingPoint", 1_750, 0, Double.MAX_VALUE);
        PhaseChange.HIGH_MELTING_POINT = BUILDER
            .comment("Melting point in Kelvin of the high-temperature phase-change block.")
            .defineInRange("highMeltingPoint", 3_000, 0, Double.MAX_VALUE);
        PhaseChange.BUFFER_CAPACITY = BUILDER
            .comment("Latent heat in Joules every phase-change block can absorb at its melting point before its temperature starts rising again. Shared by all three tiers.")
            .defineInRange("bufferCapacity", 1_000_000, 0, Double.MAX_VALUE);
        PhaseChange.HEAT_CAPACITY = BUILDER
            .comment("Heat capacity of the phase-change blocks in J/K, controlling how quickly their temperature changes. Shared by all three tiers.")
            .defineInRange("heatCapacity", 100, 1, Double.MAX_VALUE);
        PhaseChange.INVERSE_CONDUCTION_COEFFICIENT = BUILDER
            .comment("Inverse conduction coefficient of the phase-change blocks, controlling how readily they exchange heat with adjacent blocks. Shared by all three tiers.")
            .defineInRange("inverseConductionCoefficient", 5, 1, Double.MAX_VALUE);
        PhaseChange.INVERSE_INSULATION_COEFFICIENT = BUILDER
            .comment("Inverse insulation coefficient of the phase-change blocks, controlling how readily they lose heat to the environment. Shared by all three tiers.")
            .defineInRange("inverseInsulationCoefficient", 5, 1, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("ambientMelting");
        AmbientMelting.MODE = BUILDER
            .comment("How the blocks list is interpreted. WHITELIST: only the listed blocks melt (default, with \"minecraft\" listed so only vanilla blocks melt). BLACKLIST: every block except the listed ones melts.")
            .defineEnum("mode", BlockMeltFilter.Mode.WHITELIST);
        AmbientMelting.BLOCKS = BUILDER
            .comment("Blocks that the mode applies to. Each entry is either a namespace (e.g. \"minecraft\", matching every block in that namespace) or the qualified id of a single block (e.g. \"minecraft:stone\"). Air is always rejected regardless of this list.")
            .defineListAllowEmpty("blocks", List.of("minecraft"), () -> "minecraft:stone", BlockMeltFilter::isValidEntry);
        AmbientMelting.MELT_THRESHOLD = BUILDER
            .comment("Ambient temperature in Kelvin above which a chunk's blocks start melting into unstable lava.")
            .defineInRange("meltThreshold", 1_800, 0, Double.MAX_VALUE);
        AmbientMelting.SOURCE_INTERVAL = BUILDER
            .comment("How often (in game ticks) every ticking chunk is sampled to turn the block below unstable lava back into another unstable lava source, essentially cascading the melting process.")
            .defineInRange("sourceInterval", 5, 1, Integer.MAX_VALUE);
        AmbientMelting.SOURCE_SAMPLES = BUILDER
            .comment("How many random positions per chunk are checked on each source pass.")
            .defineInRange("sourceSamples", 15, 0, Integer.MAX_VALUE);
        AmbientMelting.MELT_INTERVAL = BUILDER
            .comment("How often (in game ticks) every ticking chunk above the melt threshold is sampled to melt one of its blocks into unstable lava.")
            .defineInRange("meltInterval", 20, 1, Integer.MAX_VALUE);
        AmbientMelting.MELT_SAMPLES = BUILDER
            .comment("How many random positions per hot chunk are checked on each melt pass.")
            .defineInRange("meltSamples", 1, 0, Integer.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("temperatureController");
        TemperatureController.MAX_ENERGY_OUTPUT = BUILDER
            .comment("Max Joules that Temperature Controller writes to adjacent coolers and resistive heaters in energy output mode.")
            .defineInRange("maxEnergyOutput", 1_000_000, 1, Long.MAX_VALUE);
        TemperatureController.DISPLAY_MIN_TEMPERATURE = BUILDER
            .comment("Ambient temperature in Kelvin at or below which the Temperature Controller's front window is entirely unlit.")
            .defineInRange("displayMinTemperature", 0, 0, Double.MAX_VALUE);
        TemperatureController.DISPLAY_MAX_TEMPERATURE = BUILDER
            .comment("Ambient temperature in Kelvin at which every row of the Temperature Controller's front window is lit. Must be above displayMinTemperature for the window to light up at all.")
            .defineInRange("displayMaxTemperature", 1_800, 0, Double.MAX_VALUE);
        TemperatureController.MAX_EXPRESSION_LENGTH = BUILDER
            .comment("Maximum number of characters the Temperature Controller accepts for its expression, both in the GUI text field and on the server.")
            .defineInRange("maxExpressionLength", 256, 1, 1_024);
        TemperatureController.INTERVAL = BUILDER
            .comment("The ticks between two output updates of the temperature controller.")
            .defineInRange("interval", 20, 1, Integer.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("retroentropicArray");
        RetroentropicArray.HEAT_CAPACITY = BUILDER
            .comment(heatCapacity("Retroentropic Array"))
            .defineInRange("heatCapacity", 100, 1, Double.MAX_VALUE);
        RetroentropicArray.INVERSE_CONDUCTION_COEFFICIENT = BUILDER
            .comment(invCdt("Retroentropic Array"))
            .defineInRange("inverseConductionCoefficient", 5, 1, Double.MAX_VALUE);
        RetroentropicArray.INVERSE_INSULATION_COEFFICIENT = BUILDER
            .comment(invIns("Retroentropic Array"))
            .defineInRange("inverseInsulationCoefficient", 5, 1, Double.MAX_VALUE);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        //Recompile the melt filter so config edits take effect without a restart.
        AmbientMeltingHandler.onConfigReload();
    }
}
