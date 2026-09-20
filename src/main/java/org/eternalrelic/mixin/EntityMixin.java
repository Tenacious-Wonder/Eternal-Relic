package org.eternalrelic.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;

import org.eternalrelic.relic.ItemPreservation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把掉进虚空的受保物品捞回来。
 *
 * <p><b>为什么挂在 {@code Entity} 上而不是 {@code ItemEntity} 上</b>：判断「是否掉出世界」的
 * {@code tickInVoid} 定义在 {@code Entity} 里，物品实体并没有覆写它，所以在物品实体那一层
 * 找不到可注入的方法。这里挂在上游，只有「受保的物品实体」才动手，其余实体一律放行。</p>
 *
 * <p><b>捞而不是留</b>：只把删除拦下来，物品会继续往世界底下无限下坠——玩家既看不见也拿不回，
 * 还会一直占着这份数据。所以这里改成送回主人身边（见 {@link ItemPreservation#rescueFromVoid}）。</p>
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    /**
     * 受保物品掉出世界时，把它捞回主人身边，并取消原本的删除。
     *
     * @param callbackInfo 原方法（无返回值）的回调；取消即表示不执行删除
     */
    @Inject(method = "attemptTickInVoid", at = @At("HEAD"), cancellable = true)
    private void eternal_relic$rescuePreservedItemFromVoid(CallbackInfo callbackInfo) {
        if ((Object) this instanceof ItemEntity item && ItemPreservation.rescueFromVoid(item)) {
            callbackInfo.cancel();
        }
    }
}
