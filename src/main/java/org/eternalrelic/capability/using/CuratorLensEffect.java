package org.eternalrelic.capability.using;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.eternalrelic.registry.ModItems;

/**
 * 「举镜清点」能力：玩家举着馆藏透镜时，身边十三格内的箱子会浮起一枚印记。
 *
 * <p><b>为什么它单独成包</b>：遗物按「怎样才算生效」分成几类——装在身上生效的放
 * {@code capability.worn}，放在背包里生效的放 {@code capability.carried}，吃下去才生效的放
 * {@code capability.consumed}。馆藏透镜三者都不是：它要拿在手上按住右键才作数，所以自成一类
 * {@code capability.using}。分开的好处是以后再有「举起来才有用」的遗物，直接往这里放。</p>
 *
 * <p><b>这里只回答「哪些箱子该亮」，不负责画。</b>真正把印记画到屏幕上的事在客户端，
 * 见 {@code org.eternalrelic.client.render.world.ChestMarkRenderer}。</p>
 */
public final class CuratorLensEffect {

    /** 举镜时能从多远看清箱子（米）。 */
    private static final int SIGHT_RANGE = 13;

    private CuratorLensEffect() {
    }

    /**
     * 找出玩家此刻该浮起印记的箱子。
     *
     * <p>范围是一个以玩家脚下那一格为中心的球，而不是一个方盒子——按直线距离算，十三格外
     * 哪怕正对着也不会亮。范围不大（约九千个格子），且只在举镜期间才真的去数，平时这一步
     * 立刻返回空表，不产生任何开销。</p>
     *
     * @param player 举镜的玩家
     * @return 该浮起印记的箱子位置；没在举镜时返回空表
     */
    public static List<BlockPos> chestsInSight(PlayerEntity player) {
        if (!isLookingThroughLens(player)) {
            return List.of();
        }

        World world = player.getWorld();
        BlockPos center = player.getBlockPos();
        List<BlockPos> found = new ArrayList<>();

        // 游标复用同一对象，只在真的命中时才复制一份留下来；
        // 否则这一趟要凭空造出上万个小对象，白白给垃圾回收添活
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int rangeSquared = SIGHT_RANGE * SIGHT_RANGE;

        for (int dx = -SIGHT_RANGE; dx <= SIGHT_RANGE; dx++) {
            for (int dy = -SIGHT_RANGE; dy <= SIGHT_RANGE; dy++) {
                int planar = dx * dx + dy * dy;
                if (planar > rangeSquared) {
                    continue;
                }

                // 前两轴定下来之后，第三轴能走多远就定死了：先算好，里面那层循环便不必再逐格判距离
                int span = (int) Math.sqrt(rangeSquared - planar);

                for (int dz = -span; dz <= span; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (isChest(world.getBlockState(cursor))) {
                        found.add(cursor.toImmutable());
                    }
                }
            }
        }

        return found;
    }

    /**
     * 玩家此刻是否正举着馆藏透镜。
     *
     * <p>判据是「正在使用物品」加上「手上那件是馆藏透镜」。这里不去问玩家的背包——透镜必须
     * 拿在手上举起来才算数，放在背包里躺着不算。</p>
     *
     * @param player 待检查的玩家
     * @return 正举着透镜时返回 {@code true}
     */
    public static boolean isLookingThroughLens(PlayerEntity player) {
        return player.isUsingItem() && player.getActiveItem().isOf(ModItems.CURATOR_LENS);
    }

    /**
     * 判断一个方块是不是要找的箱子。
     *
     * <p>只认箱子与陷阱箱两种。木桶、潜影盒、末影箱等都<b>不算</b>——这是制作者定下的口径：
     * 要的是那种「一看就是藏东西的地方」的老式箱子。大箱子由两个方块拼成，两块都认，
     * 因此会浮起两枚并排的印记。</p>
     *
     * @param state 待判断的方块状态
     * @return 是箱子或陷阱箱时返回 {@code true}
     */
    private static boolean isChest(BlockState state) {
        return state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST);
    }
}
