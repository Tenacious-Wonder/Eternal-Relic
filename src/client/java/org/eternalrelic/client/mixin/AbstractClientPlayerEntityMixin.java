package org.eternalrelic.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.eternalrelic.capability.using.CuratorLensEffect;

/**
 * 把馆藏透镜的放大倍率从原版望远镜的十倍改成两倍。
 *
 * <p><b>为什么必须动游戏内部代码</b>：放大多少这件事不在物品手里。游戏算视野时会走到
 * {@code AbstractClientPlayerEntity#getFovMultiplier}，那里对「正在用望远镜」的情况直接
 * {@code return 0.1F}——一个写死的数字，物品无从插手。因此在方法出口处把那个结果换掉。</p>
 *
 * <p><b>为什么换成 0.5 就是两倍</b>：这个返回值是<b>视野的缩放系数</b>，不是放大倍数。
 * 系数 0.1 表示把视野压到十分之一，看到的画面就是十倍大；要两倍，系数取二分之一即可。</p>
 *
 * <p><b>为什么还要再判一次第一人称</b>：原版那条分支本身就带这个前提——第三人称举镜不放大，
 * 否则玩家会看不见自己。这里从出口处替换返回值，会把第三人称的结果也一并改掉，
 * 所以必须把同一个前提补上。</p>
 *
 * <p>原版望远镜不受影响：那条判据只对馆藏透镜成立，其余情况原样放行。</p>
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    /** 举着馆藏透镜时的视野缩放系数：0.5，即画面放大两倍。 */
    private static final float CURATOR_LENS_FOV_MULTIPLIER = 0.5F;

    /**
     * 举着馆藏透镜观望时，把视野缩放系数改成两倍。
     *
     * @param callbackInfo 原方法的返回值回调
     */
    @Inject(method = "getFovMultiplier", at = @At("RETURN"), cancellable = true)
    private void eternal_relic$zoomForCuratorLens(CallbackInfoReturnable<Float> callbackInfo) {
        if (!MinecraftClient.getInstance().options.getPerspective().isFirstPerson()) {
            return;
        }

        AbstractClientPlayerEntity player = (AbstractClientPlayerEntity) (Object) this;
        if (CuratorLensEffect.isLookingThroughLens(player)) {
            callbackInfo.setReturnValue(CURATOR_LENS_FOV_MULTIPLIER);
        }
    }
}
