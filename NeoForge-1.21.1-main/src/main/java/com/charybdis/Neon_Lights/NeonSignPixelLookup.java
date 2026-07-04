package com.charybdis.Neon_Lights;

import com.charybdis.Neon_Lights.NeonSignBlock.FrameLayout;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class NeonSignPixelLookup {

    private final Level level;
    private final BlockPos signPos;
    private final BlockState signState;
    private final Direction facing;
    private final boolean[] occupied;

    public NeonSignPixelLookup(Level level, BlockPos signPos, BlockState signState, boolean[] occupied) {
        this.level = level;
        this.signPos = signPos;
        this.signState = signState;
        this.facing = signState.getValue(NeonSignBlock.FACING);
        this.occupied = occupied;
    }

    public boolean hasFilledNeighbor(int x, int y, int dx, int dy) {
        int nx = x + dx;
        int ny = y + dy;
        if (nx >= 0 && nx < NeonSignGrid.PIXELS_W && ny >= 0 && ny < NeonSignGrid.PIXELS_H) {
            return occupied[NeonSignGrid.index(nx, ny)];
        }
        return hasCrossBlockNeighbor(nx, ny);
    }

    private boolean hasCrossBlockNeighbor(int nx, int ny) {
        Direction right = facing.getClockWise();
        BlockPos neighborPos = signPos;
        if (nx < 0) {
            neighborPos = signPos.relative(right.getOpposite());
            nx = NeonSignGrid.PIXELS_W - 1;
        } else if (nx >= NeonSignGrid.PIXELS_W) {
            neighborPos = signPos.relative(right);
            nx = 0;
        } else if (ny < 0) {
            neighborPos = signPos.below();
            ny = NeonSignGrid.PIXELS_H - 1;
        } else if (ny >= NeonSignGrid.PIXELS_H) {
            neighborPos = signPos.above();
            ny = 0;
        } else {
            return false;
        }

        BlockState neighborState = level.getBlockState(neighborPos);
        if (!NeonSignBlock.connectsAsSign(signState, neighborState)) {
            return false;
        }
        if (!(level.getBlockEntity(neighborPos) instanceof NeonSignBlockEntity neighborSign)) {
            return false;
        }
        byte[] neighborPixels = neighborSign.getPixelsForRender();
        if (neighborPixels == null) {
            return false;
        }
        FrameLayout neighborFrame = neighborSign.renderFrameLayout();
        if (!NeonSignFrameMask.isEditable(neighborFrame, nx, ny)) {
            return false;
        }
        return (neighborPixels[NeonSignGrid.index(nx, ny)] & 0xFF) != NeonSignGrid.COLOR_EMPTY;
    }

    public static boolean[] buildOccupancy(byte[] pixels, FrameLayout frame) {
        boolean[] occ = new boolean[NeonSignGrid.PIXEL_COUNT];
        for (int i = 0; i < NeonSignGrid.PIXEL_COUNT; i++) {
            if ((pixels[i] & 0xFF) == NeonSignGrid.COLOR_EMPTY) {
                continue;
            }
            int x = NeonSignGrid.xOf(i);
            int y = NeonSignGrid.yOf(i);
            if (NeonSignFrameMask.isEditable(frame, x, y)) {
                occ[i] = true;
            }
        }
        return occ;
    }
}
