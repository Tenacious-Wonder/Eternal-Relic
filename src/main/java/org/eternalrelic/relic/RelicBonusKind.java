package org.eternalrelic.relic;

import net.minecraft.entity.attribute.EntityAttributeModifier;

/**
 * 遗物属性加成的计算方式。
 *
 * <p>决定登记在遗物表里的那个数值怎么落到玩家身上。两种方式适用的属性不同：</p>
 * <ul>
 *   <li>{@link #PERCENT} 在最终总量上乘一个百分比。生命上限、移动速度、攻击力、护甲
 *       这类本身有非零基数的属性适合它，加成会随玩家已有的装备一起放大。</li>
 *   <li>{@link #FLAT} 直接加上一个固定数值。护甲「再加 2 点」、击退抗性这类以点数为单位、
 *       且基数可能为 0 的属性只能用它——基数为 0 时乘任何百分比都还是 0。</li>
 * </ul>
 */
public enum RelicBonusKind {

    /** 按最终总量乘以一个百分比，例如 {@code 0.12} 表示 +12%。 */
    PERCENT(EntityAttributeModifier.Operation.MULTIPLY_TOTAL),

    /** 直接加上一个固定数值，例如 {@code 2.0} 表示 +2 点。 */
    FLAT(EntityAttributeModifier.Operation.ADDITION);

    private final EntityAttributeModifier.Operation operation;

    private RelicBonusKind(EntityAttributeModifier.Operation operation) {
        this.operation = operation;
    }

    /**
     * @return 本方式对应的属性运算
     */
    public EntityAttributeModifier.Operation operation() {
        return this.operation;
    }

    // 这里原先还有一个 valueBefore(modifiedValue, bonus)：由"加成挂上之后的数值"反推"挂上之前"。
    // 它已经被删掉，因为它**在数学上还原不出真正想要的那个数** —— 反推出来的是"完全没有加成时的
    // 数值"，而不是"改动前那一刻"的数值，只有"从 0 件变成 1 件"时才碰巧正确。
    // 生命上限的折算现已改为**改动之前先把上限量下来**（见 CarriedRelicEffect.keepHealthRatio）。
    // 想再引入类似的"反推"之前，请先读那段注释。
}
