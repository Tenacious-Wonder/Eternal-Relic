package org.eternalrelic.bodypart;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.Vec3d;

/**
 * 一次「弹射物打中玩家某部位」的完整记录。
 *
 * <p>判定结果本身只有一个部位，但后续要做效果时通常还需要知道<b>是谁射的、射中了谁、打在哪个点</b>，
 * 所以这里一并带上，避免将来为了多拿一个信息再回头改判定层。</p>
 *
 * @param player     被命中的玩家
 * @param projectile 命中的那颗弹射物（箭、三叉戟、火球等）
 * @param part       命中的部位
 * @param hitPos     命中点的世界坐标
 */
public record ProjectileBodyPartHit(PlayerEntity player, ProjectileEntity projectile, BodyPart part, Vec3d hitPos) {
}
