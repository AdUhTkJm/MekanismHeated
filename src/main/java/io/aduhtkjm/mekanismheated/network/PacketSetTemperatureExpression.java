package io.aduhtkjm.mekanismheated.network;

import io.aduhtkjm.mekanismheated.Mod;
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
 * Replaces the expression of a {@link TileEntityTemperatureController}. The text is sent verbatim, even when it is
 * empty or does not parse: the server stores whatever the player typed and reports the outcome, and only the GUI turns
 * it into a message (with the client's own language, by re-parsing the same text).
 */
public record PacketSetTemperatureExpression(BlockPos pos, String expression) implements IMekanismPacket {

    public static final CustomPacketPayload.Type<PacketSetTemperatureExpression> TYPE = new CustomPacketPayload.Type<>(Mod.rl("set_temperature_expression"));
    public static final StreamCodec<ByteBuf, PacketSetTemperatureExpression> STREAM_CODEC = StreamCodec.composite(
          BlockPos.STREAM_CODEC, PacketSetTemperatureExpression::pos,
          ByteBufCodecs.STRING_UTF8, PacketSetTemperatureExpression::expression,
          PacketSetTemperatureExpression::new
    );

    @NotNull
    @Override
    public CustomPacketPayload.Type<PacketSetTemperatureExpression> type() {
        return TYPE;
    }

    @Override
    public void handle(IPayloadContext context) {
        if (PacketUtils.blockEntity(context, pos) instanceof TileEntityTemperatureController tile) {
            tile.setExpressionFromPacket(expression);
        }
    }
}
