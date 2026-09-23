package org.eternalrelic.mixin;

import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.random.Random;

import org.eternalrelic.capability.carried.WolfTamingEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让可怕狼牙吊坠把「喂骨头驯服狼」的成功机会抬高一截。
 *
 * <p><b>为什么只改骰子、不抄判定</b>：原版驯服的写法是「掷一次三面的骰子，掷出 0 就算认主」。
 * 这里替换掉掷骰子那一步，直接给出一个让原版判定通过的数字，于是概率随我们调，
 * 而「认主之后做什么」（认主、坐下、冒爱心、扣掉一根骨头）全部仍旧由原版执行，一行也没有复制。
 * 将来原版改了驯服之后的表现，这里不必跟着改。</p>
 *
 * <p><b>为什么需要记住「谁在喂」</b>：原版掷骰子那一步只用到狼自己，<b>看不到玩家</b>，
 * 而「要不要抬高」取决于喂骨头的人身上有没有吊坠。因此在方法开头把这位玩家记下来、
 * 结束时清掉；不这么做就只能去改判定表达式本身，那比改骰子脆弱得多。</p>
 */
@Mixin(WolfEntity.class)
public abstract class WolfTamingMixin {

    /** 原版驯服骰子的面数：三面里掷出一面就算成功，也就是三分之一。 */
    @Unique
    private static final int VANILLA_TAME_BOUND = 3;

    /** 原版判定里「掷出这个数就算驯服成功」。 */
    @Unique
    private static final int TAME_SUCCESS_VALUE = 0;

    /** 除成功之外的任何一个数都算失败，这里固定给 1。 */
    @Unique
    private static final int TAME_FAILURE_VALUE = 1;

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
     * 替原版掷那次驯服判定的骰子。
     *
     * <p>喂骨头的人身上没有吊坠时，照原版掷一次三面的骰子；有吊坠时改为<b>六面里只有一面失败</b>，
     * 再把结果翻译回原版要的数字（0 表示成功，1 表示失败），好让原版那句「等于 0 就算驯服」照常成立。</p>
     *
     * <p><b>只认原版那一次掷骰</b>：判据是「面数为 3」——那正是原版驯服判定用的骰子。
     * 这个方法里目前只有这一处掷骰，所以这个条件已经够用；将来原版（或别的模组）若在这里
     * 添了别的掷骰，也会被原样放过去，不受影响。</p>
     *
     * @param random 这只狼自己的随机源
     * @param bound  原版这次要掷几面
     * @return 交给原版判定的数字：0 会让它认为驯服成功，其余则失败
     */
    @Redirect(method = "interactMob",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/random/Random;nextInt(I)I"))
    private int eternal_relic$easeTaming(Random random, int bound) {
        if (bound != VANILLA_TAME_BOUND || !WolfTamingEffect.easesTaming(this.eternal_relic$interactingPlayer)) {
            return random.nextInt(bound);
        }

        return WolfTamingEffect.tames(random) ? TAME_SUCCESS_VALUE : TAME_FAILURE_VALUE;
    }
}
