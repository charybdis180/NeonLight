package com.charybdis.Neon_Lights;

import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

public class NeonSignRenderer implements BlockEntityRenderer<NeonSignBlockEntity> {

    private static final ResourceLocation GLYPH_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Neon_Lights.MODID, "block/glyph_base");

    public static final List<String> FRAME_MODELS = List.of(
            "block/frame_edge_up", "block/frame_edge_down", "block/frame_edge_left", "block/frame_edge_right",
            "block/frame_corner_up_left", "block/frame_corner_up_right",
            "block/frame_corner_down_left", "block/frame_corner_down_right",
            "block/corner_up_left", "block/corner_up_right", "block/corner_down_left", "block/corner_down_right",
            "block/support_up", "block/support_down", "block/support_left", "block/support_right");

    public static final int RAINBOW_CYCLE_TICKS = 200;

    // Reusable scratch for per-tick pixel-mesh baking (render thread only). 6 faces per pixel max.
    private static final int[] BAKE_SCRATCH = new int[NeonSignGrid.PIXEL_COUNT * 6];

    public NeonSignRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(NeonSignBlockEntity sign, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState state = sign.getBlockState();
        if (!(state.getBlock() instanceof NeonSignBlock)) {
            return;
        }

        Level level = sign.getLevel();

        int rgb;
        if (Neon_Lights.isRainbow(state.getBlock())) {
            long gameTime = (level == null) ? 0L : level.getGameTime();
            rgb = rainbowColor(gameTime, partialTick);
        } else {
            DyeColor color = Neon_Lights.colorOf(state.getBlock());
            rgb = (color == null) ? 0xFFFFFF : color.getTextureDiffuseColor();
        }
        float red = ((rgb >> 16) & 0xFF) / 255.0F;
        float green = ((rgb >> 8) & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;

        int yRotation = switch (state.getValue(NeonSignBlock.FACING)) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        boolean mounted = state.getValue(NeonSignBlock.MOUNTED);

        NeonSignBlock.FrameLayout frame = sign.renderFrameLayout();
        if (level != null) {
            renderFrame(frame, yRotation, mounted, red, green, blue,
                    poseStack, bufferSource, state, packedLight, packedOverlay);
        }

        String glyphModel = NeonSignGlyphs.glyphModelFor(sign.getCharacter());
        if (glyphModel != null) {
            boolean lit = state.getValue(NeonSignBlock.LIT);
            int glyphLight = lit ? LightTexture.FULL_BRIGHT : packedLight;
            float glyphRed = red;
            float glyphGreen = green;
            float glyphBlue = blue;
            int glyphColorIndex = sign.getGlyphColor();
            if (glyphColorIndex != NeonSignGrid.COLOR_EMPTY) {
                long gameTime = (level == null) ? 0L : level.getGameTime();
                int glyphRgb = NeonSignGrid.rgbOf(glyphColorIndex, gameTime, partialTick);
                glyphRed = ((glyphRgb >> 16) & 0xFF) / 255.0F;
                glyphGreen = ((glyphRgb >> 8) & 0xFF) / 255.0F;
                glyphBlue = (glyphRgb & 0xFF) / 255.0F;
            }
            renderPiece(glyphModel, yRotation, mounted, glyphRed, glyphGreen, glyphBlue,
                    poseStack, bufferSource, state, glyphLight, packedOverlay, RenderType.translucent());
        }

        if (sign.hasAnyPixels() && level != null) {
            byte[] pixels = sign.getPixelsForRender();
            if (pixels != null) {
                boolean lit = state.getValue(NeonSignBlock.LIT);
                int pixelLight = lit ? LightTexture.FULL_BRIGHT : packedLight;
                long gameTime = level.getGameTime();
                if (sign.pixelMeshTick() != gameTime || sign.pixelMesh() == null) {
                    boolean[] occupancy = sign.renderOccupancy(pixels, frame);
                    NeonSignPixelLookup lookup = new NeonSignPixelLookup(level, sign.getBlockPos(), state, occupancy);
                    int count = buildPixelMesh(pixels, frame, lookup, BAKE_SCRATCH);
                    sign.setPixelMesh(java.util.Arrays.copyOf(BAKE_SCRATCH, count), count, gameTime);
                }
                renderPixelsBaked(sign.pixelMesh(), sign.pixelMeshCount(), gameTime, partialTick,
                        yRotation, mounted, poseStack, bufferSource, pixelLight, packedOverlay);
            }
        }
    }

    /**
     * Culls the pixel art into a packed list of visible faces once per tick. The expensive
     * per-pixel neighbor/frame checks (including cross-block world lookups) only run here, so
     * per-frame rendering just replays the cached faces.
     */
    private static int buildPixelMesh(byte[] pixels, NeonSignBlock.FrameLayout frame,
                                      NeonSignPixelLookup lookup, int[] out) {
        int n = 0;
        for (int i = 0; i < NeonSignGrid.PIXEL_COUNT; i++) {
            int colorIndex = pixels[i] & 0xFF;
            if (colorIndex == NeonSignGrid.COLOR_EMPTY) {
                continue;
            }
            int x = NeonSignGrid.xOf(i);
            int y = NeonSignGrid.yOf(i);
            if (!NeonSignFrameMask.isEditable(frame, x, y)) {
                continue;
            }
            out[n++] = NeonSignGrid.packFace(x, y, 0, colorIndex);
            out[n++] = NeonSignGrid.packFace(x, y, 1, colorIndex);
            if (!lookup.hasFilledNeighbor(x, y, 1, 0)) {
                out[n++] = NeonSignGrid.packFace(x, y, 2, colorIndex);
            }
            if (!lookup.hasFilledNeighbor(x, y, -1, 0)) {
                out[n++] = NeonSignGrid.packFace(x, y, 3, colorIndex);
            }
            if (!lookup.hasFilledNeighbor(x, y, 0, 1)) {
                out[n++] = NeonSignGrid.packFace(x, y, 4, colorIndex);
            }
            if (!lookup.hasFilledNeighbor(x, y, 0, -1)) {
                out[n++] = NeonSignGrid.packFace(x, y, 5, colorIndex);
            }
        }
        return n;
    }

    private void renderPixelsBaked(int[] mesh, int count, long gameTime, float partialTick,
                                   int yRotation, boolean mounted,
                                   PoseStack poseStack, MultiBufferSource bufferSource,
                                   int packedLight, int packedOverlay) {
        if (mesh == null || count == 0) {
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(GLYPH_TEXTURE);
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.translucent());
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yRotation));
        poseStack.translate(-0.5, -0.5, -0.5);
        if (mounted) {
            poseStack.translate(0.0, 0.0, NeonSignBlock.MOUNTED_DEPTH);
        }

        int cachedColorIndex = -1;
        float red = 1.0F;
        float green = 1.0F;
        float blue = 1.0F;
        for (int idx = 0; idx < count; idx++) {
            int packed = mesh[idx];
            int colorIndex = NeonSignGrid.faceColor(packed);
            if (colorIndex != cachedColorIndex) {
                int rgb = NeonSignGrid.rgbOf(colorIndex, gameTime, partialTick);
                red = ((rgb >> 16) & 0xFF) / 255.0F;
                green = ((rgb >> 8) & 0xFF) / 255.0F;
                blue = (rgb & 0xFF) / 255.0F;
                cachedColorIndex = colorIndex;
            }
            emitFace(poseStack, consumer, NeonSignGrid.faceX(packed), NeonSignGrid.faceY(packed),
                    NeonSignGrid.faceId(packed), red, green, blue, u0, u1, v0, v1,
                    packedLight, packedOverlay);
        }

        poseStack.popPose();
    }

    private void emitFace(PoseStack poseStack, VertexConsumer consumer, int x, int y, int face,
                          float red, float green, float blue, float u0, float u1, float v0, float v1,
                          int packedLight, int packedOverlay) {
        float x1 = x / 16.0F;
        float x2 = (x + 1) / 16.0F;
        float y1 = y / 16.0F;
        float y2 = (y + 1) / 16.0F;
        float z1 = (float) (NeonSignGrid.PIXEL_Z1 / 16.0D);
        float z2 = (float) (NeonSignGrid.PIXEL_Z2 / 16.0D);

        switch (face) {
            case 0 -> emitQuad(poseStack, consumer, red, green, blue, packedLight, packedOverlay,
                    x1, y1, z1, x2, y1, z1, x2, y2, z1, x1, y2, z1,
                    u0, u1, v0, v1, 0.0F, 0.0F, -1.0F);
            case 1 -> emitQuad(poseStack, consumer, red, green, blue, packedLight, packedOverlay,
                    x1, y2, z2, x2, y2, z2, x2, y1, z2, x1, y1, z2,
                    u0, u1, v0, v1, 0.0F, 0.0F, 1.0F);
            case 2 -> emitQuad(poseStack, consumer, red, green, blue, packedLight, packedOverlay,
                    x2, y1, z1, x2, y1, z2, x2, y2, z2, x2, y2, z1,
                    u0, u1, v0, v1, 1.0F, 0.0F, 0.0F);
            case 3 -> emitQuad(poseStack, consumer, red, green, blue, packedLight, packedOverlay,
                    x1, y2, z1, x1, y2, z2, x1, y1, z2, x1, y1, z1,
                    u0, u1, v0, v1, -1.0F, 0.0F, 0.0F);
            case 4 -> emitQuad(poseStack, consumer, red, green, blue, packedLight, packedOverlay,
                    x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2,
                    u0, u1, v0, v1, 0.0F, 1.0F, 0.0F);
            case 5 -> emitQuad(poseStack, consumer, red, green, blue, packedLight, packedOverlay,
                    x1, y1, z2, x2, y1, z2, x2, y1, z1, x1, y1, z1,
                    u0, u1, v0, v1, 0.0F, -1.0F, 0.0F);
            default -> {
            }
        }
    }

    private void emitQuad(PoseStack poseStack, VertexConsumer consumer,
                          float red, float green, float blue, int packedLight, int packedOverlay,
                          float x0, float y0, float z0,
                          float x1, float y1, float z1,
                          float x2, float y2, float z2,
                          float x3, float y3, float z3,
                          float u0, float u1, float v0, float v1,
                          float nx, float ny, float nz) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();

        putVertex(consumer, pose, matrix, x0, y0, z0, red, green, blue, u0, v0, packedOverlay, packedLight, nx, ny, nz);
        putVertex(consumer, pose, matrix, x1, y1, z1, red, green, blue, u1, v0, packedOverlay, packedLight, nx, ny, nz);
        putVertex(consumer, pose, matrix, x2, y2, z2, red, green, blue, u1, v1, packedOverlay, packedLight, nx, ny, nz);
        putVertex(consumer, pose, matrix, x3, y3, z3, red, green, blue, u0, v1, packedOverlay, packedLight, nx, ny, nz);
    }

    private void putVertex(VertexConsumer consumer, PoseStack.Pose pose, Matrix4f matrix,
                           float x, float y, float z,
                           float red, float green, float blue,
                           float u, float v, int overlay, int light,
                           float nx, float ny, float nz) {
        consumer.addVertex(matrix, x, y, z)
                .setColor(red, green, blue, 1.0F)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    private void renderFrame(NeonSignBlock.FrameLayout f, int yRotation, boolean mounted,
                             float red, float green, float blue, PoseStack poseStack,
                             MultiBufferSource bufferSource, BlockState state, int packedLight, int packedOverlay) {
        RenderType renderType = RenderType.cutout();
        NeonSignBlock.SideConnection sign = NeonSignBlock.SideConnection.SIGN;
        NeonSignBlock.SideConnection support = NeonSignBlock.SideConnection.SUPPORT;

        if (f.up() != sign) {
            renderPiece("block/frame_edge_up", yRotation, mounted, red, green, blue, poseStack, bufferSource, state, packedLight, packedOverlay, renderType);
        }
        if (f.down() != sign) {
            renderPiece("block/frame_edge_down", yRotation, mounted, red, green, blue, poseStack, bufferSource, state, packedLight, packedOverlay, renderType);
        }
        if (f.left() != sign) {
            renderPiece("block/frame_edge_left", yRotation, mounted, red, green, blue, poseStack, bufferSource, state, packedLight, packedOverlay, renderType);
        }
        if (f.right() != sign) {
            renderPiece("block/frame_edge_right", yRotation, mounted, red, green, blue, poseStack, bufferSource, state, packedLight, packedOverlay, renderType);
        }

        if (!mounted) {
            if (f.up() == support) {
                renderPiece("block/support_up", yRotation, false, red, green, blue, poseStack, bufferSource, state, packedLight, packedOverlay, renderType);
            }
            if (f.down() == support) {
                renderPiece("block/support_down", yRotation, false, red, green, blue, poseStack, bufferSource, state, packedLight, packedOverlay, renderType);
            }
            if (f.left() == support) {
                renderPiece("block/support_left", yRotation, false, red, green, blue, poseStack, bufferSource, state, packedLight, packedOverlay, renderType);
            }
            if (f.right() == support) {
                renderPiece("block/support_right", yRotation, false, red, green, blue, poseStack, bufferSource, state, packedLight, packedOverlay, renderType);
            }
        }

        if (f.up() != sign && f.left() != sign) {
            renderPiece("block/frame_corner_up_left", yRotation, mounted, red, green, blue, poseStack, bufferSource, state, packedLight, packedOverlay, renderType);
        }
        if (f.up() != sign && f.right() != sign) {
            renderPiece("block/frame_corner_up_right", yRotation, mounted, red, green, blue, poseStack, bufferSource, state, packedLight, packedOverlay, renderType);
        }
        if (f.down() != sign && f.left() != sign) {
            renderPiece("block/frame_corner_down_left", yRotation, mounted, red, green, blue, poseStack, bufferSource, state, packedLight, packedOverlay, renderType);
        }
        if (f.down() != sign && f.right() != sign) {
            renderPiece("block/frame_corner_down_right", yRotation, mounted, red, green, blue, poseStack, bufferSource, state, packedLight, packedOverlay, renderType);
        }

        if (f.innerUpLeft()) {
            renderPiece("block/corner_up_left", yRotation, mounted, red, green, blue, poseStack, bufferSource, state, packedLight, packedOverlay, renderType);
        }
        if (f.innerUpRight()) {
            renderPiece("block/corner_up_right", yRotation, mounted, red, green, blue, poseStack, bufferSource, state, packedLight, packedOverlay, renderType);
        }
        if (f.innerDownLeft()) {
            renderPiece("block/corner_down_left", yRotation, mounted, red, green, blue, poseStack, bufferSource, state, packedLight, packedOverlay, renderType);
        }
        if (f.innerDownRight()) {
            renderPiece("block/corner_down_right", yRotation, mounted, red, green, blue, poseStack, bufferSource, state, packedLight, packedOverlay, renderType);
        }
    }

    private void renderPiece(String modelPath, int yRotation, boolean mounted, float red, float green, float blue,
                             PoseStack poseStack, MultiBufferSource bufferSource, BlockState state,
                             int packedLight, int packedOverlay, RenderType renderType) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(
                ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(Neon_Lights.MODID, modelPath)));

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yRotation));
        poseStack.translate(-0.5, -0.5, -0.5);
        if (mounted) {
            poseStack.translate(0.0, 0.0, NeonSignBlock.MOUNTED_DEPTH);
        }

        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(), bufferSource.getBuffer(renderType), state, model, red, green, blue,
                packedLight, packedOverlay, ModelData.EMPTY, renderType);

        poseStack.popPose();
    }

    public static int rainbowColor(long gameTime, float partialTick) {
        float hue = ((gameTime % RAINBOW_CYCLE_TICKS) + partialTick) / (float) RAINBOW_CYCLE_TICKS;
        return Mth.hsvToRgb(hue, 1.0F, 1.0F) & 0xFFFFFF;
    }

    public static int rainbowColorNow() {
        float hue = (System.currentTimeMillis() % (RAINBOW_CYCLE_TICKS * 50L)) / (RAINBOW_CYCLE_TICKS * 50.0F);
        return Mth.hsvToRgb(hue, 1.0F, 1.0F) & 0xFFFFFF;
    }
}
