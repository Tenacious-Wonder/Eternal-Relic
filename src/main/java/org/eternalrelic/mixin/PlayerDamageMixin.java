package org.eternalrelic.mixin;

import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import org.eternalrelic.bodypart.BodyPart;
import org.eternalrelic.bodypart.RecentBodyPartHit;
import org.eternalrelic.capability.attached.ChestGuardEffect;
import org.eternalrelic.capability.carried.ReversalPendantEffect;
import org.eternalrelic.capability.carried.ThornsOathEffect;
import org.eternalrelic.debug.BodyPartHitReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 玩家受伤结算里的一处总闸：给缝在胸甲上的护具按部位减伤、替胸甲片挡下几点魔法伤害，
 * 另外还替两件遗物搭了手 —— <b>荆棘之誓</b>在这里取「最终实际承受的伤害」（挨打事件里的数字
 * 是护甲都没算的原始伤害，取不到最终值），<b>颠倒吊坠</b>在这里决定「这一击到底算不算数」。
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
 * <p>只改这一个数值，不动伤害流程的其它任何一步；不会倒扣成加血。两条减伤的收敛方式不同：
 * <b>按部位的那一条可以扣到 0</b>（挡下轻击本来就是护具的本事），而<b>魔法那一条至少留 1 点</b>
 * ——理由见方法里的注释：中毒与凋零每跳只有 1 点，抹平就等于白送一个免疫。</p>
 *
 * <p><b>荆棘之誓也搭在这一处</b>：它要的不是「挨打事件里的原始伤害」，而是玩家最终实际掉的血，
 * 而那个数字只有在护甲与保护附魔都算完之后才拿得到，因此它借这一个注入点取值
 * （见 {@link ThornsOathEffect#reflect}）。它自己并不改动这一击的数值——
 * 那一件遗物从不替玩家挡伤害。<b>若日后挪动或删掉这个注入点，荆棘之誓会跟着一起失效。</b></p>
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

        // 按部位的减伤：胸甲上缝着的护具各减几点，**可以扣到 0**——挡下轻击本来就是护具的本事
        float blocked = ChestGuardEffect.reductionFor(player, part);

        float guarded = Math.max(0.0F, amount - blocked);

        // 魔法减伤另算，而且**至少给对方留 1 点**。
        //
        // 为什么单独放宽这一条：中毒与凋零的伤害是每跳 1 点，而这里也是减 1 点——
        // 若照"扣到 0 为止"，这 1 点会被整个抹平，等于白送一个「免疫中毒与凋零」，
        // 比「受到的魔法伤害减少 1 点」这句话强得多。因此这一条只削不灭。
        // 末尾再与扣之前取较小值：万一原始伤害本来就不足 1 点，不能被这条抬高。
        if (ChestGuardEffect.isMagicDamage(source)) {
            float magic = ChestGuardEffect.magicReductionFor(player);
            if (magic > 0.0F) {
                guarded = Math.min(guarded, Math.max(1.0F, guarded - magic));
            }
        }

        // 临时调试输出：把「原版算完后多少、护具减完多少」打在聊天栏里（见 BodyPartHitReport）。
        // 它只读这几个数字、不参与结算，与那段调试代码一并删除即可
        BodyPartHitReport.report(player, amount, guarded, amount - guarded);

        // 荆棘之誓：把玩家「最终实际承受的那部分」的五分之一扎回给动手的人（单次上限 20 点）。
        // 为什么放在这一刻：此处已经算完护甲、保护附魔与胸甲护具，再扣掉金心（吸收）就是游戏
        // 接下去真正会从血条上扣掉的那个数——正是制作者要的「最终数值」。挨打事件里的数字则是
        // 这些全都没算的原始伤害，取不到最终值，所以那一件**不挂挨打事件**。
        // 金心整个挡下时传进去的是 0，能力类据此不出手。
        ThornsOathEffect.reflect(player, source, guarded - player.getAbsorptionAmount());

        // 颠倒吊坠：偶尔把这一击整个反过来——不掉血、改成回等量的血，代价是等量的经验点数。
        // 放在荆棘之誓之后：两件同时带着时，先按最终伤害把这一下扎回去，
        // 再由颠倒决定"这一下到底算不算数"（归零即表示没落下）
        if (ReversalPendantEffect.reverses(player, guarded)) {
            return 0.0F;
        }

        return guarded;
    }
}
