package io.aduhtkjm.mekanismheated.network;

import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.content.upgrade.HeatedUpgrade;
import io.aduhtkjm.mekanismheated.content.upgrade.HeatedUpgrades;
import io.aduhtkjm.mekanismheated.content.upgrade.IHeatedUpgradeComponent;
import io.netty.buffer.ByteBuf;
import mekanism.common.network.IMekanismPacket;
import mekanism.common.network.PacketUtils;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * Sent by the upgrade window when a player uninstalls one of this mod's heat upgrades. The installed upgrades are moved
 * into the machine's upgrade output slot, exactly like Mekanism's own uninstall button does.
 *
 * @param pos       Position of the machine.
 * @param upgrade   Ordinal of the {@link HeatedUpgrade} to remove.
 * @param removeAll Whether to remove every installed upgrade of that type instead of just one.
 */
public record PacketRemoveHeatedUpgrade(BlockPos pos, int upgrade, boolean removeAll) implements IMekanismPacket {

    public static final CustomPacketPayload.Type<PacketRemoveHeatedUpgrade> TYPE = new CustomPacketPayload.Type<>(Mod.rl("remove_heated_upgrade"));
    public static final StreamCodec<ByteBuf, PacketRemoveHeatedUpgrade> STREAM_CODEC = StreamCodec.composite(
          BlockPos.STREAM_CODEC, PacketRemoveHeatedUpgrade::pos,
          ByteBufCodecs.VAR_INT, PacketRemoveHeatedUpgrade::upgrade,
          ByteBufCodecs.BOOL, PacketRemoveHeatedUpgrade::removeAll,
          PacketRemoveHeatedUpgrade::new
    );

    @NotNull
    @Override
    public CustomPacketPayload.Type<PacketRemoveHeatedUpgrade> type() {
        return TYPE;
    }

    @Override
    public void handle(IPayloadContext context) {
        if (PacketUtils.blockEntity(context, pos) instanceof TileEntityMekanism tile && HeatedUpgrades.supports(tile) && tile.supportsUpgrades()
              && tile.getComponent() instanceof IHeatedUpgradeComponent component) {
            component.mekanismheated$removeHeatedUpgrades(HeatedUpgrade.BY_ID.apply(upgrade), removeAll);
        }
    }
}
