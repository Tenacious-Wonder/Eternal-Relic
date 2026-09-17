package org.eternalrelic.item;

import net.minecraft.item.Item;

/**
 * 奥塔的枝叶 —— 本模组的第一件永恒遗物。
 *
 * <p>它本身不带任何行为，价值全部来自「携带在背包中」这一条件：
 * 由 {@link org.eternalrelic.capability.carried.CarriedRelicEffect} 按遗物表登记的
 * 效果属性与数值，把生命上限加成挂到玩家身上。名称、品阶与效果说明由语言文件和遗物界面承担，
 * 提示框里只留一句「按左 Shift 详细查看」。</p>
 */
public class AotaBranchItem extends Item {

    public AotaBranchItem(Settings settings) {
        super(settings);
    }
}
