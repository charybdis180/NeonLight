package com.charybdis.Neon_Lights;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetGlyphColorPayload(BlockPos pos, int colorIndex) implements CustomPacketPayload {

    public static final Type<SetGlyphColorPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Neon_Lights.MODID, "set_glyph_color"));

    public static final StreamCodec<ByteBuf, SetGlyphColorPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetGlyphColorPayload::pos,
            ByteBufCodecs.VAR_INT, SetGlyphColorPayload::colorIndex,
            SetGlyphColorPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, SetGlyphColorPayload::handle);
    }

    public static void handle(SetGlyphColorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            double dx = payload.pos.getX() + 0.5, dy = payload.pos.getY() + 0.5, dz = payload.pos.getZ() + 0.5;
            if (player.distanceToSqr(dx, dy, dz) > 64.0) {
                return;
            }
            if (!NeonSignGrid.isValidColorIndex(payload.colorIndex)) {
                return;
            }
            if (player.level().getBlockEntity(payload.pos) instanceof NeonSignBlockEntity sign
                    && !sign.getCharacter().isEmpty()) {
                sign.setGlyphColor(payload.colorIndex);
            }
        });
    }
}
