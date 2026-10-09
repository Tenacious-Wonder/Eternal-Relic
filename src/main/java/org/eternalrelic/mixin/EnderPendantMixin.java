package org.eternalrelic.mixin;

import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.player.PlayerEntity;

import org.eternalrelic.capability.carried.EnderPendantEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <b>末影吊坠要的那一处改动：让末影人不再因为被盯着而发怒。</b>
 *
 * <p>注入点是 {@code EndermanEntity#isPlayerStaring(PlayerEntity)} ——
 * 游戏问"这位玩家正在盯着我吗"，答案决定它要不要把这名玩家记成仇人。
 * 带着挂坠的玩家一律回答"没有"，于是它连看都不看你一眼。</p>
 *
 * <p><b>为什么不拦"设谁为目标"</b>：那会把"你打了它、它反击"也一并拦掉。
 * 挂坠只该管"无端被盯上"这一种敌意，动手打它照样要挨揍 —— 那是活该。</p>
 */
@Mixin(EndermanEntity.class)
public abstract class EnderPendantMixin {

    /**
     * 带着挂坠的玩家不算"在盯着它"。
     *
     * @param player       可能正在盯着它看的那位玩家
     * @param callbackInfo 原方法的返回值回调；命中时直接改成 {@code false}
     */
    @Inject(method = "isPlayerStaring", at = @At("HEAD"), cancellable = true)
    private void eternal_relic$enderPendant(PlayerEntity player,
            CallbackInfoReturnable<Boolean> callbackInfo) {
        if (EnderPendantEffect.soothes(player)) {
            callbackInfo.setReturnValue(false);
        }
    }
}
