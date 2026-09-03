package io.aduhtkjm.mekanismheated.network;

import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.tile.TileEntityCooler;
import io.netty.buffer.ByteBuf;
import mekanism.common.network.IMekanismPacket;
import mekanism.common.network.PacketUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record PacketCoolerSetEnergy(BlockPos pos, long energyUsage) implements IMekanismPacket {

    public static final CustomPacketPayload.Type<PacketCoolerSetEnergy> TYPE = new CustomPacketPayload.Type<>(Mod.rl("cooler_set_energy"));
    public static final StreamCodec<ByteBuf, PacketCoolerSetEnergy> STREAM_CODEC = StreamCodec.composite(
          BlockPos.STREAM_CODEC, PacketCoolerSetEnergy::pos,
          ByteBufCodecs.VAR_LONG, PacketCoolerSetEnergy::energyUsage,
          PacketCoolerSetEnergy::new
    );

    @NotNull
    @Override
    public CustomPacketPayload.Type<PacketCoolerSetEnergy> type() {
        return TYPE;
    }

    @Override
    public void handle(IPayloadContext context) {
        if (PacketUtils.blockEntity(context, pos) instanceof TileEntityCooler tile) {
            tile.setEnergyUsageFromPacket(energyUsage);
        }
    }
}
