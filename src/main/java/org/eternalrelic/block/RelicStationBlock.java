package org.eternalrelic.block;

import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 遗物装卸台 —— 既能给装备附上遗物、也能把遗物拆下来的工作方块。
 *
 * <p>它自己几乎不含逻辑：东西存在 {@link RelicStationBlockEntity} 里，
 * 怎么装卸由界面容器与遗物附着表决定。这个类只负责两件事——
 * 造出方块实体，以及被拆掉时把里面的东西还给玩家。</p>
 *
 * <p><b>它长什么样</b>：外观就是一个普通的方块模型（{@code models/block/relic_station.json}），
 * 由制作者在 Blockbench 里做好、按原版方块模型的规矩导出，游戏自己烘、自己打光、自己上贴图。
 * 因此这里返回 {@link BlockRenderType#MODEL}——原来那套自写渲染器已经全部删掉了。</p>
 */
public class RelicStationBlock extends BlockWithEntity {

    public RelicStationBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new RelicStationBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    /**
     * 台子被拆掉时，把里面存着的东西一并还给玩家。
     *
     * <p>方块自己由掉落表掉落，但存在方块实体里的东西不会跟着掉——不还回去就等于凭空吞掉了
     * 玩家放进去的装备与遗物。</p>
     *
     * <p><b>⚠️ 只还"前三格"，预览格绝不掉出来。</b>预览是台子自己摆的一份成品副本，
     * 不是玩家的东西；把它掉出来就等于"摆好材料、关掉界面、再拆掉台子"白得一份。
     * 因此循环的边界是 {@link RelicStationBlockEntity#PERSISTED_SLOTS} 而不是 {@code size()}，
     * <b>改动它之前请先想清楚这一点</b>。</p>
     *
     * <p>另外这里加了"只在服务端生成掉落物"的保护：原版同类写法（{@code ItemScatterer}）
     * 内部就带这道判断，手写时容易漏，漏了客户端也会生成一遍、视觉上一瞬间多一份。</p>
     */
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!world.isClient && !state.isOf(newState.getBlock())) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof RelicStationBlockEntity station) {
                for (int slot = 0; slot < RelicStationBlockEntity.PERSISTED_SLOTS; slot++) {
                    ItemStack stack = station.getStack(slot);
                    if (stack.isEmpty()) {
                        continue;
                    }

                    ItemEntity dropped = new ItemEntity(world,
                            pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack.copy());
                    dropped.setToDefaultPickupDelay();
                    world.spawnEntity(dropped);
                }
            }
        }

        super.onStateReplaced(state, world, pos, newState, moved);
    }

    /**
     * 右键打开装卸界面。
     *
     * @param state  方块状态
     * @param world  所在世界
     * @param pos    方块位置
     * @param player 点它的玩家
     * @param hand   用的哪只手（本方块不区分）
     * @param hit    命中详情（本方块用不到）
     * @return 是否已处理这次交互
     */
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
            BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }

        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof RelicStationBlockEntity station) {
            player.openHandledScreen(station);
        }

        return ActionResult.CONSUME;
    }
}
