package org.eternalrelic.capability.carried;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import org.eternalrelic.registry.ModItems;

/**
 * 「踩冰不滑」能力：带着雪地靴钉的人，踩在冰面上与踩在石头地上一样稳。
 *
 * <p><b>原版的「滑」是方块自己的一个数</b>：每个方块都声明自己的摩擦系数，
 * 石头、泥土这些是 0.6，冰与浮冰是 0.98 —— 数越大越滑。玩家挪动时游戏会去读脚下那一块的这个数，
 * 于是站上冰面就开始「刹不住」。</p>
 *
 * <p><b>本能力只做一件事：把那个数压回普通地面的大小。</b>本来就不滑的方块原样不动
 * （见 {@link #firmer}），因此它不会让玩家在本该稳当的地方变得奇怪；
 * 而任何模组自己加的「滑方块」也一样会被它压住——判断依据是「滑不滑」，不是一份方块名单。</p>
 *
 * <p>实际动手的位置在 {@code mixin/SnowGripStudsMixin}：那里是游戏真正读取这个数的唯一一处。</p>
 */
public final class SnowGripStudsEffect {

    /**
     * 原版普通地面的摩擦系数。
     *
     * <p>石头、泥土、木板、沙子全都是这个数（{@code Block} 的默认值），
     * 因此把它当作「不滑」的基准：比它滑的压到它，比它稳的保持原样。</p>
     */
    private static final float NORMAL_SLIPPERINESS = 0.6F;

    private SnowGripStudsEffect() {
    }

    /**
     * 这个实体此刻是否穿着雪地靴钉。
     *
     * <p>放在背包里、或者钉在某件装备上都算（与其它携带型遗物同一条口径）。</p>
     *
     * @param entity 待查的实体
     * @return 是否算数
     */
    public static boolean grips(Entity entity) {
        return entity instanceof PlayerEntity player
                && CarriedStacks.inEffect(player, ModItems.SNOW_GRIP_STUDS);
    }

    /**
     * 把脚下的摩擦系数调稳。
     *
     * <p>取「原来的数」与「普通地面」里较小的那一个：冰（0.98）会被压成 0.6，
     * 而本来就稳的方块仍旧是它自己那个数——<b>绝不往上抬</b>，因此本能力不会造出比石头更涩的地面。</p>
     *
     * @param original 脚下方块原本的摩擦系数
     * @return 调整之后交给游戏使用的摩擦系数
     */
    public static float firmer(float original) {
        return Math.min(original, NORMAL_SLIPPERINESS);
    }
}
