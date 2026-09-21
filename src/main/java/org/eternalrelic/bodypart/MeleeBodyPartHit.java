package org.eternalrelic.bodypart;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * 一次「近战打中玩家某个部位」的完整记录。
 *
 * <p>近战没有可以求交点的飞行轨迹——双方贴在一起，游戏也不会告诉你刀落在肩膀上还是肚子上，
 * 因此这里没有命中点，部位是<b>按概率抽出来的</b>（抽签表与算法见 {@link MeleeBodyPartDetector}）。</p>
 *
 * @param player   被命中的玩家
 * @param attacker 下手的那一方（怪、玩家或别的生物）
 * @param part     抽中的部位
 */
public record MeleeBodyPartHit(PlayerEntity player, LivingEntity attacker, BodyPart part)
        implements BodyPartHit {
}
