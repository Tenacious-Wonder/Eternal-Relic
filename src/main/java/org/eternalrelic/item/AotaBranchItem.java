package org.eternalrelic.item;

import java.util.List;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

/**
 * 奥塔的枝叶 —— 本模组的第一件永恒遗物。
 *
 * <p>它本身不带任何行为，价值全部来自「携带在背包中」这一条件：
 * 由 {@link org.eternalrelic.capability.carried.CarriedRelicEffect} 按遗物表登记的
 * 效果属性与数值，把生命上限加成挂到玩家身上。因此这里只负责提供名称与说明文字。</p>
 */
public class AotaBranchItem extends Item {

    public AotaBranchItem(Settings settings) {
        super(settings);
    }

    /**
     * 在物品提示框中补上一行效果说明（灰色小字）。
     *
     * @param tooltip 待填充的提示框内容
     */
    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        // 说明文字的翻译键约定：在物品自身的翻译键后追加 ".desc"
        // 对应的键名同样由遗物表给出（见 RelicDefinition#descriptionKey()）
        tooltip.add(Text.translatable(this.getTranslationKey() + ".desc").formatted(Formatting.GRAY));
    }
}
