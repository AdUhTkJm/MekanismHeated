package io.aduhtkjm.mekanismheated.network;

import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.content.expression.OutputMode;
import io.aduhtkjm.mekanismheated.tile.TileEntityTemperatureController;
import io.netty.buffer.ByteBuf;
import mekanism.common.network.IMekanismPacket;
import mekanism.common.network.PacketUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * Switches a {@link TileEntityTemperatureController} between its redstone and energy output modes. The tile also drops
 * a redstone signal latched from before the switch, so that it cannot stay on forever once nothing writes it.
 */
public record PacketSetTemperatureControllerMode(BlockPos pos, byte modeOrdinal) implements IMekanismPacket {

    public static final CustomPacketPayload.Type<PacketSetTemperatureControllerMode> TYPE =
          new CustomPacketPayload.Type<>(Mod.rl("set_temperature_controller_mode"));
    public static final StreamCodec<ByteBuf, PacketSetTemperatureControllerMode> STREAM_CODEC = StreamCodec.composite(
          BlockPos.STREAM_CODEC, PacketSetTemperatureControllerMode::pos,
          ByteBufCodecs.BYTE, PacketSetTemperatureControllerMode::modeOrdinal,
          PacketSetTemperatureControllerMode::new
    );

    @NotNull
    @Override
    public CustomPacketPayload.Type<PacketSetTemperatureControllerMode> type() {
        return TYPE;
    }

    @Override
    public void handle(IPayloadContext context) {
        if (PacketUtils.blockEntity(context, pos) instanceof TileEntityTemperatureController tile) {
            tile.setOutputModeFromPacket(OutputMode.byIndex(modeOrdinal));
        }
    }
}
