package org.eternalrelic.capability.carried;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;

import org.eternalrelic.registry.ModItems;

/**
 * 「雷电」能力：带着雷电纹章时，<b>雷雨天用武器近战打中谁，谁就额外挨 6 点魔法伤害</b>。
 *
 * <p><b>它是模组里第一件"额外那一下走魔法伤害"的遗物。</b>此前的近战类遗物要么改数值
 * （古旧剑带 +0.5）、要么给状态（炽心纹章点着对方），没有一件<b>在物理这一击之外再补一击</b>。
 * 走魔法伤害有个好处：它照原版规矩吃<b>抗性提升与保护附魔</b>的减免，
 * 因此对上装备齐全的敌人会自然衰减，不必另写一套平衡。</p>
 *
 * <p><b>为什么不写进条件遗物表</b>：那张表管的是"给玩家自己挂属性加成"，
 * 而这一件是"给挨打的一方补一下伤害" —— 两件事的路子完全不同。
 * 因此它与炽心纹章一样挂在 {@code ALLOW_DAMAGE} 上，只是那件点火、这件补伤害。</p>
 *
 * <p><b>怎么认出「近战」</b>：看伤害的<b>直接来源</b>是不是玩家本人 ——
 * 近战攻击的直接来源就是出手的玩家，箭与火球的直接来源是飞行中的弹射物。
 * 这与炽心纹章、近战部位判定用的是同一个判据。</p>
 *
 * <p><b>不会递归</b>：补的那一下走魔法伤害，它<b>没有直接来源</b>，
 * 因此不会再被本能力认成"近战"而无限触发下去。</p>
 *
 * <p><b>不碰原来那一击</b>：无论是否补伤害都返回 {@code true}，物理伤害照原样结算，
 * 不与别的遗物抢那一击的结果。</p>
 */
public final class ThunderEmblemEffect {

    /** 雷雨天近战时额外造成的魔法伤害。 */
    private static final float MAGIC_DAMAGE = 6.0F;

    private ThunderEmblemEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，挂上「雷雨天近战补一击」。
     */
    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(ThunderEmblemEffect::allowDamage);
    }

    /**
     * 伤害即将落下时，若出手的是带着纹章、又正在雷雨里的近战玩家，就再补一下魔法伤害。
     *
     * @param victim 挨打的一方
     * @param source 伤害来源
     * @param amount 这一击原本的伤害（本能力不使用，但必须与事件签名一致）
     * @return 恒为 {@code true}，让原来那一击照常结算
     */
    private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
        ServerPlayerEntity attacker = attackerOf(source);

        if (attacker == null || attacker == victim) {
            return true;
        }

        victim.damage(attacker.getDamageSources().magic(), MAGIC_DAMAGE);
        return true;
    }

    /**
     * 找出这次近战的出手者 —— 前提是雷雨天、且他身上带着（或缝着）雷电纹章。
     *
     * @param source 这次伤害的来源
     * @return 出手的玩家；不是近战、不在雷雨里、或者他没带纹章时返回 {@code null}
     */
    private static ServerPlayerEntity attackerOf(DamageSource source) {
        if (!(source.getSource() instanceof ServerPlayerEntity attacker)) {
            return null;
        }

        if (!attacker.getWorld().isThundering()) {
            return null;
        }

        return CarriedStacks.inEffect(attacker, ModItems.THUNDER_EMBLEM) ? attacker : null;
    }
}
