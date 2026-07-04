package com.charybdis.Neon_Lights;

import java.util.ArrayDeque;
import java.util.Deque;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;

public class NeonSignCanvasScreen extends Screen {

    private static final int FRAME_COLOR = 0xFF505050;
    private static final int EMPTY_COLOR = 0xFF101010;
    private static final int NO_SIGN_COLOR = 0xFF282828;
    private static final int DIVIDER_COLOR = 0xFF808080;
    private static final int SWATCH_SIZE = 14;
    private static final int TOOLBAR_HEIGHT = 40;
    private static final int BUTTON_WIDTH = 80;
    private static final int TITLE_HEIGHT = 18;
    private static final int MAX_HISTORY = 64;
    private static final int MAX_BRUSH_SIZE = 4;

    private final NeonSignClusterLayout layout;
    private byte[] canvas;
    private String customName = "";
    private int selectedColor = 1;
    private int brushSize = 1;
    private int cellSize;
    private int canvasOriginX;
    private int canvasOriginY;
    private boolean painting;
    private int lastPaintCol = Integer.MIN_VALUE;
    private int lastPaintRow = Integer.MIN_VALUE;
    private boolean editingName;
    private EditBox nameEdit;
    private final Deque<byte[]> undoStack = new ArrayDeque<>();
    private final Deque<byte[]> redoStack = new ArrayDeque<>();

    public NeonSignCanvasScreen(NeonSignClusterLayout layout) {
        super(Component.translatable("screen.neonlight.neon_canvas"));
        this.layout = layout;
        this.canvas = new byte[layout.canvasPixelW() * layout.canvasPixelH()];
    }

    @Override
    protected void init() {
        if (this.minecraft != null && this.minecraft.level != null) {
            this.canvas = layout.loadCanvasFromWorld(this.minecraft.level);
            this.customName = layout.loadCustomName(this.minecraft.level);
            this.selectedColor = defaultFrameColorIndex();
        }
        int swatchY = swatchRowY();
        int brushY = brushRowY();

        int brushX = 8;
        for (int size = 1; size <= MAX_BRUSH_SIZE; size++) {
            int chosen = size;
            addRenderableWidget(Button.builder(Component.literal(Integer.toString(size)), btn -> brushSize = chosen)
                    .bounds(brushX, brushY, SWATCH_SIZE, SWATCH_SIZE)
                    .build());
            brushX += SWATCH_SIZE + 2;
        }

        int swatchX = 8;
        for (DyeColor dye : DyeColor.values()) {
            int colorIndex = dye.getId() + 1;
            addRenderableWidget(Button.builder(Component.empty(), btn -> selectedColor = colorIndex)
                    .bounds(swatchX, swatchY, SWATCH_SIZE, SWATCH_SIZE)
                    .build());
            swatchX += SWATCH_SIZE + 2;
        }
        addRenderableWidget(Button.builder(Component.literal("R"), btn -> selectedColor = NeonSignGrid.COLOR_RAINBOW)
                .bounds(swatchX, swatchY, SWATCH_SIZE, SWATCH_SIZE)
                .build());
        swatchX += SWATCH_SIZE + 6;
        addRenderableWidget(Button.builder(Component.translatable("screen.neonlight.neon_canvas.eraser"),
                        btn -> selectedColor = NeonSignGrid.COLOR_EMPTY)
                .bounds(swatchX, swatchY, 48, SWATCH_SIZE)
                .build());
        swatchX += 48 + 3;
        addRenderableWidget(Button.builder(Component.translatable("screen.neonlight.neon_canvas.mirror"),
                        btn -> mirrorCanvasHorizontally())
                .bounds(swatchX, swatchY, 38, SWATCH_SIZE)
                .build());
        swatchX += 38 + 3;
        addRenderableWidget(Button.builder(Component.translatable("screen.neonlight.neon_canvas.undo"),
                        btn -> undo())
                .bounds(swatchX, swatchY, 30, SWATCH_SIZE)
                .build());
        swatchX += 30 + 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.neonlight.neon_canvas.redo"),
                        btn -> redo())
                .bounds(swatchX, swatchY, 30, SWATCH_SIZE)
                .build());

        int doneX = this.width - BUTTON_WIDTH - 8;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> saveAndClose())
                .bounds(doneX, swatchY, BUTTON_WIDTH, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), btn -> onClose())
                .bounds(doneX - BUTTON_WIDTH - 4, swatchY, BUTTON_WIDTH, 20)
                .build());

        if (editingName) {
            startNameEdit();
        }

        computeCanvasLayout();
    }

    private void startNameEdit() {
        editingName = true;
        int boxW = Math.min(220, this.width - 16);
        int boxX = (this.width - boxW) / 2;
        nameEdit = new EditBox(this.font, boxX, 4, boxW, 16, Component.translatable("screen.neonlight.neon_canvas.name_hint"));
        nameEdit.setMaxLength(NeonSignBlockEntity.MAX_CUSTOM_NAME_LENGTH);
        nameEdit.setValue(customName);
        nameEdit.setHint(Component.translatable("screen.neonlight.neon_canvas.name_hint"));
        addRenderableWidget(nameEdit);
        setInitialFocus(nameEdit);
    }

    private void finishNameEdit() {
        if (nameEdit != null) {
            customName = nameEdit.getValue().trim();
            removeWidget(nameEdit);
            nameEdit = null;
        }
        editingName = false;
    }

    private Component displayTitle() {
        if (customName.isEmpty()) {
            return Component.translatable("screen.neonlight.neon_canvas");
        }
        return Component.literal(customName);
    }

    private int swatchRowY() {
        return this.height - 8 - SWATCH_SIZE;
    }

    private int brushRowY() {
        return swatchRowY() - 4 - SWATCH_SIZE;
    }

    private int defaultFrameColorIndex() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return 1;
        }
        Block block = this.minecraft.level.getBlockState(layout.origin()).getBlock();
        if (Neon_Lights.isRainbow(block)) {
            return NeonSignGrid.COLOR_RAINBOW;
        }
        DyeColor dye = Neon_Lights.resolveSignColor(block);
        return dye == null ? 1 : dye.getId() + 1;
    }

    private void computeCanvasLayout() {
        int canvasTop = 24;
        int availW = this.width - 16;
        int availH = (brushRowY() - 6) - canvasTop;
        int maxCellW = availW / layout.canvasPixelW();
        int maxCellH = availH / layout.canvasPixelH();
        cellSize = Math.max(1, Math.min(maxCellW, maxCellH));
        int drawW = layout.canvasPixelW() * cellSize;
        int drawH = layout.canvasPixelH() * cellSize;
        canvasOriginX = (this.width - drawW) / 2;
        canvasOriginY = canvasTop + (availH - drawH) / 2;
    }

    private void recordHistory() {
        undoStack.push(canvas.clone());
        while (undoStack.size() > MAX_HISTORY) {
            undoStack.removeLast();
        }
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        redoStack.push(canvas.clone());
        canvas = undoStack.pop();
    }

    private void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        undoStack.push(canvas.clone());
        canvas = redoStack.pop();
    }

    private void mirrorCanvasHorizontally() {
        recordHistory();
        byte[] mirrored = canvas.clone();
        for (int cy = 0; cy < layout.canvasPixelH(); cy++) {
            for (int cx = 0; cx < layout.canvasPixelW(); cx++) {
                if (!layout.isCanvasPixelEditable(cx, cy)) {
                    continue;
                }
                int mirrorCx = layout.canvasPixelW() - 1 - cx;
                if (!layout.isCanvasPixelEditable(mirrorCx, cy)) {
                    continue;
                }
                mirrored[layout.canvasIndex(cx, cy)] = canvas[layout.canvasIndex(mirrorCx, cy)];
            }
        }
        this.canvas = mirrored;
    }

    private void saveAndClose() {
        if (editingName) {
            finishNameEdit();
        }
        PacketDistributor.sendToServer(new SetSignCanvasPayload(
                layout.origin(),
                layout.minCol(),
                layout.minRow(),
                layout.signCols(),
                layout.signRows(),
                canvas.clone(),
                customName));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        if (!editingName) {
            Component title = displayTitle();
            int titleWidth = this.font.width(title);
            int titleX = (this.width - titleWidth) / 2;
            graphics.drawString(this.font, title, titleX, 6, 0xFFFFFFFF, false);
            if (mouseX >= titleX - 4 && mouseX <= titleX + titleWidth + 4
                    && mouseY >= 4 && mouseY <= 4 + TITLE_HEIGHT) {
                graphics.fill(titleX - 2, 4 + TITLE_HEIGHT, titleX + titleWidth + 2, 4 + TITLE_HEIGHT + 1, 0xFFAAAAAA);
            } else {
                graphics.fill(titleX - 2, 4 + TITLE_HEIGHT, titleX + titleWidth + 2, 4 + TITLE_HEIGHT + 1, 0xFF555555);
            }
        }

        for (int cy = 0; cy < layout.canvasPixelH(); cy++) {
            for (int cx = 0; cx < layout.canvasPixelW(); cx++) {
                int px = canvasOriginX + (layout.canvasPixelW() - 1 - cx) * cellSize;
                int py = canvasOriginY + (layout.canvasPixelH() - 1 - cy) * cellSize;
                int color = canvas[layout.canvasIndex(cx, cy)] & 0xFF;
                int fill;
                if (!layout.isCanvasPixelEditable(cx, cy)) {
                    NeonSignClusterLayout.SignCell cell = layout.cellAtCanvasPixel(cx, cy);
                    if (cell == null) {
                        fill = NO_SIGN_COLOR;
                    } else {
                        fill = FRAME_COLOR;
                    }
                } else if (color != NeonSignGrid.COLOR_EMPTY) {
                    fill = 0xFF000000 | NeonSignGrid.rgbOfUi(color);
                } else {
                    fill = EMPTY_COLOR;
                }
                graphics.fill(px, py, px + cellSize, py + cellSize, fill);
            }
        }

        for (int col = 0; col <= layout.signCols(); col++) {
            int x = canvasOriginX + col * NeonSignGrid.PIXELS_W * cellSize;
            int y0 = canvasOriginY;
            int y1 = canvasOriginY + layout.canvasPixelH() * cellSize;
            graphics.fill(x, y0, x + 1, y1, DIVIDER_COLOR);
        }
        for (int row = 0; row <= layout.signRows(); row++) {
            int y = canvasOriginY + row * NeonSignGrid.PIXELS_H * cellSize;
            int x0 = canvasOriginX;
            int x1 = canvasOriginX + layout.canvasPixelW() * cellSize;
            graphics.fill(x0, y, x1, y + 1, DIVIDER_COLOR);
        }

        int brushY = brushRowY();
        int bx = 8;
        for (int size = 1; size <= MAX_BRUSH_SIZE; size++) {
            if (brushSize == size) {
                graphics.renderOutline(bx, brushY, SWATCH_SIZE, SWATCH_SIZE, 0xFFFFFFFF);
            }
            bx += SWATCH_SIZE + 2;
        }

        int swatchY = swatchRowY();
        int sx = 8;
        for (DyeColor dye : DyeColor.values()) {
            int rgb = 0xFF000000 | dye.getTextureDiffuseColor();
            graphics.fill(sx + 1, swatchY + 1, sx + SWATCH_SIZE - 1, swatchY + SWATCH_SIZE - 1, rgb);
            if (selectedColor == dye.getId() + 1) {
                graphics.renderOutline(sx, swatchY, SWATCH_SIZE, SWATCH_SIZE, 0xFFFFFFFF);
            }
            sx += SWATCH_SIZE + 2;
        }
        if (selectedColor == NeonSignGrid.COLOR_RAINBOW) {
            graphics.renderOutline(sx, swatchY, SWATCH_SIZE, SWATCH_SIZE, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!editingName && button == 0 && isTitleClick(mouseX, mouseY)) {
            startNameEdit();
            return true;
        }
        if (button == 0 || button == 1) {
            byte[] before = canvas.clone();
            if (paintAt(mouseX, mouseY, button == 1)) {
                undoStack.push(before);
                while (undoStack.size() > MAX_HISTORY) {
                    undoStack.removeLast();
                }
                redoStack.clear();
                painting = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isTitleClick(double mouseX, double mouseY) {
        if (mouseY < 4 || mouseY > 4 + TITLE_HEIGHT) {
            return false;
        }
        Component title = displayTitle();
        int titleWidth = this.font.width(title);
        int titleX = (this.width - titleWidth) / 2;
        return mouseX >= titleX - 4 && mouseX <= titleX + titleWidth + 4;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        painting = false;
        lastPaintCol = Integer.MIN_VALUE;
        lastPaintRow = Integer.MIN_VALUE;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (painting && (button == 0 || button == 1)) {
            if (paintAt(mouseX, mouseY, button == 1)) {
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingName) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                finishNameEdit();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (Screen.hasControlDown()) {
            if (keyCode == GLFW.GLFW_KEY_Z) {
                if (Screen.hasShiftDown()) {
                    redo();
                } else {
                    undo();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_Y) {
                redo();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean paintAt(double mouseX, double mouseY, boolean erase) {
        int drawW = layout.canvasPixelW() * cellSize;
        int drawH = layout.canvasPixelH() * cellSize;
        if (mouseX < canvasOriginX || mouseY < canvasOriginY
                || mouseX >= canvasOriginX + drawW || mouseY >= canvasOriginY + drawH) {
            // Left the canvas: break the stroke so re-entry doesn't draw a stray connecting line.
            lastPaintCol = Integer.MIN_VALUE;
            lastPaintRow = Integer.MIN_VALUE;
            return false;
        }
        int screenCol = (int) ((mouseX - canvasOriginX) / cellSize);
        int screenRow = (int) ((mouseY - canvasOriginY) / cellSize);
        int centerX = layout.canvasPixelW() - 1 - screenCol;
        int centerY = layout.canvasPixelH() - 1 - screenRow;

        boolean painted;
        if (lastPaintCol != Integer.MIN_VALUE) {
            // Interpolate between events so fast drags / frame skips don't leave gaps.
            painted = paintLine(lastPaintCol, lastPaintRow, centerX, centerY, erase);
        } else {
            painted = paintBrush(centerX, centerY, erase);
        }
        lastPaintCol = centerX;
        lastPaintRow = centerY;
        return painted;
    }

    private boolean paintLine(int x0, int y0, int x1, int y1, boolean erase) {
        boolean painted = false;
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        while (true) {
            painted |= paintBrush(x, y, erase);
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
        return painted;
    }

    private boolean paintBrush(int centerX, int centerY, boolean erase) {
        int color = erase ? NeonSignGrid.COLOR_EMPTY : selectedColor;
        int lo = -((brushSize - 1) / 2);
        int hi = brushSize / 2;
        boolean painted = false;
        for (int oy = lo; oy <= hi; oy++) {
            for (int ox = lo; ox <= hi; ox++) {
                int tx = centerX + ox;
                int ty = centerY + oy;
                if (tx < 0 || ty < 0 || tx >= layout.canvasPixelW() || ty >= layout.canvasPixelH()) {
                    continue;
                }
                if (!layout.isCanvasPixelEditable(tx, ty)) {
                    continue;
                }
                canvas[layout.canvasIndex(tx, ty)] = (byte) color;
                painted = true;
            }
        }
        return painted;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
