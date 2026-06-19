package com.charybdis.Neon_Lights;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

public class NeonSignRenderer implements BlockEntityRenderer<NeonSignBlockEntity> {

    public static final List<String> FRAME_MODELS = List.of(
            "block/frame_edge_up", "block/frame_edge_down", "block/frame_edge_left", "block/frame_edge_right",
            "block/frame_corner_up_left", "block/frame_corner_up_right",
            "block/frame_corner_down_left", "block/frame_corner_down_right",
            "block/corner_up_left", "block/corner_up_right", "block/corner_down_left", "block/corner_down_right",
            "block/support_up", "block/support_down", "block/support_left", "block/support_right");

    public static final int RAINBOW_CYCLE_TICKS = 200;

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

        if (level != null) {
            NeonSignBlock.FrameLayout frame = NeonSignBlock.computeFrame(level, sign.getBlockPos(), state);
            renderFrame(frame, yRotation, mounted, red, green, blue,
                    poseStack, bufferSource, state, packedLight, packedOverlay);
        }

        String glyphModel = NeonSignGlyphs.glyphModelFor(sign.getCharacter());
        if (glyphModel != null) {
            boolean lit = state.getValue(NeonSignBlock.LIT);
            int glyphLight = lit ? LightTexture.FULL_BRIGHT : packedLight;
            renderPiece(glyphModel, yRotation, mounted, red, green, blue,
                    poseStack, bufferSource, state, glyphLight, packedOverlay, RenderType.translucent());
        }
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
