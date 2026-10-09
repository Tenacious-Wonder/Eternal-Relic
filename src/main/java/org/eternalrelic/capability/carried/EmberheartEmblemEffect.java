package org.eternalrelic.capability.carried;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;

import org.eternalrelic.registry.ModItems;

/**
 * 「炽心」能力：带着炽心纹章的人，<b>每次近战打中谁，谁就烧起来</b>。
 *
 * <p><b>放在背包里就生效</b>，缝在正穿着的防具上同样生效 —— 判定交给
 * {@link CarriedStacks#inEffect}，它同时认「背包里的本体」与「正穿着的装备上附着的那一枚」。</p>
 *
 * <p>★ <b>这是制作者 2026-10-06 定下的纹章口径</b>：<b>纹章是「带在身上就有用」的东西</b>，
 * 缝上去只是多一条生效途径，不是前提。唯一例外是「给附着物补附魔」的那几枚纹章
 * （永恒、川流），它们必须缝上去才有对象可补，见 {@code registry/EnchantingRelics}。
 * <b>做新纹章时照这条走</b>，别再写成「只认附着份」。</p>
 *
 * <p><b>为什么挂 {@code ALLOW_DAMAGE} 而不是 mixin</b>：项目里已经有这条现成的路
 * （守护、附魔兔脚、近战部位判定都挂在它上面），「谁打了谁」从这里就能看清，
 * 不必再动游戏内部代码。</p>
 *
 * <p><b>怎么认出「近战」</b>：看伤害的<b>直接来源</b>是不是玩家本人。近战攻击的直接来源就是出手的那一方；
 * 箭、火球那类远程的直接来源是飞行中的弹射物（射手只算「主使」），因此拿弓弩射人不会点着——
 * 这一点与 {@code MeleeBodyPartDetector} 用的判据完全一致。</p>
 *
 * <p><b>不碰伤害本身</b>：这一处只点火，无论是否出手都返回 {@code true}，
 * 伤害照原样结算，因此不会与别的遗物抢那一击的结果。</p>
 */
public final class EmberheartEmblemEffect {

    /** 被点着多久。10 秒 —— 比原版火焰附加 II（8 秒）再长一点。 */
    private static final int BURN_SECONDS = 10;

    private EmberheartEmblemEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，挂上「近战点火」这一件事。
     */
    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(EmberheartEmblemEffect::allowDamage);
    }

    /**
     * 伤害即将落下时，若出手的近战玩家带着炽心纹章，就把挨打的那一方点着。
     *
     * @param victim 挨打的一方
     * @param source 伤害来源
     * @param amount 这一击的伤害数值（本能力不使用，但必须与事件签名一致）
     * @return 恒为 {@code true}，让伤害照常结算
     */
    private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
        ServerPlayerEntity attacker = attackerOf(source);

        if (attacker == null || attacker == victim) {
            return true;
        }

        victim.setOnFireFor(BURN_SECONDS);
        return true;
    }

    /**
     * 找出这次近战的出手者 —— 前提是他身上带着（或缝着）炽心纹章。
     *
     * @param source 这次伤害的来源
     * @return 出手的玩家；不是近战、或者他没带纹章时返回 {@code null}
     */
    private static ServerPlayerEntity attackerOf(DamageSource source) {
        if (!(source.getSource() instanceof ServerPlayerEntity attacker)) {
            return null;
        }

        return CarriedStacks.inEffect(attacker, ModItems.EMBERHEART_EMBLEM) ? attacker : null;
    }
}
