package org.eternalrelic.bodypart;

import net.minecraft.entity.player.PlayerEntity;

/**
 * <h1>一次「打中玩家某个部位」的公共信息</h1>
 *
 * <p>不论这一下是<b>射过来的</b>还是<b>砍过来的</b>，订阅方最需要的两件事都一样：
 * <b>被打的是谁</b>、<b>打在哪儿</b>。这两项抽成接口，两种来源各自实现，
 * 于是「打中头就加倍伤害」这类玩法只需订阅一次，不必分别对接两条来路。</p>
 *
 * <p>各自独有的信息留在各自的记录里：弹射物那一种还能给出命中点坐标（见
 * {@link ProjectileBodyPartHit}），近战那一种能给出下手的人（见 {@link MeleeBodyPartHit}）。</p>
 *
 * @see BodyPartHits 订阅这些命中的唯一入口
 */
public interface BodyPartHit {

    /** 被命中的玩家。 */
    PlayerEntity player();

    /** 命中的部位。 */
    BodyPart part();
}
