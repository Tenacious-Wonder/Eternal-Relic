package org.eternalrelic.mixin;

import net.minecraft.registry.tag.GameEventTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.event.GameEvent;

import org.eternalrelic.capability.carried.SilentBootsEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让带着无声软靴、并且正在潜行的玩家<b>不发出任何振动</b>。
 *
 * <p><b>为什么必须动游戏内部代码</b>：振动没有给模组的监听口子——感测体、校准感测体、
 * 尖啸体与监守者都从同一条链上取消息（{@code emitGameEvent → GameEventDispatchManager →
 * VibrationListener}），模组既不能在事前问一句「要不要发」，也没有可取消的事件。
 * 因此只能挂在这条链的<b>源头</b>上。</p>
 *
 * <p><b>为什么拦在 {@code ServerWorld#emitGameEvent} 这一层，而不是 {@code Entity#emitGameEvent}</b>：
 * 挖掘与放置方块这两件事是<b>方块</b>发出来的（{@code Block#onBreak} 与 {@code BlockItem#place} 走的是
 * {@code world.emitGameEvent(pos, Emitter.of(player))}），根本不经过玩家实体的那个方法。
 * 拦在这一层，实体事件与方块事件就一起盖住了。</p>
 *
 * <p><b>只拦振动、只拦玩家、只拦潜行</b>——三条都要中，这是刻意的：</p>
 * <ul>
 *   <li>按 {@code GameEventTags.VIBRATIONS} 过滤，不碰悦灵、成就统计等走同一入口的其它事件；</li>
 *   <li>来源必须是玩家（方块与活塞发的振动，来源不是玩家，一律照发）；</li>
 *   <li>带着软靴<b>且正在潜行</b>才算数（见 {@link SilentBootsEffect#hidesFromSenses}）。</li>
 * </ul>
 *
 * <p><b>已知的一个边角</b>：玩家直接站在感测体方块上走动时，原版走的是
 * {@code SculkSensorBlock#onSteppedOn} 里一条直达捷径（{@code forceListen}），绕过了本方法，
 * 因此那一种情况仍然会被听到。拦它需要另动一处方块类，代价与收益都不划算，暂时不做。</p>
 */
@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {

    /**
     * 振动将要发出去时，把「带着软靴潜行的玩家」发出的那些拦下。
     *
     * @param event        这次要发出的游戏事件
     * @param emitterPos   事件发生的位置
     * @param emitter      事件的发出者信息（里面带着来源实体）
     * @param callbackInfo 原方法（无返回值）的回调；取消即表示这一次不发
     */
    @Inject(method = "emitGameEvent(Lnet/minecraft/world/event/GameEvent;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/world/event/GameEvent$Emitter;)V",
            at = @At("HEAD"), cancellable = true)
    private void eternal_relic$silentBootsMuffleVibrations(GameEvent event, Vec3d emitterPos, GameEvent.Emitter emitter,
                                                           CallbackInfo callbackInfo) {
        if (emitter == null || !event.isIn(GameEventTags.VIBRATIONS)) {
            return;
        }

        if (!(emitter.sourceEntity() instanceof ServerPlayerEntity player)) {
            return;
        }

        if (SilentBootsEffect.hidesFromSenses(player)) {
            callbackInfo.cancel();
        }
    }
}
