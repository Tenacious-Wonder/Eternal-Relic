package org.eternalrelic.relic;

import net.minecraft.item.Item;

/**
 * 遗物替玩家挡下攻击的「守护」效果 —— 遗物表里的第二类能力插槽。
 *
 * <p>{@link RelicEffect} 描述的是「一直挂在身上的数值」，而这一份描述的是
 * 「每次挨打时出手一次」的效果，分两档：伤害不超过 {@link #blockThreshold} 的攻击被整个挡下；
 * 更重的攻击挡不干净，改为当场化出若干颗金心（吸收）替玩家分摊。带有这份配置的遗物，
 * 只要被玩家携带（主背包或副手）就有资格出手，不需要装入、也不需要右键。</p>
 *
 * <p>出手之后遗物会碎裂成 {@link #drainedForm}，并进入 {@link #cooldownTicks} 刻的冷却；
 * 碎裂期间它不再提供守护，冷却走完后自行恢复原样。</p>
 *
 * @param drainedForm         出手后碎裂成的形态
 * @param blockThreshold      能够完全挡下的伤害上限
 * @param leastHearts         刚超过门槛时给予的金心数（下限）
 * @param mostHearts          伤害达到上限时给予的金心数（上限）
 * @param damageForMostHearts 给到金心上限所需的伤害
 * @param cooldownTicks       碎裂后需要经过多少刻才恢复原样
 */
public record DamageWard(Item drainedForm, float blockThreshold, int leastHearts, int mostHearts,
        float damageForMostHearts, int cooldownTicks) {

    /** 一颗金心抵多少点吸收。 */
    private static final float ABSORPTION_PER_HEART = 2.0F;

    /**
     * 计算一次攻击应当当场给玩家多少点吸收（金心）。
     *
     * <p>刚超过门槛时给下限颗数，随着伤害升高线性增加，到「上限伤害」处封顶。
     * 颗数一律向上取整：金心没有「小半颗」的说法，宁可多给一点，也不让玩家吃亏——
     * 代价是进位会提前，刚过门槛实际拿到的是下限再加一颗，封顶也会比「上限伤害」来得早一些。</p>
     *
     * @param amount 这一击本身的伤害数值
     * @return 应当给予的吸收点数；伤害没有超过门槛时返回 0
     */
    public float absorptionFor(float amount) {
        if (amount <= this.blockThreshold) {
            return 0.0F;
        }

        float span = this.damageForMostHearts - this.blockThreshold;
        float ratio = span <= 0.0F ? 1.0F : Math.min((amount - this.blockThreshold) / span, 1.0F);
        float hearts = this.leastHearts + (this.mostHearts - this.leastHearts) * ratio;

        return (float) Math.ceil(hearts) * ABSORPTION_PER_HEART;
    }
}
