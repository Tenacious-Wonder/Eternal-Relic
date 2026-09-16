package org.eternalrelic.item;

import java.util.List;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

/**
 * 只提供名称与说明文字的遗物物品 —— 供尚无自定义行为的遗物使用。
 *
 * <p>遗物的价值通常来自「携带在背包中」这一条件，由能力类负责生效；本类只承担物品本身的
 * 门面：名称、提示框里的那一行说明。需要主动行为的遗物（如右键装入的守夜之瞳）另有自己的
 * 物品类。</p>
 */
public class RelicItem extends Item {

    public RelicItem(Settings settings) {
        super(settings);
    }

    /**
     * 在物品提示框中补上一行效果说明（灰色小字）。
     *
     * <p>说明文字的翻译键约定：在物品自身的翻译键后追加 {@code ".desc"}，
     * 对应的键名同样由遗物表给出（见 {@code RelicDefinition#descriptionKey()}）。</p>
     *
     * @param stack   正在查看的物品
     * @param world   玩家所在的世界；在部分界面里可能为 {@code null}
     * @param tooltip 待填充的提示框内容
     */
    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable(this.getTranslationKey() + ".desc").formatted(Formatting.GRAY));
    }
}
