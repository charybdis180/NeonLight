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

public record SetSignCanvasPayload(
        BlockPos origin,
        int minCol,
        int minRow,
        int signCols,
        int signRows,
        byte[] canvasPixels,
        String customName) implements CustomPacketPayload {

    public static final Type<SetSignCanvasPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Neon_Lights.MODID, "set_sign_canvas"));

    private record CanvasBody(int minCol, int minRow, int signCols, int signRows, byte[] canvasPixels, String customName) {
    }

    private static final StreamCodec<ByteBuf, CanvasBody> CANVAS_BODY_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CanvasBody::minCol,
            ByteBufCodecs.VAR_INT, CanvasBody::minRow,
            ByteBufCodecs.VAR_INT, CanvasBody::signCols,
            ByteBufCodecs.VAR_INT, CanvasBody::signRows,
            ByteBufCodecs.BYTE_ARRAY, CanvasBody::canvasPixels,
            ByteBufCodecs.STRING_UTF8, CanvasBody::customName,
            CanvasBody::new);

    public static final StreamCodec<ByteBuf, SetSignCanvasPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetSignCanvasPayload::origin,
            CANVAS_BODY_CODEC, payload -> new CanvasBody(
                    payload.minCol(), payload.minRow(), payload.signCols(), payload.signRows(),
                    payload.canvasPixels(), payload.customName()),
            (origin, body) -> new SetSignCanvasPayload(
                    origin, body.minCol(), body.minRow(), body.signCols(), body.signRows(),
                    body.canvasPixels(), body.customName()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, SetSignCanvasPayload::handle);
    }

    public static void handle(SetSignCanvasPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            double dx = payload.origin.getX() + 0.5;
            double dy = payload.origin.getY() + 0.5;
            double dz = payload.origin.getZ() + 0.5;
            if (player.distanceToSqr(dx, dy, dz) > 64.0) {
                return;
            }
            if (payload.signCols <= 0 || payload.signRows <= 0
                    || payload.signCols > NeonSignGrid.MAX_CANVAS_SIGNS_PER_AXIS
                    || payload.signRows > NeonSignGrid.MAX_CANVAS_SIGNS_PER_AXIS) {
                return;
            }
            int expectedLen = payload.signCols * payload.signRows * NeonSignGrid.PIXEL_COUNT;
            if (payload.canvasPixels.length != expectedLen) {
                return;
            }

            NeonSignClusterLayout layout = NeonSignClusterLayout.build(player.level(), payload.origin);
            if (layout == null) {
                return;
            }
            if (layout.minCol() != payload.minCol || layout.minRow() != payload.minRow
                    || layout.signCols() != payload.signCols || layout.signRows() != payload.signRows) {
                return;
            }
            if (layout.canvasPixelW() * layout.canvasPixelH() != payload.canvasPixels.length) {
                return;
            }

            for (int cy = 0; cy < layout.canvasPixelH(); cy++) {
                for (int cx = 0; cx < layout.canvasPixelW(); cx++) {
                    int color = payload.canvasPixels[layout.canvasIndex(cx, cy)] & 0xFF;
                    if (color == NeonSignGrid.COLOR_EMPTY) {
                        continue;
                    }
                    if (!layout.isCanvasPixelEditable(cx, cy) || !NeonSignGrid.isValidColorIndex(color)) {
                        return;
                    }
                }
            }

            String name = NeonSignBlockEntity.sanitizeCustomName(payload.customName);
            layout.applyCanvasToWorld(player.level(), payload.canvasPixels, name);
        });
    }
}
