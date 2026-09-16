package io.aduhtkjm.mekanismheated.content.upgrade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import mekanism.api.SerializationConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;

/**
 * The heat upgrades installed on a machine, stored as a data component on the machine's item when it is picked up.
 *
 * <p>Mirrors Mekanism's own {@code UpgradeAware} attachment, which is what keeps the vanilla upgrades on a machine that
 * is wrenched. The upgrade slot's contents are already carried by that attachment, so only the installed counts are
 * needed here.</p>
 *
 * @param upgrades Installed heat upgrades and their amounts.
 */
public record HeatedUpgradeAware(Map<HeatedUpgrade, Integer> upgrades) {

    public static final HeatedUpgradeAware EMPTY = new HeatedUpgradeAware(Collections.emptyMap());

    public static final Codec<HeatedUpgradeAware> CODEC = RecordCodecBuilder.create(instance -> instance.group(
          Codec.unboundedMap(HeatedUpgrade.CODEC, ExtraCodecs.POSITIVE_INT).fieldOf(SerializationConstants.UPGRADES).forGetter(HeatedUpgradeAware::upgrades)
    ).apply(instance, HeatedUpgradeAware::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, HeatedUpgradeAware> STREAM_CODEC = StreamCodec.composite(
          ByteBufCodecs.map(size -> new EnumMap<>(HeatedUpgrade.class), HeatedUpgrade.STREAM_CODEC, ByteBufCodecs.VAR_INT), HeatedUpgradeAware::upgrades,
          HeatedUpgradeAware::new
    );
}
