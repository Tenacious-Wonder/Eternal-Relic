package org.eternalrelic.capability.carried;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import org.eternalrelic.registry.ModItems;

/**
 * 「安抚」能力：带着末影吊坠时，<b>末影人不会因为你盯着它而发怒</b>。
 *
 * <p>这是模组里第二件针对特定生物的遗物（第一件是镇住野狼的可怕狼牙吊坠）。挑末影人的理由：
 * 它的敌意来源<b>只有"被注视"</b>这一条 —— 你打它，它当然还手；但你不打它、只是路过看它一眼，
 * 它就会追你一路。挂坠拦的正是这一条。</p>
 *
 * <h2>它不做什么</h2>
 * <p><b>不改变"攻击它就会还手"。</b>挂了挂坠去打末影人，照样会被追打 —— 那属于活该，
 * 也与"不会被主动攻击"这句话不冲突。因此这一件拦在
 * {@code EndermanEntity#isPlayerStaring}（"你在盯着它吗"）那一处，
 * 而不是拦"设谁为目标"（那会把反击也一并拦掉）。</p>
 *
 * <p>由 {@link org.eternalrelic.mixin.EnderPendantMixin} 调用。</p>
 */
public final class EnderPendantEffect {

    private EnderPendantEffect() {
    }

    /**
     * 这位玩家此刻是不是带着末影吊坠。
     *
     * @param player 正在被末影人打量（或打量末影人）的那位玩家
     * @return 带着挂坠时为 {@code true} —— 此时末影人不该因注视而发怒
     */
    public static boolean soothes(PlayerEntity player) {
        return player instanceof ServerPlayerEntity serverPlayer
                && CarriedStacks.inEffect(serverPlayer, ModItems.ENDER_PENDANT);
    }
}
