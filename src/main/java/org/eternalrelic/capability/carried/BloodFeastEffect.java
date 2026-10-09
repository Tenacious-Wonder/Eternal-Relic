package org.eternalrelic.capability.carried;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;

import org.eternalrelic.registry.ModItems;

/**
 * 「噬血」能力：带着噬血护符时，<b>亲手击杀生物后按它的最大生命回一点血</b>。
 *
 * <p>在这一件之前，模组的击杀类遗物只管"多掉东西"（猎人徽章、剥皮小刀）与"攒魂火"（引魂燃灯），
 * 没有一件把击杀变成<b>续航</b>。这一件补上的正是这条：血少了就往怪堆里冲，靠击杀回血。</p>
 *
 * <p><b>回多少看对方有多大，且有上限。</b>按被杀生物最大生命的 {@value #HEAL_RATIO}
 * 折算，单次最多 {@value #MAX_HEAL} 点 —— 前者让"打大怪回得多"成立，
 * 后者挡住"一击砍死末影龙当场回满"这种荒唐账。</p>
 *
 * <p><b>只有玩家亲手击杀才算</b>：凶手取自伤害来源，怪物互相残杀、摔死、烧死都没有凶手，
 * 一律不回血。<b>宠物与队友被杀同样不算</b> —— 那本就不是玩家的战果。</p>
 */
public final class BloodFeastEffect {

    /** 按其最大生命的这个比例回血。 */
    private static final float HEAL_RATIO = 0.10F;

    /** 单次击杀最多回这么多点。 */
    private static final float MAX_HEAL = 4.0F;

    private BloodFeastEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，挂上击杀的回调。
     */
    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(BloodFeastEffect::onDeath);
    }

    /**
     * 玩家击杀生物时给他回一点血。
     *
     * @param entity 被击杀的生物
     * @param source 致死伤害的来源
     */
    private static void onDeath(LivingEntity entity, DamageSource source) {
        if (!(source.getAttacker() instanceof ServerPlayerEntity player) || entity == player) {
            return;
        }

        if (!CarriedStacks.inEffect(player, ModItems.BLOOD_FEAST_CHARM)) {
            return;
        }

        // 满血时不必回（heal 本身无害，但少一次无谓的属性同步）
        if (!player.isAlive() || player.getHealth() >= player.getMaxHealth()) {
            return;
        }

        float heal = Math.min(MAX_HEAL, entity.getMaxHealth() * HEAL_RATIO);

        if (heal > 0.0F) {
            player.heal(heal);
        }
    }
}
