package io.aduhtkjm.mekanismheated.network;

import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.tile.TileEntityCreativeHeatBlock;
import io.netty.buffer.ByteBuf;
import mekanism.common.network.IMekanismPacket;
import mekanism.common.network.PacketUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record PacketSetHeatTarget(BlockPos pos, double targetTemperature) implements IMekanismPacket {

    public static final CustomPacketPayload.Type<PacketSetHeatTarget> TYPE = new CustomPacketPayload.Type<>(Mod.rl("set_heat_target"));
    public static final StreamCodec<ByteBuf, PacketSetHeatTarget> STREAM_CODEC = StreamCodec.composite(
          BlockPos.STREAM_CODEC, PacketSetHeatTarget::pos,
          ByteBufCodecs.DOUBLE, PacketSetHeatTarget::targetTemperature,
          PacketSetHeatTarget::new
    );

    @NotNull
    @Override
    public CustomPacketPayload.Type<PacketSetHeatTarget> type() {
        return TYPE;
    }

    @Override
    public void handle(IPayloadContext context) {
        if (PacketUtils.blockEntity(context, pos) instanceof TileEntityCreativeHeatBlock tile) {
            tile.setTargetTemperature(targetTemperature);
        }
    }
}
