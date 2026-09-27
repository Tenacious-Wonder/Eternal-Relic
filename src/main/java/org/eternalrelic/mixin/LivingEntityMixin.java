package org.eternalrelic.mixin;

import java.util.function.Consumer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.server.network.ServerPlayerEntity;

import org.eternalrelic.capability.carried.BeeswaxPendantEffect;
import org.eternalrelic.capability.carried.HunterBadgeEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 生物身上的两处加料：状态效果即将生效、以及死亡时的掉落。
 *
 * <p><b>蜂蜡吊坠为什么挂在这里</b>：蜜蜂蜇人分两步走——先结算伤害，再把中毒单独挂到目标身上。
 * 第二步是本模组唯一能插手的时机，而游戏没有提供「状态效果即将生效」的事件，
 * 只能挂到 {@code LivingEntity#addStatusEffect} 上。蜜蜂挂毒时会把<b>自己</b>作为来源实体
 * 传进来，因此这里既认得出「是谁下的」，也认得出「下的是什么效果」；其余任何来源
 * （药水、毒箭、洞穴蜘蛛、毒土豆）传进来的来源不是蜜蜂，一律放行。</p>
 *
 * <p><b>猎人徽章为什么挂在这里</b>：生物掉什么是游戏照它自己的掉落表算出来的，模组没有
 * 可以监听的事件，因此守在「跑掉落表」那一次调用上。用的是
 * {@code @WrapOperation}（包一层）而不是把它顶掉——那一步别的模组也可能动，
 * 包一层可以多家并存，顶掉则会在启动时报错。</p>
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

    /**
     * 跑掉落表那一步：击杀者是带着猎人徽章的玩家且掷骰命中时，额外多掉一件战利品。
     *
     * <p>命中的处理是「先照原样让游戏掉完，再从这一次掉出来的东西里挑一样多给一个」——
     * 挑的过程交给 {@link HunterBadgeEffect}，本方法只负责把掉落清单接住。</p>
     *
     * @param lootTable  这张生物自己的掉落表
     * @param parameters 游戏已经算好的掉落上下文（幸运、击杀条件都在里面）
     * @param seed       掉落种子，沿用游戏原本给的那一个
     * @param consumer   游戏原本用来把掉落物放到地上的那一个动作
     * @param original   那次「跑掉落表」的调用本身，由本方法负责执行
     * @param source     这只生物致死的那次伤害（从目标方法的参数里取）
     */
    @WrapOperation(
            method = "dropLoot(Lnet/minecraft/entity/damage/DamageSource;Z)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/loot/LootTable;generateLoot(Lnet/minecraft/loot/context/LootContextParameterSet;JLjava/util/function/Consumer;)V"))
    private void eternal_relic$hunterBadgeExtraItem(LootTable lootTable, LootContextParameterSet parameters, long seed,
                                                    Consumer<ItemStack> consumer, Operation<Void> original,
                                                    @Local(argsOnly = true) DamageSource source) {
        ServerPlayerEntity hunter = HunterBadgeEffect.hunterOf(source);

        // 没带徽章、不是玩家击杀、或者这次没命中：一步都不多走，原样放行。
        if (hunter == null || !HunterBadgeEffect.rollsExtra(hunter)) {
            original.call(lootTable, parameters, seed, consumer);
            return;
        }

        HunterBadgeEffect.Collector collector = new HunterBadgeEffect.Collector(consumer);
        original.call(lootTable, parameters, seed, collector);
        HunterBadgeEffect.dropExtraItem(hunter, collector.collected(), consumer);
    }
}
