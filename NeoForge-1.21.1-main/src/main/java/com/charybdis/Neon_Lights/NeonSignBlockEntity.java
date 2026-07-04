package com.charybdis.Neon_Lights;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class NeonSignBlockEntity extends BlockEntity {
    public static final int MAX_CUSTOM_NAME_LENGTH = 32;

    private String character = "";
    private String customName = "";
    private int glyphColor = NeonSignGrid.COLOR_EMPTY;
    private byte[] pixels;
    private boolean hasAnyPixels;
    private boolean canvasMember;

    // Client render caches: rebuilt at most once per game tick to avoid per-frame
    // neighbor scans and array allocations. Frame layout only changes when a neighbor
    // block is placed/removed, so a sub-tick staleness window is imperceptible.
    private NeonSignBlock.FrameLayout cachedFrame;
    private long cachedFrameTick = Long.MIN_VALUE;
    private boolean[] cachedOccupancy;
    private long cachedOccupancyTick = Long.MIN_VALUE;
    private int[] pixelMesh;
    private int pixelMeshCount;
    private long pixelMeshTick = Long.MIN_VALUE;

    public NeonSignBlockEntity(BlockPos pos, BlockState state) {
        super(Neon_Lights.NEON_SIGN_BE.get(), pos, state);
    }

    public String getCharacter() {
        return character;
    }

    public void setCharacter(String character) {
        this.character = (character == null) ? "" : character;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            if (!level.isClientSide) {
                NeonSignBlock.refreshClusterLight(level, worldPosition);
            }
        }
    }

    public boolean hasAnyPixels() {
        return hasAnyPixels;
    }

    /** True once this sign has been committed as part of a design-tool canvas (locks glyph editing & re-pooling). */
    public boolean isCanvasMember() {
        return canvasMember;
    }

    void setCanvasMemberFromBatch(boolean member) {
        this.canvasMember = member;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = sanitizeCustomName(customName);
        markUpdated();
    }

    void setCustomNameFromBatch(String customName) {
        this.customName = sanitizeCustomName(customName);
    }

    /** 0 = inherit the sign/frame color, 1-16 = dye, 17 = rainbow. */
    public int getGlyphColor() {
        return glyphColor;
    }

    public void setGlyphColor(int colorIndex) {
        if (!NeonSignGrid.isValidColorIndex(colorIndex)) {
            return;
        }
        this.glyphColor = colorIndex;
        markUpdated();
    }

    public static String sanitizeCustomName(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_CUSTOM_NAME_LENGTH) {
            trimmed = trimmed.substring(0, MAX_CUSTOM_NAME_LENGTH);
        }
        StringBuilder builder = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c >= 32 && c != 127) {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    byte[] getPixelsForRender() {
        return pixels;
    }

    /** Frame layout for rendering, cached per game tick to avoid per-frame neighbor scans. */
    public NeonSignBlock.FrameLayout renderFrameLayout() {
        if (level == null) {
            return cachedFrame != null ? cachedFrame : NeonSignBlock.emptyFrame();
        }
        long tick = level.getGameTime();
        if (cachedFrame == null || tick != cachedFrameTick) {
            cachedFrame = NeonSignBlock.computeFrame(level, worldPosition, getBlockState());
            cachedFrameTick = tick;
        }
        return cachedFrame;
    }

    /** Pixel occupancy mask for face-culling, cached per game tick. */
    public boolean[] renderOccupancy(byte[] pixelsForRender, NeonSignBlock.FrameLayout frame) {
        long tick = level == null ? 0L : level.getGameTime();
        if (cachedOccupancy == null || tick != cachedOccupancyTick) {
            cachedOccupancy = NeonSignPixelLookup.buildOccupancy(pixelsForRender, frame);
            cachedOccupancyTick = tick;
        }
        return cachedOccupancy;
    }

    public int[] pixelMesh() {
        return pixelMesh;
    }

    public int pixelMeshCount() {
        return pixelMeshCount;
    }

    public long pixelMeshTick() {
        return pixelMeshTick;
    }

    public void setPixelMesh(int[] mesh, int count, long tick) {
        this.pixelMesh = mesh;
        this.pixelMeshCount = count;
        this.pixelMeshTick = tick;
    }

    public byte[] getPixelsCopy() {
        if (pixels == null) {
            return null;
        }
        return pixels.clone();
    }

    public byte getPixel(int x, int y) {
        if (pixels == null) {
            return NeonSignGrid.COLOR_EMPTY;
        }
        return pixels[NeonSignGrid.index(x, y)];
    }

    public void setPixel(int x, int y, int colorIndex) {
        if (colorIndex == NeonSignGrid.COLOR_EMPTY) {
            if (pixels != null) {
                pixels[NeonSignGrid.index(x, y)] = NeonSignGrid.COLOR_EMPTY;
                compactPixels();
            }
            return;
        }
        if (!NeonSignGrid.isValidColorIndex(colorIndex)) {
            return;
        }
        ensurePixels();
        pixels[NeonSignGrid.index(x, y)] = (byte) colorIndex;
        hasAnyPixels = true;
    }

    public void setPixels(byte[] source) {
        applyPixels(source, true);
    }

    void setPixelsWithoutNotify(byte[] source) {
        applyPixels(source, false);
    }

    private void applyPixels(byte[] source, boolean notify) {
        if (source == null || source.length != NeonSignGrid.PIXEL_COUNT) {
            if (notify) {
                clearPixels();
            } else {
                pixels = null;
                hasAnyPixels = false;
            }
            return;
        }
        boolean any = false;
        for (byte value : source) {
            if (value != NeonSignGrid.COLOR_EMPTY) {
                any = true;
                break;
            }
        }
        if (!any) {
            if (notify) {
                clearPixels();
            } else {
                pixels = null;
                hasAnyPixels = false;
            }
            return;
        }
        ensurePixels();
        System.arraycopy(source, 0, pixels, 0, NeonSignGrid.PIXEL_COUNT);
        hasAnyPixels = true;
        if (notify) {
            markUpdated();
        }
    }

    public void clearPixels() {
        pixels = null;
        hasAnyPixels = false;
        markUpdated();
    }

    private void ensurePixels() {
        if (pixels == null) {
            pixels = new byte[NeonSignGrid.PIXEL_COUNT];
        }
    }

    private void compactPixels() {
        if (pixels == null) {
            hasAnyPixels = false;
            return;
        }
        for (byte value : pixels) {
            if (value != NeonSignGrid.COLOR_EMPTY) {
                hasAnyPixels = true;
                markUpdated();
                return;
            }
        }
        pixels = null;
        hasAnyPixels = false;
        markUpdated();
    }

    private void markUpdated() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            if (!level.isClientSide) {
                NeonSignBlock.refreshClusterLight(level, worldPosition);
            }
        }
    }

    void markUpdatedFromBatch() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Char", character);
        if (!customName.isEmpty()) {
            tag.putString("CustomName", customName);
        }
        if (glyphColor != NeonSignGrid.COLOR_EMPTY) {
            tag.putInt("GlyphColor", glyphColor);
        }
        if (canvasMember) {
            tag.putBoolean("CanvasMember", true);
        }
        if (pixels != null && hasAnyPixels) {
            tag.putByteArray("Pixels", pixels);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        character = tag.getString("Char");
        customName = tag.contains("CustomName") ? sanitizeCustomName(tag.getString("CustomName")) : "";
        glyphColor = tag.contains("GlyphColor") ? tag.getInt("GlyphColor") : NeonSignGrid.COLOR_EMPTY;
        if (!NeonSignGrid.isValidColorIndex(glyphColor)) {
            glyphColor = NeonSignGrid.COLOR_EMPTY;
        }
        canvasMember = tag.getBoolean("CanvasMember");
        if (tag.contains("Pixels")) {
            byte[] loaded = tag.getByteArray("Pixels");
            if (loaded.length == NeonSignGrid.PIXEL_COUNT) {
                pixels = loaded;
                hasAnyPixels = false;
                for (byte value : pixels) {
                    if (value != NeonSignGrid.COLOR_EMPTY) {
                        hasAnyPixels = true;
                        break;
                    }
                }
                if (!hasAnyPixels) {
                    pixels = null;
                }
            } else {
                pixels = null;
                hasAnyPixels = false;
            }
        } else {
            pixels = null;
            hasAnyPixels = false;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putString("Char", character);
        if (!customName.isEmpty()) {
            tag.putString("CustomName", customName);
        }
        if (glyphColor != NeonSignGrid.COLOR_EMPTY) {
            tag.putInt("GlyphColor", glyphColor);
        }
        if (canvasMember) {
            tag.putBoolean("CanvasMember", true);
        }
        if (pixels != null && hasAnyPixels) {
            tag.putByteArray("Pixels", pixels);
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        if (packet.getTag() != null) {
            loadAdditional(packet.getTag(), registries);
        }
    }
}
