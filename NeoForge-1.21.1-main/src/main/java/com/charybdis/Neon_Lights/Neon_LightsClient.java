package com.charybdis.Neon_Lights;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@Mod(value = Neon_Lights.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Neon_Lights.MODID, value = Dist.CLIENT)
public class Neon_LightsClient {

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Neon_Lights.NEON_SIGN_BE.get(), NeonSignRenderer::new);
    }

    @SubscribeEvent
    static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        NeonSignGlyphs.additionalModelPaths().forEach(path -> register(event, path));
        NeonSignRenderer.FRAME_MODELS.forEach(path -> register(event, path));
    }

    private static void register(ModelEvent.RegisterAdditional event, String path) {
        event.register(ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(Neon_Lights.MODID, path)));
    }

    public static void openSignScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new NeonSignScreen(pos));
    }

    @SubscribeEvent
    static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        for (DyeColor color : DyeColor.values()) {
            int rgb = color.getTextureDiffuseColor();
            event.register((state, level, pos, tintIndex) -> tintIndex == 0 ? rgb : -1,
                    Neon_Lights.COLORED_SIGNS.get(color).get());
        }
    }

    @SubscribeEvent
    static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        for (DyeColor color : DyeColor.values()) {
            int rgb = color.getTextureDiffuseColor();
            event.register((stack, tintIndex) -> tintIndex == 0 ? rgb : -1,
                    Neon_Lights.COLORED_SIGN_ITEMS.get(color).get());
        }
        event.register((stack, tintIndex) -> tintIndex == 0 ? NeonSignRenderer.rainbowColorNow() : -1,
                Neon_Lights.RAINBOW_NEON_SIGN_ITEM.get());
    }
}
