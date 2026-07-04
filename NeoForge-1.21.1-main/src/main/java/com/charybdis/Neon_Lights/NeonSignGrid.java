package com.charybdis.Neon_Lights;

import net.minecraft.world.item.DyeColor;

public final class NeonSignGrid {

    public static final int PIXELS_W = 16;
    public static final int PIXELS_H = 16;
    public static final int PIXEL_COUNT = PIXELS_W * PIXELS_H;
    public static final double PIXEL_Z1 = 7.5D;
    public static final double PIXEL_Z2 = 8.5D;

    public static final int COLOR_EMPTY = 0;
    public static final int COLOR_RAINBOW = 17;
    public static final int MAX_DYE_COLOR_INDEX = 16;

    public static final int MAX_CANVAS_SIGNS_PER_AXIS = 32;

    private NeonSignGrid() {
    }

    public static int index(int x, int y) {
        return y * PIXELS_W + x;
    }

    public static int xOf(int index) {
        return index % PIXELS_W;
    }

    public static int yOf(int index) {
        return index / PIXELS_W;
    }

    public static boolean isValidColorIndex(int colorIndex) {
        return colorIndex == COLOR_EMPTY
                || (colorIndex >= 1 && colorIndex <= MAX_DYE_COLOR_INDEX)
                || colorIndex == COLOR_RAINBOW;
    }

    public static DyeColor dyeColorOf(int colorIndex) {
        if (colorIndex >= 1 && colorIndex <= MAX_DYE_COLOR_INDEX) {
            return DyeColor.byId(colorIndex - 1);
        }
        return null;
    }

    public static int rgbOf(int colorIndex, long gameTime, float partialTick) {
        if (colorIndex == COLOR_RAINBOW) {
            return NeonSignRenderer.rainbowColor(gameTime, partialTick);
        }
        DyeColor dye = dyeColorOf(colorIndex);
        return dye == null ? 0xFFFFFF : dye.getTextureDiffuseColor();
    }

    public static int rgbOfUi(int colorIndex) {
        if (colorIndex == COLOR_RAINBOW) {
            return NeonSignRenderer.rainbowColorNow();
        }
        DyeColor dye = dyeColorOf(colorIndex);
        return dye == null ? 0x000000 : dye.getTextureDiffuseColor();
    }

    // Packed pixel-mesh face encoding: x(4) | y(4) | face(3) | colorIndex(5).
    // Faces: 0=front(-Z) 1=back(+Z) 2=+X 3=-X 4=+Y 5=-Y.
    public static int packFace(int x, int y, int face, int colorIndex) {
        return (x & 0xF) | ((y & 0xF) << 4) | ((face & 0x7) << 8) | ((colorIndex & 0x1F) << 11);
    }

    public static int faceX(int packed) {
        return packed & 0xF;
    }

    public static int faceY(int packed) {
        return (packed >> 4) & 0xF;
    }

    public static int faceId(int packed) {
        return (packed >> 8) & 0x7;
    }

    public static int faceColor(int packed) {
        return (packed >> 11) & 0x1F;
    }
}
