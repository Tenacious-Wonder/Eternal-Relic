package org.eternalrelic.mixin;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;

import org.eternalrelic.relic.ItemPreservation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让钉了永恒纹章的掉落物烧不掉、炸不掉、扎不掉，也不会自行消失。
 *
 * <p>三处注入对应三条真实的销毁路径（都查自 1.20.1 的物品实体源码）：</p>
 *
 * <ul>
 *   <li>{@code isFireImmune} —— 游戏点火之前先问「怕不怕火」，回答「不怕」就既不会被火烧，
 *       也不会被岩浆点着（岩浆那一步问的是同一个问题）；</li>
 *   <li>{@code damage} —— 爆炸与仙人掌刺都走这里扣物品的那 5 点血，扣完才消失；</li>
 *   <li>{@code tick} —— 物品在地上满 6000 刻（5 分钟）会被删除。</li>
 * </ul>
 *
 * <p><b>「永不消失」为什么放在 tick 末尾也来得及</b>：{@code setNeverDespawn()} 把年龄置为一个
 * 特殊值，此后每刻只加不减的逻辑会跳过它，因此年龄<b>永远到不了 6000</b>。物品实体出现的第一个
 * 服务端时刻就会走到这里，不存在「先长到 5999 再钉纹章」的情形——在地上的物品根本进不了装卸台。</p>
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    /**
     * 受保物品回答「我不怕火」，从而跳过点火与岩浆销毁。
     *
     * @param callbackInfo 原方法的返回值回调，改写为 {@code true} 即表示不怕火
     */
    @Inject(method = "isFireImmune", at = @At("HEAD"), cancellable = true)
    private void eternal_relic$preservedItemsAreFireImmune(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (ItemPreservation.protects(((ItemEntity) (Object) this).getStack())) {
            callbackInfo.setReturnValue(true);
        }
    }

    /**
     * 受保物品不受任何伤害（因而爆炸与仙人掌都伤不到它）。
     *
     * @param source       伤害来源
     * @param amount       伤害数值
     * @param callbackInfo 原方法的返回值回调；返回 {@code false} 表示「没受伤，也就没被毁掉」
     */
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void eternal_relic$preservedItemsTakeNoDamage(DamageSource source, float amount,
                                                          CallbackInfoReturnable<Boolean> callbackInfo) {
        if (ItemPreservation.protects(((ItemEntity) (Object) this).getStack())) {
            callbackInfo.setReturnValue(false);
        }
    }

    /**
     * 受保物品的年龄不再增长，因此永远不会「到点消失」。
     *
     * @param callbackInfo 原方法（无返回值）的回调
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void eternal_relic$preservedItemsNeverDespawn(CallbackInfo callbackInfo) {
        ItemEntity self = (ItemEntity) (Object) this;

        if (ItemPreservation.protects(self.getStack())) {
            self.setNeverDespawn();
        }
    }
}
