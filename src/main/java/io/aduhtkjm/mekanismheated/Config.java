package io.aduhtkjm.mekanismheated;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = Mod.MODID)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue BASE_SPEED = BUILDER.comment(
          "Base number of game ticks the Heat Smelter takes to complete a recipe when running at full speed.")
          .defineInRange("baseSpeed", 100, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue FULL_SPEED_TEMPERATURE = BUILDER.comment(
          "Temperature in Kelvin the Heat Smelter must reach to process recipes at 100% base speed.")
          .defineInRange("fullSpeedTemperature", 1_000D, 0D, Double.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue BASE_TEMPERATURE = BUILDER.comment(
          "Temperature in Kelvin below which the Heat Smelter cannot process recipes (speed is clamped to zero).")
          .defineInRange("baseTemperature", 300D, 0D, Double.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue HEAT_CAPACITY = BUILDER.comment(
          "Heat capacity of the Heat Smelter in J/K, controlling how quickly its temperature changes. Must be at least one.")
          .defineInRange("heatCapacity", 100D, 1D, Double.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue INVERSE_CONDUCTION_COEFFICIENT = BUILDER.comment(
          "Inverse conduction coefficient of the Heat Smelter, controlling how readily it exchanges heat with adjacent blocks. Must be at least one.")
          .defineInRange("inverseConductionCoefficient", 5D, 1D, Double.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue INVERSE_INSULATION_COEFFICIENT = BUILDER.comment(
          "Inverse insulation coefficient of the Heat Smelter, controlling how readily it loses heat to the environment. Must be at least one.")
          .defineInRange("inverseInsulationCoefficient", 10D, 1D, Double.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue MAX_FUEL_TEMPERATURE = BUILDER.comment(
          "Temperature in Kelvin at which the Heat Smelter stops burning fuel, i.e. its maximum achievable temperature.")
          .defineInRange("maxFuelTemperature", 1_000D, 0D, Double.MAX_VALUE);

    static final ModConfigSpec SPEC = BUILDER.build();

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
    }
}
