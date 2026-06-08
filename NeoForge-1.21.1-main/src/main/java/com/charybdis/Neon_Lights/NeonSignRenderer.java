package com.charybdis.Neon_Lights;

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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

public class NeonSignRenderer implements BlockEntityRenderer<NeonSignBlockEntity> {

    public NeonSignRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(NeonSignBlockEntity sign, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState state = sign.getBlockState();
        if (!(state.getBlock() instanceof NeonSignBlock)) {
            return;
        }

        DyeColor color = Neon_Lights.colorOf(state.getBlock());
        int rgb = (color == null) ? 0xFFFFFF : color.getTextureDiffuseColor();
        float red = ((rgb >> 16) & 0xFF) / 255.0F;
        float green = ((rgb >> 8) & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;
        int yRotation = switch (state.getValue(NeonSignBlock.FACING)) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };

        String glyphModel = NeonSignGlyphs.glyphModelFor(sign.getCharacter());
        if (glyphModel != null) {
            boolean lit = state.getValue(NeonSignBlock.LIT);
            int glyphLight = lit ? LightTexture.FULL_BRIGHT : packedLight;
            renderGlyph(glyphModel, yRotation, red, green, blue, poseStack, bufferSource, state, glyphLight);
        }
    }

    private void renderGlyph(String modelPath, int yRotation, float red, float green, float blue,
                             PoseStack poseStack, MultiBufferSource bufferSource, BlockState state, int packedLight) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(
                ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(Neon_Lights.MODID, modelPath)));
        RenderType renderType = RenderType.translucent();

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yRotation));
        poseStack.translate(-0.5, -0.5, -0.5);

        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(), bufferSource.getBuffer(renderType), state, model, red, green, blue,
                packedLight, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, renderType);

        poseStack.popPose();
    }
}
