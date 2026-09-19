package org.eternalrelic.relic;

/**
 * 遗物的「携带生效」效果：携带时按数量放大的属性加成。
 *
 * @param attribute     被影响的属性
 * @param kind          加成的计算方式（百分比或固定值）
 * @param firstValue    携带第一件时的加成数值；百分比方式下 0.12 表示 +12%，固定值方式下 2.0 表示 +2 点
 * @param perExtraValue 背包中每多携带一件时追加的加成数值
 * @param maxCount      参与计算的件数上限
 */
public record RelicEffect(RelicAttribute attribute, RelicBonusKind kind, double firstValue, double perExtraValue,
        int maxCount) {

    /**
     * 登记一件按百分比加成的遗物。
     *
     * @param attribute     被影响的属性
     * @param firstValue    携带第一件时的加成倍率，例如 0.12 表示 +12%
     * @param perExtraValue 每多携带一件时追加的加成倍率
     * @param maxCount      参与计算的件数上限
     */
    public RelicEffect(RelicAttribute attribute, double firstValue, double perExtraValue, int maxCount) {
        this(attribute, RelicBonusKind.PERCENT, firstValue, perExtraValue, maxCount);
    }

    /**
     * 登记一件只加固定数值、且多带也不会更强的遗物。
     *
     * <p>件数上限固定为 1：背包里带一件与带十件拿到的加成相同。</p>
     *
     * @param attribute 被影响的属性
     * @param value     加成数值，例如 2.0 表示 +2 点
     * @return 登记好的加成
     */
    public static RelicEffect flat(RelicAttribute attribute, double value) {
        return new RelicEffect(attribute, RelicBonusKind.FLAT, value, 0.0D, 1);
    }

    /**
     * 登记一件「每多一份就再加同样多」的固定值加成。
     *
     * <p>与 {@link #flat} 的区别：那个多带没有用，这个按份数往上加，加到 {@code maxCount} 份为止。
     * 适合「每个部位各给一点」这类遗物——例如一片甲片只给半点护甲，四个部位合起来才够看。</p>
     *
     * @param attribute    被影响的属性
     * @param valuePerCopy 每一份的加成数值，例如 0.5 表示每份 +0.5 点
     * @param maxCount     最多算几份
     * @return 登记好的加成
     */
    public static RelicEffect flatPerCopy(RelicAttribute attribute, double valuePerCopy, int maxCount) {
        return new RelicEffect(attribute, RelicBonusKind.FLAT, valuePerCopy, valuePerCopy, maxCount);
    }

    /**
     * 计算给定携带件数对应的加成数值。
     *
     * @param count 携带件数
     * @return 加成数值；百分比方式下 0.12 表示 +12%，固定值方式下 2.0 表示 +2 点
     */
    public double valueFor(int count) {
        int effective = Math.min(count, this.maxCount);
        return this.firstValue + this.perExtraValue * (effective - 1);
    }
}
