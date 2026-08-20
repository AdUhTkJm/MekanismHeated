package io.aduhtkjm.mekanismheated;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = MekanismHeated.MODID)
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

    static final ModConfigSpec SPEC = BUILDER.build();

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
    }
}
