package org.eternalrelic.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.eternalrelic.screen.RelicStationScreenHandler;

/**
 * 遗物装卸台 —— 既能给装备附上遗物、也能把遗物拆下来的工作方块。
 *
 * <p><b>它自己什么也不存</b>：照原版工作台的样子，方块只是个开关 —— 右键开出界面，
 * 东西全在 {@link RelicStationScreenHandler} 那一次会话里，关掉界面就还给玩家。
 * 因此这里没有方块实体、没有存档、也没有"被拆掉时把里面的东西掉出来"这件事要做；
 * 连带一个大好处是<b>两个玩家同时开同一个台子时各用各的</b>。</p>
 *
 * <p><b>它长什么样</b>：外观就是一个普通的方块模型（{@code models/block/relic_station.json}），
 * 由制作者在 Blockbench 里做好、按原版方块模型的规矩导出，游戏自己烘、自己打光、自己上贴图。
 * 因此不需要指定渲染方式 —— 普通方块默认就是按模型渲染的。</p>
 *
 * <p>这个类只做一件事：告诉游戏"右键我该开哪个界面"。界面的内容、规矩与退还全在
 * {@link RelicStationScreenHandler} 里，本类不掺和。</p>
 */
public class RelicStationBlock extends Block {

    public RelicStationBlock(Settings settings) {
        super(settings);
    }

    /**
     * 右键打开装卸界面。
     *
     * <p>写法与原版工作台一致：客户端先应一声（让手臂摆动、方块有反应），服务端才真的开界面。</p>
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

        player.openHandledScreen(state.createScreenHandlerFactory(world, pos));
        return ActionResult.CONSUME;
    }

    /**
     * 造出装卸界面的容器。
     *
     * <p>把方块位置一并交给容器：它要用这个位置在台子那儿播放敲击声、
     * 以及在玩家走远或台子被拆掉时判定界面该不该关。位置是这一次打开时现取的，
     * <b>不是存下来的状态</b>——台子仍然只是一块普通的方块。</p>
     *
     * @param state 方块状态
     * @param world 所在世界
     * @param pos   方块位置
     * @return 打开界面用的工厂
     */
    @Override
    public NamedScreenHandlerFactory createScreenHandlerFactory(BlockState state, World world, BlockPos pos) {
        return new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, player) -> new RelicStationScreenHandler(syncId, playerInventory, pos),
                this.getName());
    }
}
