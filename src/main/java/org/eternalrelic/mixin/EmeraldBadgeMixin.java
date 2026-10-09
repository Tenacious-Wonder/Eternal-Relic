package org.eternalrelic.mixin;

import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;

import org.eternalrelic.capability.carried.EmeraldBadgeEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>绿宝石徽章要的那一处改动：玩家坐到交易界面前后，把报价调成他该看到的价格。</b>
 *
 * <p>注入点是 {@code MerchantEntity#setCustomer(PlayerEntity)} —— 游戏在这里记下
 * "谁正在跟这个村民交易"。点开交易界面时传玩家、关掉时传 {@code null}，
 * 因此这一个位置正好能同时管"打折"与"恢复原价"两件事，
 * 不必再去分辨界面什么时候关上。</p>
 *
 * <p>真正的价格计算交给 {@link EmeraldBadgeEffect}，这里只负责在正确的时刻叫它。</p>
 */
@Mixin(MerchantEntity.class)
public abstract class EmeraldBadgeMixin {

    /**
     * 换顾客时，把这位交易者面前的一整批报价重算一遍。
     *
     * @param customer     新的顾客；关掉界面时是 {@code null}
     * @param callbackInfo 原方法的回调（本注入不改变它的走向）
     */
    @Inject(method = "setCustomer", at = @At("HEAD"))
    private void eternal_relic$merchantBadge(PlayerEntity customer, CallbackInfo callbackInfo) {
        EmeraldBadgeEffect.apply(((MerchantEntity) (Object) this).getOffers(), customer);
    }
}
