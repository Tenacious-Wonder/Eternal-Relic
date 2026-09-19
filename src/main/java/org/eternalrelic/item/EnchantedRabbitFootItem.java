package org.eternalrelic.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * 附魔兔脚 —— 一件永远带着附魔光效的遗物。
 *
 * <p>它与普通遗物在功能上没有区别：效果同样由「携带在背包中」这个条件触发，由能力类负责。
 * 唯一的差别是外观——它看上去是被附魔过的，因此始终覆着一层附魔光效。</p>
 *
 * <p>光效没有做成「往物品上写一条假附魔」：那样会因为附魔数据而让物品显示附魔名、
 * 影响铁砧与附魔台的行为，甚至被别的模组当成真附魔处理。这里只覆写「要不要画光效」
 * 这一个判断，物品数据完全不动。</p>
 */
public class EnchantedRabbitFootItem extends RelicItem {

    public EnchantedRabbitFootItem(Settings settings) {
        super(settings);
    }

    /**
     * 让这件物品始终带有附魔光效。
     *
     * @param stack 正在被绘制的物品堆
     * @return 恒为 {@code true}，因此无论物品数据如何都会画出光效
     */
    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }
}
