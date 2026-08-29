package io.aduhtkjm.mekanismheated.content.fusedpipe;

import io.aduhtkjm.mekanismheated.Mod;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import mekanism.api.tier.BaseTier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Per-function configuration of a fused pipe. A function that has no tier stored is disabled.
 */
public final class FusedPipeConfig {

    public static final String TAG_CONFIG = "fused_config";

    private final Map<FusedFunction, BaseTier> tiers = new EnumMap<>(FusedFunction.class);

    /**
     * @return A config with every function enabled at basic tier.
     */
    public static FusedPipeConfig defaults() {
        FusedPipeConfig config = new FusedPipeConfig();
        for (FusedFunction function : FusedFunction.VALUES) {
            config.tiers.put(function, BaseTier.BASIC);
        }
        return config;
    }

    /**
     * @return The BLOCK_ENTITY_DATA component payload a fresh fused pipe item carries, which
     * vanilla applies to the placed block entity automatically.
     */
    public static CompoundTag createDefaultBlockEntityData() {
        return createBlockEntityData(List.of(FusedFunction.VALUES));
    }

    /**
     * @param enabled The functions to enable, each at basic tier.
     * @return A BLOCK_ENTITY_DATA payload carrying only the given functions, used by the fused
     * pipe recipe to encode which functions the crafted pipe supports.
     */
    public static CompoundTag createBlockEntityData(Iterable<FusedFunction> enabled) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", Mod.MODID + ":fused_pipe");
        CompoundTag configTag = new CompoundTag();
        for (FusedFunction function : enabled) {
            configTag.putString(function.getSerializedName(), BaseTier.BASIC.name());
        }
        tag.put(TAG_CONFIG, configTag);
        return tag;
    }

    public boolean isEnabled(FusedFunction function) {
        return tiers.containsKey(function);
    }

    /**
     * @return The highest tier among the enabled functions, or {@link BaseTier#BASIC} if no
     * function is enabled. The creative tier never occurs (it is clamped to ultimate on read).
     */
    public BaseTier displayTier() {
        BaseTier highest = BaseTier.BASIC;
        for (BaseTier tier : tiers.values()) {
            if (tier.ordinal() > highest.ordinal()) {
                highest = tier;
            }
        }
        return highest;
    }

    /**
     * Variant of {@link #displayTier()} for a raw config compound, e.g. read directly from an
     * item's BLOCK_ENTITY_DATA component where no registry provider is at hand.
     */
    public static BaseTier displayTier(CompoundTag tag) {
        BaseTier highest = BaseTier.BASIC;
        for (FusedFunction function : FusedFunction.VALUES) {
            String key = function.getSerializedName();
            if (tag.contains(key, Tag.TAG_STRING)) {
                BaseTier tier = parseTier(tag.getString(key));
                if (tier != null) {
                    //Creative tier has no transmitter equivalent; clamp it to ultimate
                    if (tier == BaseTier.CREATIVE) {
                        tier = BaseTier.ULTIMATE;
                    }
                    if (tier.ordinal() > highest.ordinal()) {
                        highest = tier;
                    }
                }
            }
        }
        return highest;
    }

    @Nullable
    public BaseTier getTier(FusedFunction function) {
        return tiers.get(function);
    }

    public void setTier(FusedFunction function, @Nullable BaseTier tier) {
        if (tier == null) {
            tiers.remove(function);
        } else {
            tiers.put(function, tier);
        }
    }

    public CompoundTag write(HolderLookup.Provider provider, CompoundTag tag) {
        for (Map.Entry<FusedFunction, BaseTier> entry : tiers.entrySet()) {
            tag.putString(entry.getKey().getSerializedName(), entry.getValue().name());
        }
        return tag;
    }

    public static FusedPipeConfig read(HolderLookup.Provider provider, CompoundTag tag) {
        FusedPipeConfig config = new FusedPipeConfig();
        for (FusedFunction function : FusedFunction.VALUES) {
            String key = function.getSerializedName();
            if (tag.contains(key, Tag.TAG_STRING)) {
                BaseTier tier = parseTier(tag.getString(key));
                if (tier != null) {
                    //Creative tier has no transmitter equivalent; clamp it to ultimate
                    config.tiers.put(function, tier == BaseTier.CREATIVE ? BaseTier.ULTIMATE : tier);
                }
            }
        }
        return config;
    }

    @Nullable
    private static BaseTier parseTier(String name) {
        for (BaseTier tier : BaseTier.values()) {
            if (tier.name().equals(name)) {
                return tier;
            }
        }
        return null;
    }

    @NotNull
    @Override
    public String toString() {
        return "FusedPipeConfig" + tiers;
    }
}
