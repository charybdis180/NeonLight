package com.charybdis.Neon_Lights;

import com.charybdis.Neon_Lights.NeonSignBlock.FrameLayout;
import com.charybdis.Neon_Lights.NeonSignBlock.SideConnection;

public final class NeonSignFrameMask {

    private static final long[][] LOOKUP = buildLookupTable();

    private NeonSignFrameMask() {
    }

    public static boolean isEditable(FrameLayout layout, int x, int y) {
        if (x < 0 || x >= NeonSignGrid.PIXELS_W || y < 0 || y >= NeonSignGrid.PIXELS_H) {
            return false;
        }
        long[] masks = LOOKUP[layoutKey(layout)];
        int bit = NeonSignGrid.index(x, y);
        return (masks[bit >>> 6] & (1L << (bit & 63))) != 0L;
    }

    private static int layoutKey(FrameLayout layout) {
        int key = 0;
        key |= sideBit(layout.up(), 0);
        key |= sideBit(layout.down(), 2);
        key |= sideBit(layout.left(), 4);
        key |= sideBit(layout.right(), 6);
        if (layout.innerUpLeft()) key |= 1 << 8;
        if (layout.innerUpRight()) key |= 1 << 9;
        if (layout.innerDownLeft()) key |= 1 << 10;
        if (layout.innerDownRight()) key |= 1 << 11;
        return key;
    }

    private static int sideBit(SideConnection connection, int shift) {
        return switch (connection) {
            case NONE -> 0;
            case SIGN -> 1 << shift;
            case SUPPORT -> 2 << shift;
        };
    }

    private static long[][] buildLookupTable() {
        long[][] table = new long[4096][];
        for (int key = 0; key < table.length; key++) {
            table[key] = buildMask(decodeLayout(key));
        }
        return table;
    }

    private static FrameLayout decodeLayout(int key) {
        SideConnection up = decodeSide((key >> 0) & 3);
        SideConnection down = decodeSide((key >> 2) & 3);
        SideConnection left = decodeSide((key >> 4) & 3);
        SideConnection right = decodeSide((key >> 6) & 3);
        boolean innerUpLeft = (key & (1 << 8)) != 0;
        boolean innerUpRight = (key & (1 << 9)) != 0;
        boolean innerDownLeft = (key & (1 << 10)) != 0;
        boolean innerDownRight = (key & (1 << 11)) != 0;
        return new FrameLayout(up, down, left, right, innerUpLeft, innerUpRight, innerDownLeft, innerDownRight);
    }

    private static SideConnection decodeSide(int value) {
        return switch (value) {
            case 1 -> SideConnection.SIGN;
            case 2 -> SideConnection.SUPPORT;
            default -> SideConnection.NONE;
        };
    }

    private static long[] buildMask(FrameLayout layout) {
        long[] masks = new long[4];
        SideConnection sign = SideConnection.SIGN;
        for (int y = 0; y < NeonSignGrid.PIXELS_H; y++) {
            for (int x = 0; x < NeonSignGrid.PIXELS_W; x++) {
                if (computeEditable(layout, x, y, sign)) {
                    int bit = NeonSignGrid.index(x, y);
                    masks[bit >>> 6] |= 1L << (bit & 63);
                }
            }
        }
        return masks;
    }

    private static boolean computeEditable(FrameLayout f, int x, int y, SideConnection sign) {
        if (f.up() != sign && y >= 14) {
            return false;
        }
        if (f.down() != sign && y <= 1) {
            return false;
        }
        if (f.left() != sign && x <= 1) {
            return false;
        }
        if (f.right() != sign && x >= 14) {
            return false;
        }
        if (f.innerUpLeft() && x <= 1 && y >= 14) {
            return false;
        }
        if (f.innerUpRight() && x >= 14 && y >= 14) {
            return false;
        }
        if (f.innerDownLeft() && x <= 1 && y <= 1) {
            return false;
        }
        if (f.innerDownRight() && x >= 14 && y <= 1) {
            return false;
        }
        return true;
    }
}
