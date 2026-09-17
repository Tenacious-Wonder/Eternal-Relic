package org.eternalrelic.item;

import net.minecraft.item.Item;

/**
 * 只提供名称的遗物物品 —— 供尚无自定义行为的遗物使用。
 *
 * <p>遗物的价值通常来自「携带在背包中」这一条件，由能力类负责生效；名称、品阶与效果说明
 * 分别由语言文件和遗物界面承担，提示框里只留一句「按左 Shift 详细查看」。
 * 因此本类目前只是一层占位，留待将来给它挂上行为。</p>
 */
public class RelicItem extends Item {

    public RelicItem(Settings settings) {
        super(settings);
    }
}
