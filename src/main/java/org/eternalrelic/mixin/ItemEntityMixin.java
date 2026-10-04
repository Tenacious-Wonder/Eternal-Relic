package org.eternalrelic.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

import org.eternalrelic.relic.ItemPreservation;
import org.eternalrelic.relic.PocketStorage;
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
 *
 * <p><b>另有一处与永恒纹章无关的注入</b>：拾取那一刻，让「装了拾荒符石的口袋」优先收货
 * （见 {@link #eternal_relic$pocketFirst}）。它包住的是原版往玩家背包里塞东西的那一次调用，
 * 用的是「包一层」，因此别的模组也想在这一步插一手时不会互相顶掉。</p>
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

    /**
     * 拾取的那一刻：玩家带着「装了拾荒符石的口袋」时，东西优先进那口口袋，不占他自己的背包。
     *
     * <p><b>为什么拦在这里</b>：游戏与 Fabric 都<b>没有「玩家捡起了什么」这类事件</b>
     * （Fabric 的物品 API 与实体事件 API 里都没有，已逐个查过），而原版把东西收进背包只有
     * 这一次调用——{@code ItemEntity#onPlayerCollision} 里那句「往玩家背包里塞」。
     * 包住它，就同时在「走过去捡」这条路上插进了手。</p>
     *
     * <p><b>只包一层、不顶掉</b>：这一步别的模组（更大的背包、自动拾取之类）多半也想动，
     * 包一层两家都能生效；顶掉则会在启动时报错。传下去的 {@code stack} 还是原版那一个，
     * 因此容量、合并、NBT 比对这些规矩一步都没改。</p>
     *
     * <p><b>口袋只装下一部分时怎么办</b>：剩下的仍旧交给原版塞进背包。返回值表示
     * 「这一次到底收下东西没有」——口袋收了一半而背包塞不下时也要返回真，
     * 否则剩余那份会被当成「没被收下」而白白留在原地（甚至被当成丢弃）。</p>
     *
     * @param inventory 玩家背包（原版那次调用的接收者）
     * @param stack     这一份掉落物；会被就地扣减
     * @param original  原版那次「塞进背包」的调用本身，由本方法决定何时执行
     * @param player    捡东西的玩家（从目标方法的参数里取）
     * @return 这一次是否收下了东西
     */
    @WrapOperation(
            method = "onPlayerCollision",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerInventory;insertStack(Lnet/minecraft/item/ItemStack;)Z"))
    private boolean eternal_relic$pocketFirst(PlayerInventory inventory, ItemStack stack, Operation<Boolean> original,
                                              @Local(argsOnly = true) PlayerEntity player) {
        int intoPocket = PocketStorage.insertIntoMagnetPocket(player, stack);

        // 整份都进了口袋：原版该做的那几件事（拾取动画、统计、清掉物品实体）仍旧由它走完
        if (stack.isEmpty()) {
            return true;
        }

        return original.call(inventory, stack) || intoPocket > 0;
    }
}
