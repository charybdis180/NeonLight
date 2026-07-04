package com.charybdis.Neon_Lights;

import java.util.HashSet;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

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

    public static void openCanvasScreen(NeonSignClusterLayout layout) {
        Minecraft.getInstance().setScreen(new NeonSignCanvasScreen(layout));
    }

    private static void register(ModelEvent.RegisterAdditional event, String path) {
        event.register(ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(Neon_Lights.MODID, path)));
    }

    public static void openSignScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new NeonSignScreen(pos));
    }

    public static void openGlyphColorScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new NeonGlyphColorScreen(pos));
    }

    @SubscribeEvent
    static void onBlockHighlight(RenderHighlightEvent.Block event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        boolean holdingTool = player.getMainHandItem().is(Neon_Lights.NEON_DESIGN_TOOL.get())
                || player.getOffhandItem().is(Neon_Lights.NEON_DESIGN_TOOL.get());
        if (!holdingTool) {
            return;
        }
        BlockPos pos = event.getTarget().getBlockPos();
        if (!NeonSignBlock.isCustomized(mc.level, pos)) {
            return;
        }
        BlockState state = mc.level.getBlockState(pos);
        Set<BlockPos> cluster = new HashSet<>(NeonSignBlock.collectCanvasCluster(mc.level, pos, state));
        if (cluster.isEmpty()) {
            return;
        }
        AABB local = state.getShape(mc.level, pos).bounds();
        if (local.getXsize() == 0 && local.getYsize() == 0 && local.getZsize() == 0) {
            return;
        }

        event.setCanceled(true);

        int rgb = frameColorOf(state.getBlock());
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;

        float phase = (mc.level.getGameTime() % 40L
                + event.getDeltaTracker().getGameTimeDeltaPartialTick(false)) / 40.0f;
        float pulse = 0.5f + 0.5f * Mth.sin(phase * (float) (Math.PI * 2.0));
        float a = 0.6f + 0.35f * pulse;

        Direction facing = state.getValue(NeonSignBlock.FACING);
        boolean planeXY = facing.getAxis() == Direction.Axis.Z;
        double m = 0.01;

        Vec3 cam = event.getCamera().getPosition();
        PoseStack.Pose pose = event.getPoseStack().last();
        VertexConsumer vc = event.getMultiBufferSource().getBuffer(NeonRenderTypes.THICK_LINES);

        for (BlockPos p : cluster) {
            AABB box = local.move(p.getX(), p.getY(), p.getZ());
            double x0 = box.minX - m - cam.x;
            double x1 = box.maxX + m - cam.x;
            double y0 = box.minY - m - cam.y;
            double y1 = box.maxY + m - cam.y;
            double z0 = box.minZ - m - cam.z;
            double z1 = box.maxZ + m - cam.z;
            boolean up = cluster.contains(p.above());
            boolean down = cluster.contains(p.below());

            if (planeXY) {
                double zf = ((facing == Direction.NORTH ? box.minZ : box.maxZ) + facing.getStepZ() * 0.01) - cam.z;
                boolean east = cluster.contains(p.east());
                boolean west = cluster.contains(p.west());
                if (!up) {
                    addLine(vc, pose, x0, y1, zf, x1, y1, zf, r, g, b, a);
                }
                if (!down) {
                    addLine(vc, pose, x0, y0, zf, x1, y0, zf, r, g, b, a);
                }
                if (!east) {
                    addLine(vc, pose, x1, y0, zf, x1, y1, zf, r, g, b, a);
                }
                if (!west) {
                    addLine(vc, pose, x0, y0, zf, x0, y1, zf, r, g, b, a);
                }
            } else {
                double xf = ((facing == Direction.WEST ? box.minX : box.maxX) + facing.getStepX() * 0.01) - cam.x;
                boolean south = cluster.contains(p.south());
                boolean north = cluster.contains(p.north());
                if (!up) {
                    addLine(vc, pose, xf, y1, z0, xf, y1, z1, r, g, b, a);
                }
                if (!down) {
                    addLine(vc, pose, xf, y0, z0, xf, y0, z1, r, g, b, a);
                }
                if (!south) {
                    addLine(vc, pose, xf, y0, z1, xf, y1, z1, r, g, b, a);
                }
                if (!north) {
                    addLine(vc, pose, xf, y0, z0, xf, y1, z0, r, g, b, a);
                }
            }
        }
    }

    private static void addLine(VertexConsumer vc, PoseStack.Pose pose,
                                double x0, double y0, double z0, double x1, double y1, double z1,
                                float r, float g, float b, float a) {
        float nx = (float) (x1 - x0);
        float ny = (float) (y1 - y0);
        float nz = (float) (z1 - z0);
        float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0e-5f) {
            return;
        }
        nx /= len;
        ny /= len;
        nz /= len;
        vc.addVertex(pose.pose(), (float) x0, (float) y0, (float) z0).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose.pose(), (float) x1, (float) y1, (float) z1).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
    }

    private static int frameColorOf(net.minecraft.world.level.block.Block block) {
        if (Neon_Lights.isRainbow(block)) {
            return NeonSignRenderer.rainbowColorNow();
        }
        DyeColor dye = Neon_Lights.resolveSignColor(block);
        return dye == null ? 0xFFFFFF : dye.getTextureDiffuseColor();
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
