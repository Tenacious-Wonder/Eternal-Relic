package org.eternalrelic.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.random.Random;

import org.eternalrelic.capability.carried.CastIronThumbRingEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让铸铁拇指戒有机会保住工具与武器的一次耐久损耗。
 *
 * <p><b>为什么挂在 {@code ItemStack#damage} 上</b>：游戏里所有耐久损耗——挖方块、砍怪、
 * 拉弓射箭、剪羊毛、用打火石——最后都会汇到这一个方法上，它才是真正把耐久加上去的那一步。
 * 挂在它上面，等于一次覆盖全部用法，不必逐个数「有哪几种用法会掉耐久」。</p>
 *
 * <p><b>拦下时返回「没坏」</b>：原方法的返回值表示「这件东西有没有坏」，这里既然一点耐久
 * 都没扣，就应当如实回答 {@code false}，调用方不会因此少做任何它该做的事。</p>
 *
 * <p>判定本身在 {@link CastIronThumbRingEffect} 里；本类只负责在正确的时机问它一句。</p>
 */
@Mixin(ItemStack.class)
public abstract class ItemStackDurabilityMixin {

    /**
     * 耐久即将落下的那一刻问一句：这一次保不保。
     *
     * @param amount       这次要扣的耐久点数
     * @param random       调用方用的随机源（本模组用的是持有者身上那个，因此这里不用它）
     * @param player       持有者；发射器、漏斗这类非玩家造成的损耗在这里是 {@code null}，
     *                     客户端自己预测的那一份同样是 {@code null}（它不是服务端玩家）
     * @param callbackInfo 原方法的返回值回调，触发时把结果改成「没坏」
     */
    @Inject(method = "damage(ILnet/minecraft/util/math/random/Random;Lnet/minecraft/server/network/ServerPlayerEntity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void eternal_relic$castIronThumbRing(int amount, Random random, ServerPlayerEntity player,
                                                 CallbackInfoReturnable<Boolean> callbackInfo) {
        if (amount <= 0 || player == null) {
            return;
        }

        if (CastIronThumbRingEffect.savesDurability((ItemStack) (Object) this, player)) {
            callbackInfo.setReturnValue(false);
        }
    }
}
