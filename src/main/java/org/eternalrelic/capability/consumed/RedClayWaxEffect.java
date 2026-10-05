package org.eternalrelic.capability.consumed;

import net.minecraft.item.ItemStack;

import org.eternalrelic.relic.AttachTarget;

/**
 * 「揉蜡修补」能力：把一块红土蜡块用在副手那件东西上，补回它一段耐久。
 *
 * <p><b>它是本模组第二件「用掉才生效」的遗物</b>，与「一个圆形的饼」同一路：效果发生在物品
 * 消失的那一刻，此后什么都不剩，因此没有「携带生效」可言。触发点由
 * {@link org.eternalrelic.item.RedClayWaxItem} 接住玩家的右键。</p>
 *
 * <p><b>能补哪些东西，直接问附着分类表</b>（{@link AttachTarget#coversAny}）：那张表本来
 * 就是给「哪件东西算武器 / 工具 / 装备」下的定义，纹章、弓油、剑带全都照它判定。
 * 这里复用同一份口径，好处是将来别的模组加了新武器，这张蜡块<b>不必改一行代码就自动认它</b>；
 * 反过来若在这里另写一份物品名单，迟早会与那张表对不上。盾牌也算在内——它属装备那一类，
 * 而且确实有耐久可修。</p>
 */
public final class RedClayWaxEffect {

    /**
     * 一次修补补回耐久上限的多少。{@code 0.145} 即 14.5%。
     *
     * <p>这个数不是凑整的：从空补满约需七次（100 ÷ 14.5 ≈ 6.9），因此一块蜡块的价值大致是
     * 「七分之一件装备」。数值由制作者指定，改它只需改这一处。</p>
     */
    private static final double RESTORE_RATIO = 0.145D;

    private RedClayWaxEffect() {
    }

    /**
     * 判断一件东西能不能用蜡块补。
     *
     * <p>三个条件缺一不可：这件东西<b>本身有耐久</b>（石头、木棍这类没有可修的东西）、
     * <b>已经掉了耐久</b>（完好的东西补了也是白补）、并且<b>算武器 / 工具 / 装备</b>。</p>
     *
     * @param stack 待检查的物品堆
     * @return 可以补时返回 {@code true}
     */
    public static boolean canRestore(ItemStack stack) {
        return !stack.isEmpty()
                && stack.isDamageable()
                && stack.getDamage() > 0
                && AttachTarget.coversAny(stack.getItem());
    }

    /**
     * 补一次。
     *
     * <p>补的量按<b>上限</b>算而不是按已掉的量算：铁剑上限 250，一次就补 36 点，
     * 与它当前掉了多少无关。补不满时如实收下；本来就没掉那么多时，最多补到完好为止——
     * 因此「补回的点数」需要实际算一遍，不能直接报那个名义值。</p>
     *
     * @param stack 要补的物品堆
     * @return 实际补回的点数；没补成时返回 {@code 0}
     */
    public static int restore(ItemStack stack) {
        if (!canRestore(stack)) {
            return 0;
        }

        // 至少补 1 点：钓鱼竿、打火石这类上限很低的工具，14.5% 向下取整会变成 0，
        // 那样蜡块用掉了却一点变化都没有，玩家只会觉得这是个坏掉的东西
        int nominal = Math.max(1, (int) Math.round(stack.getMaxDamage() * RESTORE_RATIO));

        int damage = stack.getDamage();
        int restored = Math.min(damage, nominal);

        stack.setDamage(damage - restored);
        return restored;
    }
}
