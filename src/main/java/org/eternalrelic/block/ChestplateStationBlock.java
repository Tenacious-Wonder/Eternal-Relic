package org.eternalrelic.block;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import org.eternalrelic.block.entity.ChestplateStationBlockEntity;
import org.eternalrelic.screen.ChestplateStationScreenHandler;

/**
 * <h1>胸甲台</h1>
 *
 * <p>
 * 给胸甲装卸盔甲配件的工作方块，四个部位台子中的第一个。台面是展示位：摆上去的胸甲按穿戴姿态显示。
 * 台上的胸甲由方块实体保存（见 {@link ChestplateStationBlockEntity}），方块模型只画台座，
 * 胸甲由客户端渲染器绘制
 * （见 {@code client.render.blockentity.ChestplateStationBlockEntityRenderer}）。
 * 本类只接受胸甲，头盔、护腿、靴子各有对应的台子。
 * </p>
 *
 * <h2>交互流程</h2>
 * <ol>
 *     <li>手持胸甲右键 —— 摆上台面，台上原有的那件还给玩家；</li>
 *     <li>空手右键 —— 打开界面，台面空着时不开（没有东西可装配）；</li>
 *     <li>按住 Shift 空手右键 —— 取回台上的胸甲。</li>
 * </ol>
 *
 * <p>
 * 台面数据在两端都会改：客户端立刻改本地那一份，台上的胸甲不必等网络往返就换好；
 * 玩家背包的结算（扣下手中的、把换下来的还给玩家）与打开界面只在服务端做，否则两边各算一次。
 * </p>
 *
 * @see ChestplateStationBlockEntity
 */
public class ChestplateStationBlock extends BlockWithEntity {
    /** 台子的水平朝向，放置时取玩家的水平朝向（见 {@link #getPlacementState}）。 */
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    /** 台座：底下 2 像素厚的一整块，15×15 居中摆放，边缘留出一点余量。 */
    private static final VoxelShape BASE_SHAPE = Block.createCuboidShape(0.5D, 0.0D, 0.5D, 15.5D, 2.0D, 15.5D);

    /** 挂架的立柱：立在台面正中，与朝向无关。 */
    private static final VoxelShape POST_SHAPE = Block.createCuboidShape(7.0D, 2.0D, 7.0D, 9.0D, 16.0D, 9.0D);

    /** 朝向为南/北（横杆沿 X 轴）时的整体形状。 */
    private static final VoxelShape SHAPE_BAR_X = VoxelShapes.union(BASE_SHAPE, POST_SHAPE,
            Block.createCuboidShape(-4.0D, 16.0D, 7.0D, 20.0D, 18.0D, 9.0D));

    /** 朝向为东/西（横杆沿 Z 轴）时的整体形状。 */
    private static final VoxelShape SHAPE_BAR_Z = VoxelShapes.union(BASE_SHAPE, POST_SHAPE,
            Block.createCuboidShape(7.0D, 16.0D, -4.0D, 9.0D, 18.0D, 20.0D));

    public ChestplateStationBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing());
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
            BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof ChestplateStationBlockEntity station)) {
            return ActionResult.PASS;
        }

        ItemStack held = player.getStackInHand(hand);

        if (isChestplate(held)) {
            ItemStack previous = station.getChestplate();

            station.setChestplate(held.copyWithCount(1));

            // 客户端到此为止：手上与台上的物品结算交给服务端
            if (world.isClient) {
                return ActionResult.SUCCESS;
            }

            if (!player.getAbilities().creativeMode) {
                held.decrement(1);
            }

            giveBack(player, previous);
            return ActionResult.CONSUME;
        }

        if (player.isSneaking() && held.isEmpty()) {
            ItemStack taken = station.takeChestplate();

            // 客户端到此为止：取下的那件由服务端交到手里
            if (world.isClient) {
                return ActionResult.SUCCESS;
            }

            giveBack(player, taken);
            return ActionResult.CONSUME;
        }

        // 开界面只在服务端做，客户端只要把这次交互交给服务端即可
        if (held.isEmpty() && !station.getChestplate().isEmpty()) {
            if (world.isClient) {
                return ActionResult.SUCCESS;
            }

            openScreen(player, pos);
            return ActionResult.CONSUME;
        }

        return ActionResult.PASS;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!world.isClient
                && !state.isOf(newState.getBlock())
                && world.getBlockEntity(pos) instanceof ChestplateStationBlockEntity station) {
            ItemStack shown = station.getChestplate();

            if (!shown.isEmpty()) {
                world.spawnEntity(new ItemEntity(world,
                        pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, shown));
            }
        }

        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ChestplateStationBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return shapeOf(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return shapeOf(state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    /**
     * 取台子的形状：台座、立柱，以及挂胸甲的那根横杆。
     *
     * <p>
     * 横杆的方向随朝向改变——模型在朝南时横杆沿 X 轴，转到东/西之后横杆落在 Z 轴上，
     * 因此两种方向各准备一个形状。形状与方块模型（{@code models/block/chestplate_station.json}）一致。
     * </p>
     *
     * @param state 方块状态
     * @return 该朝向下的形状
     */
    private static VoxelShape shapeOf(BlockState state) {
        // 朝向落在 Z 轴（南/北）时横杆沿 X，其余情况沿 Z
        return state.get(FACING).getAxis() == Direction.Axis.Z ? SHAPE_BAR_X : SHAPE_BAR_Z;
    }

    /** 打开胸甲台的界面。 */
    private static void openScreen(PlayerEntity player, BlockPos pos) {
        player.openHandledScreen(new ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(ServerPlayerEntity serverPlayer, PacketByteBuf buf) {
                // 界面要显示台上的胸甲与其已装配件，这些都在方块实体里，客户端需要位置才能取到同一份
                buf.writeBlockPos(pos);
            }

            @Override
            public Text getDisplayName() {
                return Text.translatable("container.eternal_relic.chestplate_station");
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity menuPlayer) {
                return new ChestplateStationScreenHandler(syncId, playerInventory, pos);
            }
        });
    }

    private static boolean isChestplate(ItemStack stack) {
        // 按部位判定而非物品名单，其它模组添加的胸甲同样可以放上来
        return stack.getItem() instanceof ArmorItem armor && armor.getSlotType() == EquipmentSlot.CHEST;
    }

    private static void giveBack(PlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
    }
}
