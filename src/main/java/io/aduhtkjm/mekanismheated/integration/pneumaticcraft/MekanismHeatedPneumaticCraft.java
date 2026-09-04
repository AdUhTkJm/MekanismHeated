package io.aduhtkjm.mekanismheated.integration.pneumaticcraft;

import net.neoforged.fml.ModList;

/**
 * Entry point for the optional PneumaticCraft: Repressurized integration.
 *
 * <p>Mirrors the role of {@code MekanismHeatedJEI} and {@code MekanismHeatedJadePlugin} as the single
 * home for code wiring MekanismHeated into PneumaticCraft. Unlike JEI/Jade, PC:Re exposes no
 * auto-discovered plugin interface, so anything added here must be guarded with
 * {@link #isLoaded()} (or wired through an event that only fires while the mod is present) to keep
 * the dependency optional.
 */
public final class MekanismHeatedPneumaticCraft {
    public static final String MOD_ID = "pneumaticcraft";

    private MekanismHeatedPneumaticCraft() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
