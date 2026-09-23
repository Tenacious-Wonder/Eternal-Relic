package org.eternalrelic.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;

import org.eternalrelic.capability.carried.BeeswaxPendantEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让蜂蜡吊坠能拦下蜜蜂蜇人带来的中毒。
 *
 * <p><b>为什么挂在这里</b>：蜜蜂蜇人分两步走——先结算伤害，再把中毒单独挂到目标身上。
 * 第二步是本模组唯一能插手的时机，而游戏没有提供「状态效果即将生效」的事件，
 * 只能挂到 {@code LivingEntity#addStatusEffect} 上。</p>
 *
 * <p><b>为什么判断得准</b>：蜜蜂挂毒时会把<b>自己</b>作为来源实体传进来，因此这里既认得出
 * 「是谁下的」，也认得出「下的是什么效果」；其余任何来源（药水、毒箭、洞穴蜘蛛、毒土豆）
 * 传进来的来源不是蜜蜂，一律放行。</p>
 *
 * <p><b>拦下时返回「没加上」</b>：原方法的返回值表示这条效果到底有没有生效，这里既然把它挡掉了，
 * 就应当如实回答 {@code false}，而不是拦下之后仍报成功。</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    /**
     * 状态效果即将生效时，若这是蜜蜂蜇向带着蜂蜡吊坠的玩家下的一口毒，就把这条效果拦下。
     *
     * @param effect       即将生效的状态效果
     * @param source       施加这条效果的来源实体，可能为 {@code null}
     * @param callbackInfo 原方法的返回值回调；改成 {@code false} 即表示这条效果没有加上
     */
    @Inject(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void eternal_relic$blockBeeStingPoison(StatusEffectInstance effect, Entity source,
                                                   CallbackInfoReturnable<Boolean> callbackInfo) {
        if (BeeswaxPendantEffect.blocksBeeStingPoison((LivingEntity) (Object) this, effect, source)) {
            callbackInfo.setReturnValue(false);
        }
    }
}
