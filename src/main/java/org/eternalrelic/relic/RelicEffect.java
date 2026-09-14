package org.eternalrelic.relic;

/**
 * 遗物的「携带生效」效果：携带时按数量放大的属性加成。
 *
 * @param attribute       被影响的属性
 * @param firstValue      携带第一件时的加成倍率，例如 0.12 表示 +12%
 * @param perExtraValue   背包中每多携带一件时追加的加成倍率
 * @param maxCount        参与计算的件数上限
 */
public record RelicEffect(RelicAttribute attribute, double firstValue, double perExtraValue, int maxCount) {

    /**
     * 计算给定携带件数对应的加成倍率。
     *
     * @param count 携带件数
     * @return 加成倍率，例如 0.12 表示 +12%
     */
    public double valueFor(int count) {
        int effective = Math.min(count, this.maxCount);
        return this.firstValue + this.perExtraValue * (effective - 1);
    }
}
