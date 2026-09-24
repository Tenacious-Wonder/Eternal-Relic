package org.eternalrelic.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

import org.eternalrelic.capability.carried.WolfTamingEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让可怕狼牙吊坠把「喂骨头驯服狼」的成功机会抬高一截。
 *
 * <p><b>为什么只改骰子、不抄判定</b>：原版驯服的写法是「掷一次三面的骰子，掷出 0 就算认主」。
 * 这里接手的是<b>掷出来的那个数</b>，于是概率随我们调，而「认主之后做什么」
 * （认主、坐下、冒爱心、扣掉一根骨头）全部仍旧由原版执行，一行也没有复制。
 * 将来原版改了驯服之后的表现，这里不必跟着改。</p>
 *
 * <p><b>为什么需要记住「谁在喂」</b>：原版掷骰子那一步只用到狼自己，<b>看不到玩家</b>，
 * 而「要不要抬高」取决于喂骨头的人身上有没有吊坠。因此在方法开头把这位玩家记下来、
 * 结束时清掉；不这么做就只能去改判定表达式本身，那比改骰子脆弱得多。</p>
 *
 * <h2>接法：改掷出来的数，而不是把掷骰动作换掉</h2>
 * <p>「替换那次调用」是占位式的 —— 两个模组都想改这里就会在启动时撞崩。
 * 这里改成在掷出的数上<b>做折算</b>：原版已经判成功的保持成功，原版判失败的那些里
 * 再按 {@link WolfTamingEffect} 的比例改判一部分，折算出的总成功率仍是六分之五。
 * 别的模组若也想改这里，两层都能生效（见设计决策 56）。</p>
 */
@Mixin(WolfEntity.class)
public abstract class WolfTamingMixin {

    /**
     * 正在和这只狼交互的玩家。
     *
     * <p>只在一次交互的过程里有效，交互结束就清空。同一刻不会有两个人喂同一只狼，
     * 而整套交互都跑在服务端主线程上，所以一个字段足够，不必做成一张表。</p>
     */
    @Unique
    private PlayerEntity eternal_relic$interactingPlayer;

    /**
     * 交互开始时，记下正在喂这只狼的玩家。
     *
     * @param player       正在交互的玩家
     * @param hand         用的哪只手
     * @param callbackInfo 原方法的返回值回调；这里不改变它
     */
    @Inject(method = "interactMob", at = @At("HEAD"))
    private void eternal_relic$rememberInteractingPlayer(PlayerEntity player, Hand hand,
                                                         CallbackInfoReturnable<ActionResult> callbackInfo) {
        this.eternal_relic$interactingPlayer = player;
    }

    /**
     * 交互结束时把这个记录清掉，免得留到下一次。
     *
     * <p>注入在 {@code RETURN} 上，方法里所有的返回点都会经过这里。</p>
     *
     * @param player       正在交互的玩家
     * @param hand         用的哪只手
     * @param callbackInfo 原方法的返回值回调；这里不改变它
     */
    @Inject(method = "interactMob", at = @At("RETURN"))
    private void eternal_relic$forgetInteractingPlayer(PlayerEntity player, Hand hand,
                                                       CallbackInfoReturnable<ActionResult> callbackInfo) {
        this.eternal_relic$interactingPlayer = null;
    }

    /**
     * 在驯服判定那颗骰子的结果上做折算。
     *
     * <p><b>只认原版那一次掷骰</b>：注入器默认就要求<b>恰好命中一处</b>，因此原版（或别的模组）
     * 日后若在这个方法里另加一处掷骰，启动时会直接报错，而不是悄悄把那一处也一起改掉。</p>
     *
     * @param original 原版掷出的那个数（0 表示它即将判成功）
     * @return 折算之后交给原版判定的数
     */
    @ModifyExpressionValue(
            method = "interactMob",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/random/Random;nextInt(I)I"))
    private int eternal_relic$easeTaming(int original) {
        WolfEntity wolf = (WolfEntity) (Object) this;
        return WolfTamingEffect.eased(this.eternal_relic$interactingPlayer, original, wolf.getRandom());
    }
}
