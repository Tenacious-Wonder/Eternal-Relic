package org.eternalrelic.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;

import org.eternalrelic.capability.carried.KineticHiltEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * <b>动能器柄要的那一处改动：把这一击的伤害换成强化后的数字。</b>
 *
 * <p>注入点是 {@code LivingEntity#damage(DamageSource, float)} 的入口 —— 也就是
 * <b>所有伤害都要经过的那道门</b>。选这里的理由：</p>
 *
 * <ul>
 *   <li><b>改的是参数本身</b>（{@code @ModifyVariable(argsOnly = true)}），因此这一击后续的
 *       每一步 —— 无敌帧判定、护甲结算、属性面板上的数字 —— 都按强化后的值走，
 *       与"这一击本来就该这么疼"没有区别；</li>
 *   <li><b>不必去碰 {@code PlayerEntity#attack}</b>：那个方法里伤害是现算的，注入点又深又杂，
 *       而这里只需要认"动手的是谁、挨打的是谁、原本多少"。</li>
 * </ul>
 *
 * <p><b>开销</b>：这个方法每一次伤害都会走，因此 {@link KineticHiltEffect#amplify} 的
 * 第一件事就是确认"动手的是不是带了护腕的玩家" —— 不是就立刻原样返回，
 * 不查表、不做任何计算。</p>
 */
@Mixin(LivingEntity.class)
public abstract class KineticHiltMixin {

    /**
     * 把这一击的伤害交给连击结算。
     *
     * @param amount 这一击原本的伤害
     * @param source 伤害来源（从中认出动手的玩家）
     * @return 实际使用的伤害
     */
    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
    private float eternal_relic$comboBracerDamage(float amount, DamageSource source) {
        return KineticHiltEffect.amplify(source, (LivingEntity) (Object) this, amount);
    }
}
