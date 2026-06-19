package com.charybdis.Neon_Lights;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class NeonSignBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public enum SideConnection {
        NONE,
        SIGN,
        SUPPORT
    }

    public record FrameLayout(
            SideConnection up, SideConnection down, SideConnection left, SideConnection right,
            boolean innerUpLeft, boolean innerUpRight, boolean innerDownLeft, boolean innerDownRight) {
    }

    public static final MapCodec<NeonSignBlock> CODEC = simpleCodec(NeonSignBlock::new);

    public static final BooleanProperty MOUNTED = BooleanProperty.create("mounted");

    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    public static final int LIT_LIGHT_LEVEL = 10;

    public static final double MOUNTED_DEPTH = 6.5D / 16.0D;

    private static final VoxelShape SHAPE_NORTH_SOUTH = Block.box(0, 0, 6, 16, 16, 10);
    private static final VoxelShape SHAPE_EAST_WEST = Block.box(6, 0, 0, 10, 16, 16);

    private static final VoxelShape MOUNTED_SHAPE_NORTH = Block.box(0, 0, 12, 16, 16, 16);
    private static final VoxelShape MOUNTED_SHAPE_SOUTH = Block.box(0, 0, 0, 16, 16, 4);
    private static final VoxelShape MOUNTED_SHAPE_EAST = Block.box(0, 0, 0, 4, 16, 16);
    private static final VoxelShape MOUNTED_SHAPE_WEST = Block.box(12, 0, 0, 16, 16, 16);

    public NeonSignBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(MOUNTED, false)
                .setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MOUNTED, LIT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NeonSignBlockEntity(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        Direction lookOpposite = context.getHorizontalDirection().getOpposite();

        boolean headOn = clickedFace.getAxis().isHorizontal() && clickedFace == lookOpposite;
        boolean wallMount = headOn && !context.isSecondaryUseActive();

        return this.defaultBlockState()
                .setValue(FACING, lookOpposite)
                .setValue(MOUNTED, wallMount);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!level.isClientSide) {
            refreshClusterLight(level, pos);
        }
        super.onPlace(state, level, pos, oldState, isMoving);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide) {
            refreshClusterLight(level, pos);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        if (state.getValue(MOUNTED)) {
            return switch (facing) {
                case SOUTH -> MOUNTED_SHAPE_SOUTH;
                case EAST -> MOUNTED_SHAPE_EAST;
                case WEST -> MOUNTED_SHAPE_WEST;
                default -> MOUNTED_SHAPE_NORTH;
            };
        }
        boolean alongZ = facing == Direction.NORTH || facing == Direction.SOUTH;
        return alongZ ? SHAPE_NORTH_SOUTH : SHAPE_EAST_WEST;
    }

    public static FrameLayout computeFrame(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof NeonSignBlock block)) {
            return new FrameLayout(SideConnection.NONE, SideConnection.NONE, SideConnection.NONE,
                    SideConnection.NONE, false, false, false, false);
        }
        Direction facing = state.getValue(FACING);
        boolean mounted = state.getValue(MOUNTED);
        Direction right = facing.getClockWise();
        Direction left = facing.getCounterClockWise();
        BlockPos up = pos.above();
        BlockPos down = pos.below();
        BlockPos rightPos = pos.relative(right);
        BlockPos leftPos = pos.relative(left);

        SideConnection connectUp = classify(level.getBlockState(up), level, up, facing, mounted, block);
        SideConnection connectDown = classify(level.getBlockState(down), level, down, facing, mounted, block);
        SideConnection connectRight = classify(level.getBlockState(rightPos), level, rightPos, facing, mounted, block);
        SideConnection connectLeft = classify(level.getBlockState(leftPos), level, leftPos, facing, mounted, block);

        boolean upJoined = connectUp == SideConnection.SIGN;
        boolean downJoined = connectDown == SideConnection.SIGN;
        boolean leftJoined = connectLeft == SideConnection.SIGN;
        boolean rightJoined = connectRight == SideConnection.SIGN;

        return new FrameLayout(connectUp, connectDown, connectLeft, connectRight,
                upJoined && leftJoined && !isSameSign(level, up.relative(left), facing, mounted, block),
                upJoined && rightJoined && !isSameSign(level, up.relative(right), facing, mounted, block),
                downJoined && leftJoined && !isSameSign(level, down.relative(left), facing, mounted, block),
                downJoined && rightJoined && !isSameSign(level, down.relative(right), facing, mounted, block));
    }

    private static SideConnection classify(BlockState neighbor, BlockGetter level, BlockPos neighborPos,
                                           Direction facing, boolean mounted, NeonSignBlock block) {
        if (neighbor.is(block)) {
            return (neighbor.getValue(FACING) == facing && neighbor.getValue(MOUNTED) == mounted)
                    ? SideConnection.SIGN : SideConnection.NONE;
        }
        if (mounted) {
            return SideConnection.NONE;
        }
        return neighbor.isCollisionShapeFullBlock(level, neighborPos) ? SideConnection.SUPPORT : SideConnection.NONE;
    }

    private static boolean isSameSign(BlockGetter level, BlockPos pos, Direction facing, boolean mounted, NeonSignBlock block) {
        BlockState neighbor = level.getBlockState(pos);
        return neighbor.is(block) && neighbor.getValue(FACING) == facing && neighbor.getValue(MOUNTED) == mounted;
    }

    public static void refreshClusterLight(Level level, BlockPos origin) {
        if (level.isClientSide) {
            return;
        }
        BlockState originState = level.getBlockState(origin);
        if (!(originState.getBlock() instanceof NeonSignBlock block)) {
            return;
        }
        Direction facing = originState.getValue(FACING);
        boolean mounted = originState.getValue(MOUNTED);
        List<BlockPos> cluster = collectCluster(level, origin, block, facing, mounted);

        boolean clusterPowered = false;
        for (BlockPos pos : cluster) {
            if (level.hasNeighborSignal(pos)) {
                clusterPowered = true;
                break;
            }
        }

        for (BlockPos pos : cluster) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof NeonSignBlock)) {
                continue;
            }
            boolean hasGlyph = level.getBlockEntity(pos) instanceof NeonSignBlockEntity sign
                    && !sign.getCharacter().isEmpty();
            boolean shouldBeLit = hasGlyph && !clusterPowered;
            if (state.getValue(LIT) != shouldBeLit) {
                level.setBlock(pos, state.setValue(LIT, shouldBeLit), Block.UPDATE_CLIENTS);
            }
        }
    }

    private static List<BlockPos> collectCluster(Level level, BlockPos origin, NeonSignBlock block, Direction facing, boolean mounted) {
        Direction right = facing.getClockWise();
        Direction left = facing.getCounterClockWise();

        List<BlockPos> cluster = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();

        BlockPos start = origin.immutable();
        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            cluster.add(pos);
            BlockPos[] neighbors = {
                    pos.above(), pos.below(), pos.relative(left), pos.relative(right),
            };
            for (BlockPos rawNeighbor : neighbors) {
                BlockPos neighbor = rawNeighbor.immutable();
                if (visited.contains(neighbor)) {
                    continue;
                }
                BlockState neighborState = level.getBlockState(neighbor);
                if (neighborState.getBlock() == block
                        && neighborState.getValue(FACING) == facing
                        && neighborState.getValue(MOUNTED) == mounted) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        return cluster;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            Neon_LightsClient.openSignScreen(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
