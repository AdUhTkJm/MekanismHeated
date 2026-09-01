package io.aduhtkjm.mekanismheated;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

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
    }

    public static class Shaker {
        public static ModConfigSpec.IntValue BASE_SPEED;
        public static ModConfigSpec.LongValue ENERGY_PER_TICK;
        public static ModConfigSpec.LongValue MAX_ENERGY;
    }

    public static class Fractionation {
        public static ModConfigSpec.IntValue TOWER_MAX_HEIGHT;
        public static ModConfigSpec.IntValue FLUID_PER_LAYER;
        public static ModConfigSpec.DoubleValue HEAT_CAPACITY_PER_HEIGHT;
        public static ModConfigSpec.DoubleValue HEAT_DISSIPATION;
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
            .defineInRange("fullSpeedTemperature", 1_000D, 0D, Double.MAX_VALUE);
        HeatSmelter.BASE_TEMPERATURE = BUILDER
            .comment("Temperature in Kelvin below which the Heat Smelter cannot process recipes.")
            .defineInRange("baseTemperature", 300D, 0D, Double.MAX_VALUE);
        HeatSmelter.HEAT_CAPACITY = BUILDER
            .comment("Heat capacity of the Heat Smelter in J/K, controlling how quickly its temperature changes. Must be at least one.")
            .defineInRange("heatCapacity", 50D, 1D, Double.MAX_VALUE);
        HeatSmelter.INVERSE_CONDUCTION_COEFFICIENT = BUILDER
            .comment("Inverse conduction coefficient of the Heat Smelter, controlling how readily it exchanges heat with adjacent blocks (smaller means slower). Must be at least one.")
            .defineInRange("inverseConductionCoefficient", 5D, 1D, Double.MAX_VALUE);
        HeatSmelter.INVERSE_INSULATION_COEFFICIENT = BUILDER
            .comment("Inverse insulation coefficient of the Heat Smelter, controlling how readily it loses heat to the environment (smaller means slower). Must be at least one.")
            .defineInRange("inverseInsulationCoefficient", 3D, 1D, Double.MAX_VALUE);
        HeatSmelter.MAX_FUEL_TEMPERATURE = BUILDER
            .comment("Temperature in Kelvin at which the Heat Smelter stops burning fuel. Note the temperature can be raised by, e.g., resistive heaters beyond this point.")
            .defineInRange("maxFuelTemperature", 1_000D, 0D, Double.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("shaker");
        Shaker.BASE_SPEED = BUILDER
            .comment("Base number of game ticks the shaker takes to complete a recipe.")
            .defineInRange("baseSpeed", 200, 1, Integer.MAX_VALUE);
        Shaker.ENERGY_PER_TICK = BUILDER
            .comment("Energy consumed per tick.")
            .defineInRange("energyPerTick", 40, 0, Long.MAX_VALUE);
        Shaker.MAX_ENERGY = BUILDER
            .comment("Maximum amount of energy the shaker can hold.")
            .defineInRange("maxEnergy", 80000, 0, Long.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("fractionation");
        Fractionation.TOWER_MAX_HEIGHT = BUILDER
            .comment("The maximum height of fractionation tower.")
            .defineInRange("towerMaxHeight", 27, 4, 127);
        Fractionation.FLUID_PER_LAYER = BUILDER
            .comment("Fluid capacity in mB each interior block of height contributes to the feed sump or an output bank.")
            .defineInRange("fluidPerLayer", 10_000, 1, Integer.MAX_VALUE);
        Fractionation.HEAT_CAPACITY_PER_HEIGHT = BUILDER
            .comment("Heat capacity in J/K added per block of tower height. Must be at least one.")
            .defineInRange("heatCapacityPerHeight", 100D, 1D, Double.MAX_VALUE);
        Fractionation.HEAT_DISSIPATION = BUILDER
            .comment("Coefficient controlling how quickly the tower loses heat to the environment (larger means faster loss). Must be positive.")
            .defineInRange("heatDissipation", 1.0E-6D, 0D, Double.MAX_VALUE);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
    }
}
