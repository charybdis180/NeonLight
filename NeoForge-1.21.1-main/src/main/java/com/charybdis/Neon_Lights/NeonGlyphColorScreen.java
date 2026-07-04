package com.charybdis.Neon_Lights;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;

public class NeonGlyphColorScreen extends Screen {

    private static final int SWATCH_SIZE = 18;
    private static final int SWATCH_GAP = 3;

    private final BlockPos pos;
    private int selectedColor = NeonSignGrid.COLOR_EMPTY;
    private int gridOriginX;
    private int gridY;

    public NeonGlyphColorScreen(BlockPos pos) {
        super(Component.translatable("screen.neonlight.glyph_color"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        if (this.minecraft != null && this.minecraft.level != null
                && this.minecraft.level.getBlockEntity(pos) instanceof NeonSignBlockEntity sign) {
            this.selectedColor = sign.getGlyphColor();
        }

        int totalSwatches = 1 + DyeColor.values().length + 1;
        int rowWidth = totalSwatches * SWATCH_SIZE + (totalSwatches - 1) * SWATCH_GAP;
        gridOriginX = (this.width - rowWidth) / 2;
        gridY = this.height / 2 - SWATCH_SIZE / 2;

        int x = gridOriginX;
        addRenderableWidget(Button.builder(Component.empty(), btn -> applyColor(NeonSignGrid.COLOR_EMPTY))
                .bounds(x, gridY, SWATCH_SIZE, SWATCH_SIZE)
                .build());
        x += SWATCH_SIZE + SWATCH_GAP;
        for (DyeColor dye : DyeColor.values()) {
            int colorIndex = dye.getId() + 1;
            addRenderableWidget(Button.builder(Component.empty(), btn -> applyColor(colorIndex))
                    .bounds(x, gridY, SWATCH_SIZE, SWATCH_SIZE)
                    .build());
            x += SWATCH_SIZE + SWATCH_GAP;
        }
        addRenderableWidget(Button.builder(Component.literal("R"), btn -> applyColor(NeonSignGrid.COLOR_RAINBOW))
                .bounds(x, gridY, SWATCH_SIZE, SWATCH_SIZE)
                .build());
    }

    private void applyColor(int colorIndex) {
        selectedColor = colorIndex;
        PacketDistributor.sendToServer(new SetGlyphColorPayload(pos, colorIndex));
        onClose();
    }

    private int inheritedColor() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return 0xFFFFFF;
        }
        Block block = this.minecraft.level.getBlockState(pos).getBlock();
        if (Neon_Lights.isRainbow(block)) {
            return NeonSignRenderer.rainbowColorNow();
        }
        DyeColor dye = Neon_Lights.resolveSignColor(block);
        return dye == null ? 0xFFFFFF : dye.getTextureDiffuseColor();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, gridY - 24, 0xFFFFFFFF);

        int x = gridOriginX;
        drawSwatch(graphics, x, 0xFF000000 | inheritedColor(), selectedColor == NeonSignGrid.COLOR_EMPTY);
        graphics.drawCenteredString(this.font, "-", x + SWATCH_SIZE / 2, gridY + SWATCH_SIZE + 2, 0xFFAAAAAA);
        x += SWATCH_SIZE + SWATCH_GAP;
        for (DyeColor dye : DyeColor.values()) {
            drawSwatch(graphics, x, 0xFF000000 | dye.getTextureDiffuseColor(), selectedColor == dye.getId() + 1);
            x += SWATCH_SIZE + SWATCH_GAP;
        }
        drawSwatch(graphics, x, 0xFF000000 | NeonSignRenderer.rainbowColorNow(),
                selectedColor == NeonSignGrid.COLOR_RAINBOW);
    }

    private void drawSwatch(GuiGraphics graphics, int x, int argb, boolean selected) {
        graphics.fill(x + 1, gridY + 1, x + SWATCH_SIZE - 1, gridY + SWATCH_SIZE - 1, argb);
        if (selected) {
            graphics.renderOutline(x, gridY, SWATCH_SIZE, SWATCH_SIZE, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
