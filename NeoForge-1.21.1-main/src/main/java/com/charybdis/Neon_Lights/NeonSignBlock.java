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
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class NeonSignBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public enum SideConnection implements StringRepresentable {
        NONE("none"),
        SIGN("sign"),
        SUPPORT("support");

        private final String name;

        SideConnection(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final MapCodec<NeonSignBlock> CODEC = simpleCodec(NeonSignBlock::new);

    public static final EnumProperty<SideConnection> CONNECT_UP = EnumProperty.create("connect_up", SideConnection.class);
    public static final EnumProperty<SideConnection> CONNECT_DOWN = EnumProperty.create("connect_down", SideConnection.class);
    public static final EnumProperty<SideConnection> CONNECT_LEFT = EnumProperty.create("connect_left", SideConnection.class);
    public static final EnumProperty<SideConnection> CONNECT_RIGHT = EnumProperty.create("connect_right", SideConnection.class);

    public static final BooleanProperty INNER_CORNER_UP_LEFT = BooleanProperty.create("inner_corner_up_left");
    public static final BooleanProperty INNER_CORNER_UP_RIGHT = BooleanProperty.create("inner_corner_up_right");
    public static final BooleanProperty INNER_CORNER_DOWN_LEFT = BooleanProperty.create("inner_corner_down_left");
    public static final BooleanProperty INNER_CORNER_DOWN_RIGHT = BooleanProperty.create("inner_corner_down_right");

    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    public static final int LIT_LIGHT_LEVEL = 10;

    private static final VoxelShape SHAPE_NORTH_SOUTH = Block.box(0, 0, 6, 16, 16, 10);
    private static final VoxelShape SHAPE_EAST_WEST = Block.box(6, 0, 0, 10, 16, 16);

    public NeonSignBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(CONNECT_UP, SideConnection.NONE)
                .setValue(CONNECT_DOWN, SideConnection.NONE)
                .setValue(CONNECT_LEFT, SideConnection.NONE)
                .setValue(CONNECT_RIGHT, SideConnection.NONE)
                .setValue(INNER_CORNER_UP_LEFT, false)
                .setValue(INNER_CORNER_UP_RIGHT, false)
                .setValue(INNER_CORNER_DOWN_LEFT, false)
                .setValue(INNER_CORNER_DOWN_RIGHT, false)
                .setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING,
                CONNECT_UP, CONNECT_DOWN, CONNECT_LEFT, CONNECT_RIGHT,
                INNER_CORNER_UP_LEFT, INNER_CORNER_UP_RIGHT, INNER_CORNER_DOWN_LEFT, INNER_CORNER_DOWN_RIGHT,
                LIT);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NeonSignBlockEntity(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
        return withConnections(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!level.isClientSide) {
            BlockState updated = withConnections(state, level, pos);
            if (updated != state) {
                level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
                state = updated;
            }
            refreshSignsAffectedByDiagonalChange(level, pos);
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
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        if (direction != Direction.UP
                && direction != Direction.DOWN
                && direction != facing.getClockWise()
                && direction != facing.getCounterClockWise()) {
            return state;
        }

        BlockState updated = withConnections(state, level, pos);
        refreshSignsAffectedByDiagonalChange(level, neighborPos);
        return updated;
    }

    private BlockState withConnections(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        Direction right = facing.getClockWise();
        Direction left = facing.getCounterClockWise();
        BlockPos up = pos.above();
        BlockPos down = pos.below();
        BlockPos rightPos = pos.relative(right);
        BlockPos leftPos = pos.relative(left);

        SideConnection connectUp = classify(level.getBlockState(up), level, up, facing);
        SideConnection connectDown = classify(level.getBlockState(down), level, down, facing);
        SideConnection connectRight = classify(level.getBlockState(rightPos), level, rightPos, facing);
        SideConnection connectLeft = classify(level.getBlockState(leftPos), level, leftPos, facing);

        boolean upJoined = connectUp == SideConnection.SIGN;
        boolean downJoined = connectDown == SideConnection.SIGN;
        boolean leftJoined = connectLeft == SideConnection.SIGN;
        boolean rightJoined = connectRight == SideConnection.SIGN;

        return state
                .setValue(CONNECT_UP, connectUp)
                .setValue(CONNECT_DOWN, connectDown)
                .setValue(CONNECT_RIGHT, connectRight)
                .setValue(CONNECT_LEFT, connectLeft)
                .setValue(INNER_CORNER_UP_RIGHT, upJoined && rightJoined && !isSameSign(level, up.relative(right), facing))
                .setValue(INNER_CORNER_UP_LEFT, upJoined && leftJoined && !isSameSign(level, up.relative(left), facing))
                .setValue(INNER_CORNER_DOWN_RIGHT, downJoined && rightJoined && !isSameSign(level, down.relative(right), facing))
                .setValue(INNER_CORNER_DOWN_LEFT, downJoined && leftJoined && !isSameSign(level, down.relative(left), facing));
    }

    private static void refreshSignsAffectedByDiagonalChange(LevelAccessor level, BlockPos changedPos) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Direction right = facing.getClockWise();
            Direction left = facing.getCounterClockWise();
            BlockPos[] candidates = {
                    changedPos.below().relative(left),
                    changedPos.below().relative(right),
                    changedPos.above().relative(left),
                    changedPos.above().relative(right),
            };
            for (BlockPos candidate : candidates) {
                BlockState candidateState = level.getBlockState(candidate);
                if (candidateState.getBlock() instanceof NeonSignBlock signBlock
                        && candidateState.getValue(FACING) == facing) {
                    BlockState refreshed = signBlock.withConnections(candidateState, level, candidate);
                    if (refreshed != candidateState) {
                        level.setBlock(candidate, refreshed, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private SideConnection classify(BlockState neighbor, BlockGetter level, BlockPos neighborPos, Direction facing) {
        if (neighbor.is(this)) {
            return neighbor.getValue(FACING) == facing ? SideConnection.SIGN : SideConnection.NONE;
        }
        return neighbor.isCollisionShapeFullBlock(level, neighborPos) ? SideConnection.SUPPORT : SideConnection.NONE;
    }

    private boolean isSameSign(LevelReader level, BlockPos pos, Direction facing) {
        BlockState neighbor = level.getBlockState(pos);
        return neighbor.is(this) && neighbor.getValue(FACING) == facing;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        boolean alongZ = facing == Direction.NORTH || facing == Direction.SOUTH;
        return alongZ ? SHAPE_NORTH_SOUTH : SHAPE_EAST_WEST;
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
        List<BlockPos> cluster = collectCluster(level, origin, block, facing);

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

    private static List<BlockPos> collectCluster(Level level, BlockPos origin, NeonSignBlock block, Direction facing) {
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
                if (neighborState.getBlock() == block && neighborState.getValue(FACING) == facing) {
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
