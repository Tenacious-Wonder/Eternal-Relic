package org.eternalrelic.block;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import org.eternalrelic.block.entity.ChestplateStationBlockEntity;
import org.eternalrelic.screen.ChestplateStationScreenHandler;

/**
 * 胸甲台 —— 给胸甲装卸盔甲配件的工作方块，也是四个部位台子里的第一个。
 *
 * <p><b>它同时是工作台和展示台。</b>平时胸甲就摆在台子上、按穿在身上的样子显示出来；
 * 要动手时再打开界面，把配料与配件敲上去、或者把已装的配件拆下来。
 * 因此这台子必须自己记住台上的胸甲（见 {@link ChestplateStationBlockEntity}），
 * 存档、重启都不能丢——这是它与遗物装卸台最大的不同，那个台子什么都不存。</p>
 *
 * <p><b>它只管胸甲。</b>头盔、护腿、靴子各有自己的台子，以后照这一套复制即可；
 * 因此这里凡是判断"能不能放"的地方都只认胸甲，不接受别的部位。</p>
 *
 * <p><b>台上的胸甲由客户端那边的渲染器画出来</b>
 * （见 {@code client.render.block.blockentity.ChestplateStationBlockEntityRenderer}），
 * 方块模型本身只是台座，两者分开。</p>
 */
public class ChestplateStationBlock extends Block implements BlockEntityProvider {

    /**
     * 台子的实际形状：只有下面 2 像素高的一块底座。
     *
     * <p>必须与方块模型一致。少了它，台子看着很矮，玩家站上去却会浮在一整格的高度。</p>
     */
    private static final VoxelShape SHAPE = Block.createCuboidShape(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);

    public ChestplateStationBlock(Settings settings) {
        super(settings);
    }

    /**
     * 右键的三种用法，与制作者定下的按键习惯一致：
     *
     * <ul>
     *   <li><b>手持胸甲</b> —— 摆到台子上（台上原有的那件还给玩家）；</li>
     *   <li><b>空手</b> —— 打开界面；台子上没有胸甲时不开（没有东西可装配）；</li>
     *   <li><b>按住 Shift + 空手</b> —— 把台上的胸甲取回来。</li>
     * </ul>
     *
     * @param state  方块状态
     * @param world  所在世界
     * @param pos    方块位置
     * @param player 点它的玩家
     * @param hand   用的哪只手
     * @param hit    命中详情（本方块用不到）
     * @return 是否已处理这次交互
     */
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
            BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }

        if (!(world.getBlockEntity(pos) instanceof ChestplateStationBlockEntity station)) {
            return ActionResult.PASS;
        }

        ItemStack held = player.getStackInHand(hand);

        // 手里拿着胸甲：摆上去。台上原有的那件不算丢掉，还给玩家
        if (isChestplate(held)) {
            ItemStack previous = station.getChestplate();

            station.setChestplate(held.copyWithCount(1));
            if (!player.getAbilities().creativeMode) {
                held.decrement(1);
            }

            giveBack(player, previous);
            return ActionResult.CONSUME;
        }

        // 按住 Shift + 空手：把台上的胸甲取回来
        if (player.isSneaking() && held.isEmpty()) {
            ItemStack shown = station.getChestplate();

            if (!shown.isEmpty()) {
                station.setChestplate(ItemStack.EMPTY);
                giveBack(player, shown);
            }

            return ActionResult.CONSUME;
        }

        // 空手：开界面。台上空着就不开——没有东西可装配
        if (held.isEmpty() && !station.getChestplate().isEmpty()) {
            openScreen(player, pos);
            return ActionResult.CONSUME;
        }

        return ActionResult.PASS;
    }

    /**
     * 方块被拆掉（或被别的方块换掉）时，把台上的胸甲掉出来。
     *
     * <p>少了这一步，玩家一镐子下去就把自己的胸甲连同方块一起弄没了。</p>
     */
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

    /**
     * 造出这个台子的方块实体。
     *
     * @param pos   方块位置
     * @param state 方块状态
     * @return 新的方块实体
     */
    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ChestplateStationBlockEntity(pos, state);
    }

    /**
     * @return 准星选中框的形状（与台座的模型一致，不圈住整个方块）
     */
    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    /**
     * @return 碰撞形状（与台座的模型一致，玩家可以正常站上台子）
     */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    // ==================== 开界面 ====================

    /**
     * 打开胸甲台的界面，并把台子的位置一并告诉客户端。
     *
     * <p><b>为什么要多说这一句</b>：界面上要显示台上那件胸甲、以及它已经装了哪些配件，
     * 而这些东西都在方块实体里。客户端只有拿到位置，才能取到同一个方块实体。
     * 这是与遗物装卸台不同的地方——那个台子的内容全在服务端一次性下发，不需要位置。</p>
     *
     * @param player 开界面的玩家
     * @param pos    台子的位置
     */
    private static void openScreen(PlayerEntity player, BlockPos pos) {
        player.openHandledScreen(new ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(ServerPlayerEntity serverPlayer, PacketByteBuf buf) {
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

    /**
     * 这件东西是不是本台子收的胸甲。
     *
     * <p>照原版的部位判定来认，不写死一份物品名单——这样别的模组加的胸甲也能放上来。</p>
     *
     * @param stack 待判断的物品
     * @return 是穿在胸前的防具时返回 {@code true}
     */
    private static boolean isChestplate(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem armor && armor.getSlotType() == EquipmentSlot.CHEST;
    }

    /**
     * 把一份东西塞回玩家背包；塞不下就丢在他脚边。
     *
     * @param player 收东西的玩家
     * @param stack  要还回去的东西；空则什么也不做
     */
    private static void giveBack(PlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
    }
}
