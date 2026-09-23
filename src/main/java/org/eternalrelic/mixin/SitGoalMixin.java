package org.eternalrelic.mixin;

import net.minecraft.entity.ai.goal.SitGoal;
import net.minecraft.entity.passive.TameableEntity;

import org.eternalrelic.capability.carried.WolfAweEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让被狼王獠牙镇住的野狼也能坐下。
 *
 * <p><b>为什么必须动这里</b>：原版负责让狼坐下、并让它原地不动的那段逻辑（{@code SitGoal}），
 * 开工前先问一句「这只动物被驯服了吗」，没被驯服就直接放弃。于是未驯服的狼根本坐不下来：
 * 光把它的坐下标志设成 true，既不会有坐姿，也不会停下——狼照样在附近乱走。</p>
 *
 * <p><b>放行的条件必须收得很紧，不能只判断「是不是未驯服的狼」</b>：原版那段逻辑在后面还有一句
 * 「没有主人就直接放行」，而未驯服的狼恰好没有主人——一旦对所有野狼放行，<b>每一只野狼都会
 * 当场被按住坐下、并且再也走不动</b>，与玩家带没带吊坠毫无关系。因此这里要求
 * 「它自己已经处于坐姿」，也就是要求这次坐下确实是本能力请它坐的
 * （详见 {@link WolfAweEffect#maySitWhileUntamed}）。</p>
 *
 * <p><b>其余动物一概不碰</b>：这一处挂在所有会坐下的动物（猫、鹦鹉、马……）共用的逻辑上，
 * 但只有狼才可能被本能力放行；其余动物问的还是同一个问题、拿到的还是同一个答案，
 * 表现与不装这个模组时完全一致。</p>
 *
 * <p>放行之后，剩下的判断（是否在水里、是否站在地上、是否有主人）原样由原版继续问下去，
 * 因此水里与半空中的狼不会被硬按住。</p>
 */
@Mixin(SitGoal.class)
public abstract class SitGoalMixin {

    /**
     * 把「这只动物被驯服了吗」换成「被驯服了，或者是一只可以被镇住的野狼」。
     *
     * @param tameable 原版正在询问的那只动物
     * @return 是否放行坐下
     */
    @Redirect(method = "canStart",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/passive/TameableEntity;isTamed()Z"))
    private boolean eternal_relic$allowCowedWolfToSit(TameableEntity tameable) {
        return tameable.isTamed() || WolfAweEffect.maySitWhileUntamed(tameable);
    }
}
