package io.aduhtkjm.mekanismheated.content.fusedpipe;

import net.minecraft.util.StringRepresentable;
import org.lwjgl.system.NonnullDefault;

/**
 * The transmission functions a fused pipe can provide. Each function has its own tier in
 * {@link FusedPipeConfig}; a function without a tier is fully disabled.
 */
@NonnullDefault
public enum FusedFunction implements StringRepresentable {
    ENERGY,
    FLUID,
    CHEMICAL,
    HEAT,
    ITEM;

    public static final FusedFunction[] VALUES = values();

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
