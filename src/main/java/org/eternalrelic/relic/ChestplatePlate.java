package org.eternalrelic.relic;

import java.util.Set;

import org.eternalrelic.bodypart.BodyPart;

/**
 * 一件「胸甲片」的配置 —— 胸甲片表（{@code registry/ChestplatePlates}）里的一行。
 *
 * <p>它回答四件事：这一击打在某个部位上时，这件胸甲片管不管；管的话这一击少掉几点伤害；
 * 远程打在这个部位上时有没有机会把这一击整个弹开；以及它替玩家挡下几点<b>魔法伤害</b>。</p>
 *
 * <p><b>它与肩甲是两回事，只是算账的方式相似</b>：肩甲护的是左右两侧肩膀，胸甲片护的是
 * <b>正胸</b>那一块——两者可以同时缝在一件胸甲上（配件类别不同），挨打时各算各的。</p>
 *
 * <p><b>护住的是「正胸」，不是「整个躯干」</b>：{@link BodyPart#CHEST} 只占躯干的<b>前半</b>，
 * 背后那一刀落在 {@link BodyPart#BACK} 上，胸甲片一点都挡不住。这是刻意分开的——
 * 一块贴在胸前的甲片护不住后背，说不过去。</p>
 *
 * <p><b>魔法减伤不看部位</b>：药水、凋零、龙息这类伤害是从身体内部发作的，
 * 游戏根本不会产生「打中哪儿」的消息，因此 {@link #magicReduction} 只在「缝着没有」这一层判断，
 * 与命中点无关。</p>
 *
 * <p>与 {@link ShoulderGuard} 同一路做法：减的是原版算完之后的数字，扣到 0 为止不倒扣；
 * 弹开与减伤是两件事（{@code deflectChance} 为 0 就是不会弹）。</p>
 *
 * @param guardedParts   这件胸甲片护住的身体部位（目前只有正胸）
 * @param reduction      这一击少掉几点伤害
 * @param deflectChance  远程打中护着的部位时，有多大概率把这一击整个弹开；0 表示不会弹
 * @param magicReduction 替玩家挡下的魔法伤害点数；0 表示不挡
 */
public record ChestplatePlate(Set<BodyPart> guardedParts, float reduction, float deflectChance,
        float magicReduction) {

    /**
     * 收下登记的部位并复制一份存起来，让这份清单此后只读。
     */
    public ChestplatePlate {
        guardedParts = Set.copyOf(guardedParts);
    }

    /**
     * @param part 这一击打中的部位
     * @return 这一击是否落在本胸甲片护着的部位上
     */
    public boolean guards(BodyPart part) {
        return this.guardedParts.contains(part);
    }

    /**
     * @return 这件胸甲片是否有可能弹开远程攻击
     */
    public boolean canDeflect() {
        return this.deflectChance > 0.0F;
    }
}
