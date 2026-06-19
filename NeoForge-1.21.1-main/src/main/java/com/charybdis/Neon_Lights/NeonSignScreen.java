package com.charybdis.Neon_Lights;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class NeonSignScreen extends Screen {
    private static final int KEY_BACKSPACE = 259;
    private static final int KEY_DELETE = 261;
    private static final int KEY_UP = 265;
    private static final int KEY_DOWN = 264;
    private static final int KEY_LEFT = 263;
    private static final int KEY_RIGHT = 262;

    private final BlockPos pos;

    public NeonSignScreen(BlockPos pos) {
        super(Component.translatable("screen.neonlight.neon_sign"));
        this.pos = pos;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        String character = String.valueOf(codePoint);
        if (NeonSignGlyphs.isAllowed(character)) {
            apply(character);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == KEY_BACKSPACE || keyCode == KEY_DELETE) {
            apply("");
            return true;
        }
        String arrow = switch (keyCode) {
            case KEY_UP -> String.valueOf(NeonSignGlyphs.ARROW_UP);
            case KEY_DOWN -> String.valueOf(NeonSignGlyphs.ARROW_DOWN);
            case KEY_LEFT -> String.valueOf(NeonSignGlyphs.ARROW_LEFT);
            case KEY_RIGHT -> String.valueOf(NeonSignGlyphs.ARROW_RIGHT);
            default -> null;
        };
        if (arrow != null) {
            apply(arrow);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void apply(String character) {
        PacketDistributor.sendToServer(new SetSignLetterPayload(pos, character));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        int cy = this.height / 2;
        graphics.drawCenteredString(this.font, this.title, cx, cy - 20, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("screen.neonlight.neon_sign.prompt"), cx, cy, 0xFFA0A0A0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
