package org.eternalrelic.mixin;

import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import org.eternalrelic.bodypart.BodyPart;
import org.eternalrelic.bodypart.RecentBodyPartHit;
import org.eternalrelic.capability.attached.ChestplatePlateEffect;
import org.eternalrelic.capability.attached.ShoulderGuardEffect;
import org.eternalrelic.debug.BodyPartHitReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 让缝在胸甲上的护具（肩甲与胸甲片），在被近战或远程打中它护着的那一块时，为那一击减掉几点伤害；
 * 胸甲片还会替玩家挡下几点魔法伤害。
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
 * <p><b>伤害来源是从原方法的参数里取的</b>（{@code @Local(argsOnly = true)}），用来认出魔法伤害。
 * 它按<b>类型</b>取，因此不必把那次调用的参数表整个列出来——这也是本模组取原方法参数的既有做法
 * （见 {@code EnchantmentHelperMixin}）。</p>
 *
 * <p><b>与回响之环的关系</b>：那一件在更前面的挨打事件里就把整一击取消掉了，那种情况下
 * 根本走不到这里，两件遗物不会互相打架。</p>
 *
 * <p><b>「这一击打中哪儿」的便条在这里取走，而且只取一次</b>：一件胸甲上可以同时缝着肩甲与
 * 胸甲片，而便条取走就没了（见 {@link RecentBodyPartHit}），所以由这里取一次、再分别问两张表。
 * 魔法伤害没有「打中哪儿」可算（药水与凋零是从体内发作的），那一条只看伤害类型。</p>
 *
 * <p>只改这一个数值，不动伤害流程的其它任何一步；扣到 0 为止，不会倒扣成加血。</p>
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerDamageMixin {

    /**
     * 在伤害即将落到玩家身上之前，按打中的部位与胸甲上的护具，把这一击减掉几点。
     *
     * <p>只认服务端：客户端那份结算会被服务端覆盖，处理了也是白做。</p>
     *
     * @param amount 原版刚算完这一击的伤害（护甲与保护附魔都已计入）
     * @param source 这一击的伤害来源（从原方法的参数里取），用来认出魔法伤害
     * @return 减掉护具那几点之后的伤害；没有护具护着时原样返回
     */
    @ModifyVariable(
            method = "applyDamage",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;modifyAppliedDamage(Lnet/minecraft/entity/damage/DamageSource;F)F",
                    shift = At.Shift.AFTER),
            argsOnly = true)
    private float eternal_relic$guardAttachments(float amount, @Local(argsOnly = true) DamageSource source) {
        if (amount <= 0.0F) {
            return amount;
        }

        if (!((Object) this instanceof ServerPlayerEntity player)) {
            return amount;
        }

        // 便条只取一次：这一击打在哪儿，肩甲与胸甲片共用这一份
        BodyPart part = RecentBodyPartHit.consume(player);

        float blocked = ShoulderGuardEffect.reductionFor(player, part)
                + ChestplatePlateEffect.reductionFor(player, part);

        // 魔法伤害没有「打中哪儿」可算，只要胸甲上缝着会挡魔法的胸甲片就减
        if (ChestplatePlateEffect.isMagicDamage(source)) {
            blocked += ChestplatePlateEffect.magicReductionFor(player);
        }

        float guarded = Math.max(0.0F, amount - blocked);

        // 临时调试输出：把「原版算完后多少、护具减完多少」打在聊天栏里（见 BodyPartHitReport）。
        // 它只读这几个数字、不参与结算，与那段调试代码一并删除即可
        BodyPartHitReport.report(player, amount, guarded, amount - guarded);

        return guarded;
    }
}
