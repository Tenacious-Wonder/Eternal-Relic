package org.eternalrelic.mixin;

import java.util.function.Consumer;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
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
import org.eternalrelic.capability.carried.SilentBootsEffect;
import org.eternalrelic.capability.carried.SkinningKnifeEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 生物身上的三处加料：状态效果即将生效、死亡时的掉落，以及「被怪物发现的距离」。
 *
 * <p><b>蜂蜡吊坠为什么挂在这里</b>：蜜蜂蜇人分两步走——先结算伤害，再把中毒单独挂到目标身上。
 * 第二步是本模组唯一能插手的时机，而游戏没有提供「状态效果即将生效」的事件，
 * 只能挂到 {@code LivingEntity#addStatusEffect} 上。蜜蜂挂毒时会把<b>自己</b>作为来源实体
 * 传进来，因此这里既认得出「是谁下的」，也认得出「下的是什么效果」；其余任何来源
 * （药水、毒箭、洞穴蜘蛛、毒土豆）传进来的来源不是蜜蜂，一律放行。</p>
 *
 * <p><b>猎人徽章与剥皮小刀为什么挂在这里</b>：生物掉什么是游戏照它自己的掉落表算出来的，模组没有
 * 可以监听的事件，因此守在「跑掉落表」那一次调用上。用的是
 * {@code @WrapOperation}（包一层）而不是把它顶掉——那一步别的模组也可能动，
 * 包一层可以多家并存，顶掉则会在启动时报错。</p>
 *
 * <p><b>无声软靴为什么挂在这里</b>：隐蔽效果在 1.20.1 里没有给模组任何口子，
 * 而 {@code getAttackDistanceScalingFactor} 是潜行 / 隐身 / 头颅伪装三种效果的唯一汇聚点，
 * 改这一处即可覆盖普通怪与走 Brain 的怪（详见那个方法上的注释）。</p>
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
     * 跑掉落表那一步：击杀者是带着猎人徽章 / 剥皮小刀的玩家且掷骰命中时，额外多掉一件战利品。
     *
     * <p><b>两件遗物共用这一处注入</b>：徽章不限对象、小刀只认动物，各掷各的骰子，
     * 因此可能同时命中、一次多掉两件。判断分别交给 {@link HunterBadgeEffect} 与
     * {@link SkinningKnifeEffect}，本方法只负责把掉落清单接住。</p>
     *
     * <p>命中的处理是「先照原样让游戏掉完，再从这一次掉出来的东西里挑一样多给一个」。</p>
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
    private void eternal_relic$extraLootFromRelics(LootTable lootTable, LootContextParameterSet parameters, long seed,
                                                   Consumer<ItemStack> consumer, Operation<Void> original,
                                                   @Local(argsOnly = true) DamageSource source) {
        // 这只生物自己：mixin 的目标就是它
        LivingEntity victim = (LivingEntity) (Object) this;

        ServerPlayerEntity hunter = HunterBadgeEffect.hunterOf(source);
        boolean badgeHit = hunter != null && HunterBadgeEffect.rollsExtra(hunter);

        ServerPlayerEntity skinner = SkinningKnifeEffect.skinnerOf(source, victim);
        boolean knifeHit = skinner != null && SkinningKnifeEffect.rollsExtra(skinner);

        // 两件都没命中（没带、不是玩家击杀、或者这次没掷中）：一步都不多走，原样放行。
        if (!badgeHit && !knifeHit) {
            original.call(lootTable, parameters, seed, consumer);
            return;
        }

        HunterBadgeEffect.Collector collector = new HunterBadgeEffect.Collector(consumer);
        original.call(lootTable, parameters, seed, collector);

        if (badgeHit) {
            HunterBadgeEffect.dropExtraItem(hunter, collector.collected(), consumer);
        }

        if (knifeHit) {
            SkinningKnifeEffect.dropExtraItem(skinner, collector.collected(), consumer);
        }
    }

    /**
     * 带着无声软靴潜行时，怪物「发现」这名玩家的距离再压一档。
     *
     * <p><b>为什么挂在这里</b>：游戏与 Fabric 都<b>没有「谁发现了谁」这类事件</b>
     * （已把 Fabric API 的全部源码包搜过一遍，零命中）。而
     * {@code LivingEntity#getAttackDistanceScalingFactor} 是潜行、隐身与「戴着对应生物的头颅」
     * 三种隐蔽效果的<b>唯一汇聚点</b>——它返回的那个倍率会被索敌的距离比较乘上去，
     * 于是普通怪（{@code ActiveTargetGoal} 那一套）与走 Brain 的怪（猪灵、监守者）一并覆盖。</p>
     *
     * <p><b>为什么不改 {@code MobEntity} 或 {@code getFollowRange}</b>：前者挂上去会漏掉所有走 Brain 的
     * 怪物，后者那里拿不到「被索敌的是谁」，一改就会把所有玩家、所有怪一起改掉。
     * 这一处是源码里唯一合适的口子，而且全项目只有这一个方法用到它。</p>
     *
     * <p>用「包一层」而不是顶掉：别的模组若也想在这一处加隐蔽效果，两层会叠加，不会互相作废。</p>
     *
     * @param original 游戏原本算出的倍率
     * @return 带着软靴潜行时压过一档的倍率，否则原样返回
     */
    @ModifyReturnValue(method = "getAttackDistanceScalingFactor", at = @At("RETURN"))
    private double eternal_relic$silentBootsMoreHidden(double original) {
        if (!((Object) this instanceof ServerPlayerEntity player)) {
            return original;
        }

        return SilentBootsEffect.hidesFromSenses(player) ? SilentBootsEffect.moreHidden(original) : original;
    }
}
