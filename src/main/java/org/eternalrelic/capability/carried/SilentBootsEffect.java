package org.eternalrelic.capability.carried;

import net.minecraft.entity.player.PlayerEntity;

import org.eternalrelic.registry.ModItems;

/**
 * 「潜行不再惊动」能力：带着无声软靴时，潜行状态下的动静传不出去。
 *
 * <p><b>它补的是原版没有免掉的那几样。</b>1.20.1 的原版潜行其实已经很安静：
 * {@code GameEventTags.IGNORE_VIBRATIONS_SNEAKING} 让脚步、游泳、落地与「开始 / 结束使用物品」
 * 这四类振动在潜行时根本不发（判据在 {@code Vibrations.Callback#canAccept} 里），
 * 而 {@code LivingEntity#getAttackDistanceScalingFactor} 已经把潜行者的被发现距离压到 <b>0.8 倍</b>。
 * 所以本能力真正加上去的是两件事：<b>潜行时挖掘、放置方块、开箱、受伤、进食等也不再产生振动</b>，
 * 以及<b>被怪物发现的距离从 0.8 倍再压到 0.5 倍</b>。</p>
 *
 * <p>两条效果各由一处游戏内部注入实现（{@code mixin/ServerWorldMixin} 管振动、
 * {@code mixin/LivingEntityMixin} 管索敌距离），本类只提供「这名玩家此刻算不算隐蔽」这一个判断，
 * 免得两处各写一遍、日后口径走散。</p>
 *
 * <p><b>不潜行就等于没戴</b>：这是刻意的——软靴护的是「蹑手蹑脚」这件事，
 * 玩家自己就能看懂「站着走会被听见、蹲下走不会」。</p>
 */
public final class SilentBootsEffect {

    /** 潜行时最终的被发现距离倍率；原版潜行给的已经是 0.8 倍。 */
    private static final double SNEAK_DETECTION_FACTOR = 0.5D;

    /** 原版潜行自带的倍率，用来把「还要再乘多少」折算出来。 */
    private static final double VANILLA_SNEAK_FACTOR = 0.8D;

    private SilentBootsEffect() {
    }

    /**
     * 这名玩家此刻是否「带着软靴在潜行」——也就是本能力保护的隐蔽状态。
     *
     * @param player 目标玩家
     * @return 是否处于隐蔽状态
     */
    public static boolean hidesFromSenses(PlayerEntity player) {
        return player.isSneaking() && CarriedStacks.inEffect(player, ModItems.SILENT_BOOTS);
    }

    /**
     * 把游戏原本算好的「被发现距离倍率」再压一档。
     *
     * <p><b>为什么是乘一档、而不是直接给 0.5</b>：这个倍率是潜行、隐身与「戴着对应生物的头颅」
     * 三种效果的<b>共同结果</b>（见 {@code LivingEntity#getAttackDistanceScalingFactor}）。
     * 直接覆盖成 0.5 会把隐身药水与头颅伪装带来的好处一并抹掉，乘一档则是在玩家已有的隐蔽程度上
     * 再叠一层，三种手段互不冲突。</p>
     *
     * @param original 游戏原本算出的倍率
     * @return 压过一档之后的倍率
     */
    public static double moreHidden(double original) {
        return original * (SNEAK_DETECTION_FACTOR / VANILLA_SNEAK_FACTOR);
    }
}
