package com.charybdis.Neon_Lights;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.charybdis.Neon_Lights.NeonSignBlock.FrameLayout;

public final class NeonSignClusterLayout {

    public record CellCoord(int col, int row) {
    }

    public record SignCell(BlockPos pos, int col, int row, FrameLayout frameLayout) {
    }

    private final BlockPos origin;
    private final int minCol;
    private final int minRow;
    private final int signCols;
    private final int signRows;
    private final int canvasPixelW;
    private final int canvasPixelH;
    private final boolean[][] hasSign;
    private final Map<BlockPos, SignCell> cellsByPos;
    private final SignCell[][] grid;

    private NeonSignClusterLayout(BlockPos origin, int minCol, int minRow, int signCols, int signRows,
                                  boolean[][] hasSign, Map<BlockPos, SignCell> cellsByPos, SignCell[][] grid) {
        this.origin = origin;
        this.minCol = minCol;
        this.minRow = minRow;
        this.signCols = signCols;
        this.signRows = signRows;
        this.canvasPixelW = signCols * NeonSignGrid.PIXELS_W;
        this.canvasPixelH = signRows * NeonSignGrid.PIXELS_H;
        this.hasSign = hasSign;
        this.cellsByPos = cellsByPos;
        this.grid = grid;
    }

    public static NeonSignClusterLayout build(Level level, BlockPos origin) {
        BlockState originState = level.getBlockState(origin);
        if (!(originState.getBlock() instanceof NeonSignBlock)) {
            return null;
        }
        Direction facing = originState.getValue(NeonSignBlock.FACING);
        List<BlockPos> cluster = NeonSignBlock.collectCanvasCluster(level, origin, originState);
        if (cluster.isEmpty()) {
            return null;
        }

        Direction right = facing.getClockWise();
        Direction up = Direction.UP;

        int minCol = Integer.MAX_VALUE;
        int minRow = Integer.MAX_VALUE;
        int maxCol = Integer.MIN_VALUE;
        int maxRow = Integer.MIN_VALUE;
        Map<BlockPos, CellCoord> rawCoords = new HashMap<>();

        for (BlockPos pos : cluster) {
            int col = projectAlong(pos, origin, right);
            int row = projectAlong(pos, origin, up);
            rawCoords.put(pos, new CellCoord(col, row));
            minCol = Math.min(minCol, col);
            minRow = Math.min(minRow, row);
            maxCol = Math.max(maxCol, col);
            maxRow = Math.max(maxRow, row);
        }

        int signCols = maxCol - minCol + 1;
        int signRows = maxRow - minRow + 1;
        if (signCols > NeonSignGrid.MAX_CANVAS_SIGNS_PER_AXIS || signRows > NeonSignGrid.MAX_CANVAS_SIGNS_PER_AXIS) {
            return null;
        }

        boolean[][] hasSign = new boolean[signCols][signRows];
        Map<BlockPos, SignCell> cellsByPos = new HashMap<>();
        SignCell[][] grid = new SignCell[signCols][signRows];

        for (BlockPos pos : cluster) {
            CellCoord raw = rawCoords.get(pos);
            int col = raw.col() - minCol;
            int row = raw.row() - minRow;
            BlockState state = level.getBlockState(pos);
            FrameLayout frame = NeonSignBlock.computeFrame(level, pos, state);
            SignCell cell = new SignCell(pos, col, row, frame);
            hasSign[col][row] = true;
            cellsByPos.put(pos, cell);
            grid[col][row] = cell;
        }

        return new NeonSignClusterLayout(origin, minCol, minRow, signCols, signRows, hasSign, cellsByPos, grid);
    }

    private static int projectAlong(BlockPos pos, BlockPos ref, Direction dir) {
        BlockPos delta = pos.subtract(ref);
        return delta.getX() * dir.getStepX() + delta.getY() * dir.getStepY() + delta.getZ() * dir.getStepZ();
    }

    public byte[] loadCanvasFromWorld(Level level) {
        byte[] canvas = new byte[canvasPixelW * canvasPixelH];
        for (SignCell cell : cellsByPos.values()) {
            if (!(level.getBlockEntity(cell.pos()) instanceof NeonSignBlockEntity sign)) {
                continue;
            }
            byte[] pixels = sign.getPixelsCopy();
            if (pixels == null) {
                continue;
            }
            for (int localY = 0; localY < NeonSignGrid.PIXELS_H; localY++) {
                for (int localX = 0; localX < NeonSignGrid.PIXELS_W; localX++) {
                    int color = pixels[NeonSignGrid.index(localX, localY)] & 0xFF;
                    if (color == NeonSignGrid.COLOR_EMPTY) {
                        continue;
                    }
                    if (!NeonSignFrameMask.isEditable(cell.frameLayout(), localX, localY)) {
                        continue;
                    }
                    int canvasX = cell.col() * NeonSignGrid.PIXELS_W + localX;
                    int canvasY = cell.row() * NeonSignGrid.PIXELS_H + localY;
                    canvas[canvasIndex(canvasX, canvasY)] = (byte) color;
                }
            }
        }
        return canvas;
    }

    public String loadCustomName(Level level) {
        for (SignCell cell : cellsByPos.values()) {
            if (level.getBlockEntity(cell.pos()) instanceof NeonSignBlockEntity sign) {
                String name = sign.getCustomName();
                if (!name.isEmpty()) {
                    return name;
                }
            }
        }
        return "";
    }

    public void applyCanvasToWorld(Level level, byte[] canvas, String customName) {
        if (canvas.length != canvasPixelW * canvasPixelH) {
            return;
        }
        for (int col = 0; col < signCols; col++) {
            for (int row = 0; row < signRows; row++) {
                if (!hasSign[col][row]) {
                    continue;
                }
                SignCell cell = grid[col][row];
                if (!(level.getBlockEntity(cell.pos()) instanceof NeonSignBlockEntity sign)) {
                    continue;
                }
                byte[] blockPixels = new byte[NeonSignGrid.PIXEL_COUNT];
                for (int localY = 0; localY < NeonSignGrid.PIXELS_H; localY++) {
                    for (int localX = 0; localX < NeonSignGrid.PIXELS_W; localX++) {
                        int canvasX = col * NeonSignGrid.PIXELS_W + localX;
                        int canvasY = row * NeonSignGrid.PIXELS_H + localY;
                        int color = canvas[canvasIndex(canvasX, canvasY)] & 0xFF;
                        if (color == NeonSignGrid.COLOR_EMPTY || !NeonSignFrameMask.isEditable(cell.frameLayout(), localX, localY)) {
                            continue;
                        }
                        if (NeonSignGrid.isValidColorIndex(color)) {
                            blockPixels[NeonSignGrid.index(localX, localY)] = (byte) color;
                        }
                    }
                }
                sign.setPixelsWithoutNotify(blockPixels);
            }
        }
        for (SignCell cell : cellsByPos.values()) {
            if (level.getBlockEntity(cell.pos()) instanceof NeonSignBlockEntity sign) {
                sign.setCanvasMemberFromBatch(true);
                sign.setCustomNameFromBatch(customName);
                sign.markUpdatedFromBatch();
            }
            BlockState state = level.getBlockState(cell.pos());
            level.sendBlockUpdated(cell.pos(), state, state, Block.UPDATE_CLIENTS);
            NeonSignBlock.refreshFrameNeighbors(level, cell.pos(), state);
        }
        NeonSignBlock.refreshClusterLight(level, origin);
    }

    public int canvasIndex(int canvasX, int canvasY) {
        return canvasY * canvasPixelW + canvasX;
    }

    public SignCell cellAtCanvasPixel(int canvasX, int canvasY) {
        if (canvasX < 0 || canvasY < 0 || canvasX >= canvasPixelW || canvasY >= canvasPixelH) {
            return null;
        }
        int col = canvasX / NeonSignGrid.PIXELS_W;
        int row = canvasY / NeonSignGrid.PIXELS_H;
        if (col < 0 || row < 0 || col >= signCols || row >= signRows || !hasSign[col][row]) {
            return null;
        }
        return grid[col][row];
    }

    public int localX(int canvasX) {
        return canvasX % NeonSignGrid.PIXELS_W;
    }

    public int localY(int canvasY) {
        return canvasY % NeonSignGrid.PIXELS_H;
    }

    public boolean isCanvasPixelEditable(int canvasX, int canvasY) {
        SignCell cell = cellAtCanvasPixel(canvasX, canvasY);
        if (cell == null) {
            return false;
        }
        return NeonSignFrameMask.isEditable(cell.frameLayout(), localX(canvasX), localY(canvasY));
    }

    public BlockPos origin() {
        return origin;
    }

    public int minCol() {
        return minCol;
    }

    public int minRow() {
        return minRow;
    }

    public int signCols() {
        return signCols;
    }

    public int signRows() {
        return signRows;
    }

    public int canvasPixelW() {
        return canvasPixelW;
    }

    public int canvasPixelH() {
        return canvasPixelH;
    }

    public boolean hasSignAt(int col, int row) {
        return col >= 0 && row >= 0 && col < signCols && row < signRows && hasSign[col][row];
    }

    public SignCell cellAt(int col, int row) {
        if (!hasSignAt(col, row)) {
            return null;
        }
        return grid[col][row];
    }
}
