package org.eternalrelic.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import org.eternalrelic.capability.attached.ShoulderGuardEffect;
import org.eternalrelic.debug.BodyPartHitReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 让缝在胸甲上的肩甲，在被近战或远程打中肩膀时，为那一击减掉几点伤害。
 *
 * <p><b>为什么必须动游戏内部代码</b>：游戏给模组提供的「挨打」事件只能选择把这一击
 * <b>整个放行或者整个取消</b>（回响之环走的就是那条路），没有「少扣 2 点」这个档位。
 * 要做到「按部位减掉一个固定点数」，只能自己插进伤害结算里。</p>
 *
 * <p><b>注入点选在 {@code modifyAppliedDamage} 之后</b>，也就是玩家扣血流程里的这一步：</p>
 *
 * <pre>
 * 护甲减伤 → 保护附魔 / 抗性提升 → ★ 这里（少掉几点） → 金心（吸收）抵扣 → 真正扣血
 * </pre>
 *
 * <p>选在这里有三个理由：一是它<b>在原版该算的都算完之后</b>，减掉的是玩家实际要承受的伤害，
 * 也就是需求里说的「优先级在原版之后」；二是在金心抵扣<b>之前</b>，所以减伤不会被吸收重复抵消，
 * 也不会让金心替我们买单；三是原版的无敌帧判断（挨打后的短暂免疫）用的是<b>减伤之前</b>的数字，
 * 因此连击时「第二下算不算数」的规矩不受本改动影响。</p>
 *
 * <p><b>与回响之环的关系</b>：那一件在更前面的挨打事件里就把整一击取消掉了，那种情况下
 * 根本走不到这里，两件遗物不会互相打架。</p>
 *
 * <p>只改这一个数值，不动伤害流程的其它任何一步；扣到 0 为止，不会倒扣成加血。</p>
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerDamageMixin {

    /**
     * 在伤害即将落到玩家身上之前，按打中的部位与胸甲上的肩甲，把这一击减掉几点。
     *
     * <p>只认服务端：客户端那份结算会被服务端覆盖，处理了也是白做。</p>
     *
     * @param amount 原版刚算完这一击的伤害（护甲与保护附魔都已计入）
     * @return 减掉肩甲那几点之后的伤害；没有肩甲护着时原样返回
     */
    @ModifyVariable(
            method = "applyDamage",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;modifyAppliedDamage(Lnet/minecraft/entity/damage/DamageSource;F)F",
                    shift = At.Shift.AFTER),
            argsOnly = true)
    private float eternal_relic$guardShoulder(float amount) {
        if (amount <= 0.0F) {
            return amount;
        }

        if (!((Object) this instanceof ServerPlayerEntity player)) {
            return amount;
        }

        float blocked = ShoulderGuardEffect.reductionFor(player);
        float guarded = Math.max(0.0F, amount - blocked);

        // 临时调试输出：把「原版算完后多少、肩甲减完多少」打在聊天栏里（见 BodyPartHitReport）。
        // 它只读这几个数字、不参与结算，与那段调试代码一并删除即可
        BodyPartHitReport.report(player, amount, guarded, amount - guarded);

        return guarded;
    }
}
