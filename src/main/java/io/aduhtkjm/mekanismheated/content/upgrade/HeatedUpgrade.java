package io.aduhtkjm.mekanismheated.content.upgrade;

import mekanism.api.Upgrade;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the heat upgrades this mod adds to Mekanism's {@link Upgrade} enum.
 *
 * <p>{@code Upgrade} is an ordinary (if closed) enum, so an addon can extend it: {@code MixinUpgrade} appends these
 * constants to its {@code $VALUES} during the enum's static initializer and rebuilds the enum's codecs afterwards. From
 * that point on every heat upgrade <em>is</em> a {@link Upgrade}, so Mekanism's own upgrade slot, install progress, NBT
 * serialization, container sync, upgrade window and item transport all handle them with no further help.</p>
 *
 * <p>The fields are assigned by {@code MixinUpgrade}; never write to them from anywhere else. They are mutable fields
 * rather than an enum of this mod's own because the constants do not exist until Mekanism's enum is initialised.</p>
 *
 * @see HeatedUpgrades
 */
public final class HeatedUpgrade {

    private HeatedUpgrade() {
    }

    /**
     * More of a machine's heat is exchanged with adjacent blocks: the inverse conduction coefficient is divided by
     * {@code (1 + bonus)^count}.
     */
    public static Upgrade CONDUCTION;
    /**
     * Less heat is lost to the environment: the inverse insulation coefficient is multiplied by {@code (1 + bonus)^count}.
     */
    public static Upgrade INSULATION;
    /**
     * The machine can hold more heat: the heat capacity is multiplied by {@code (1 + bonus)^count}.
     */
    public static Upgrade CAPACITY;

    /**
     * All heat upgrades, in the order they were injected. Assigned by {@code MixinUpgrade} next to the fields above.
     *
     * <p>DO NOT MODIFY THIS ARRAY.</p>
     */
    public static Upgrade[] HEAT_UPGRADES;

    /**
     * Checks whether the given upgrade is one of this mod's heat upgrades.
     */
    public static boolean isHeatUpgrade(@Nullable Upgrade upgrade) {
        return upgrade != null && (upgrade == CONDUCTION || upgrade == INSULATION || upgrade == CAPACITY);
    }
}
