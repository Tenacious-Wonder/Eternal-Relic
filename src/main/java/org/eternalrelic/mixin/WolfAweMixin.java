package org.eternalrelic.mixin;

import net.minecraft.entity.passive.WolfEntity;

import org.eternalrelic.capability.carried.WolfAweEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让每一只野狼定期回头看一眼：身边有没有带着可怕狼牙吊坠的玩家。
 *
 * <p><b>为什么挂在狼自己身上，而不是在服务端定时扫描</b>：挂在狼身上就不必在模组里维护
 * 「哪些狼正被我按着」这本账。账本一旦存在，就得处理狼被杀死、区块被卸载、
 * 服务器重启这些情况，任何一处漏掉都会留下「永远坐着的野狼」。
 * 让狼自己看自己，这些问题就都不存在了——真正的状态只有「它此刻坐着没有」这一个。</p>
 *
 * <p><b>为什么挂在 {@code tick} 的末尾</b>：狼在这一刻的移动、寻路、AI 都已经跑完，
 * 此时再决定坐与不坐，本刻之内不会再被别的逻辑改写。判断本身是隔十刻做一次，
 * 因此每刻只是拿年龄取个模，代价可以忽略。</p>
 *
 * <p>只读取「附近有没有携带者」这一个事实并据此设置坐下标志，不改变原版的其它任何行为。</p>
 */
@Mixin(WolfEntity.class)
public abstract class WolfAweMixin {

    /**
     * 狼每刻走完自己的逻辑之后，按「身边有没有带着狼牙吊坠的玩家」决定坐还是站。
     *
     * @param callbackInfo 原方法（无返回值）的回调；这里不改变它
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void eternal_relic$cowerBeforeWolfFang(CallbackInfo callbackInfo) {
        WolfAweEffect.update((WolfEntity) (Object) this);
    }
}
