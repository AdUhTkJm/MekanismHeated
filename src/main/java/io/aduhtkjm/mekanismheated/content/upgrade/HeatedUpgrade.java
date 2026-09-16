package io.aduhtkjm.mekanismheated.content.upgrade;

import com.mojang.serialization.Codec;
import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.ModLang;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import mekanism.api.text.EnumColor;
import mekanism.api.text.IHasTranslationKey;
import mekanism.api.text.ILangEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import org.lwjgl.system.NonnullDefault;

/**
 * The heat upgrades added by this mod. They are deliberately <em>not</em> part of Mekanism's {@link mekanism.api.Upgrade}
 * enum: that enum is closed, so an addon cannot add constants to it (and its upgrade window is hard-typed to it). Instead
 * this enum mirrors the parts of {@code Upgrade} the machine code needs, and the machines of this mod accept both kinds.
 *
 * <p>Every installed upgrade multiplies (or, for {@link #CONDUCTION}, divides) its capacitor value by
 * {@code (1 + bonus)^count} where {@code bonus} is the per-upgrade config value (10% by default), so the upgrades stack
 * multiplicatively up to {@link #MAX} installed per machine.</p>
 *
 * <p>Only machines that implement {@link IHeatedUpgradeTile} and carry Mekanism's upgrade component accept these: in
 * practice the mod's own machines with a heat capacitor. Mekanism's own machines are unaffected.</p>
 */
@NonnullDefault
public enum HeatedUpgrade implements IHasTranslationKey.IHasEnumNameTranslationKey, StringRepresentable {
    CONDUCTION("conduction", ModLang.UPGRADE_CONDUCTION, ModLang.UPGRADE_CONDUCTION_DESCRIPTION, EnumColor.ORANGE),
    INSULATION("insulation", ModLang.UPGRADE_INSULATION, ModLang.UPGRADE_INSULATION_DESCRIPTION, EnumColor.INDIGO),
    CAPACITY("capacity", ModLang.UPGRADE_CAPACITY, ModLang.UPGRADE_CAPACITY_DESCRIPTION, EnumColor.PURPLE);

    /**
     * Codec for serializing heat upgrades by their name.
     */
    public static final Codec<HeatedUpgrade> CODEC = StringRepresentable.fromEnum(HeatedUpgrade::values);
    /**
     * Gets a heat upgrade by index, wrapping for out-of-bound indices.
     */
    public static final IntFunction<HeatedUpgrade> BY_ID = ByIdMap.continuous(HeatedUpgrade::ordinal, values(), ByIdMap.OutOfBoundsStrategy.WRAP);
    /**
     * Stream codec for syncing heat upgrades by index.
     */
    public static final StreamCodec<ByteBuf, HeatedUpgrade> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, HeatedUpgrade::ordinal);

    private final String name;
    private final ILangEntry langEntry;
    private final ILangEntry descriptionEntry;
    private final EnumColor color;

    HeatedUpgrade(String name, ILangEntry langEntry, ILangEntry descriptionEntry, EnumColor color) {
        this.name = name;
        this.langEntry = langEntry;
        this.descriptionEntry = descriptionEntry;
        this.color = color;
    }

    /**
     * Gets the maximum number of upgrades of this type that can be installed on a single machine.
     */
    public int getMax() {
        return Config.Upgrades.MAX_HEAT_UPGRADES.get();
    }

    /**
     * Gets the configured per-upgrade bonus as a fraction: 0.1 means every upgrade is a 10% improvement.
     */
    public double getBonus() {
        return switch (this) {
            case CONDUCTION -> Config.Upgrades.CONDUCTION.get();
            case INSULATION -> Config.Upgrades.INSULATION.get();
            case CAPACITY -> Config.Upgrades.CAPACITY.get();
        };
    }

    /**
     * Gets the factor a capacitor value is multiplied by for the given number of installed upgrades, which is
     * {@code (1 + bonus)^count}.
     */
    public double getMultiplier(int count) {
        return Math.pow(1 + getBonus(), count);
    }

    /**
     * Gets the display name of this upgrade, without the "Upgrade" suffix.
     */
    public Component getTranslatedName() {
        return langEntry.translate();
    }

    /**
     * Gets the description of what this upgrade does.
     */
    public Component getDescription() {
        return descriptionEntry.translate();
    }

    /**
     * Gets the color to use when rendering this upgrade's name.
     */
    public EnumColor getColor() {
        return color;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    @Override
    public String getTranslationKey() {
        return langEntry.getTranslationKey();
    }
}
