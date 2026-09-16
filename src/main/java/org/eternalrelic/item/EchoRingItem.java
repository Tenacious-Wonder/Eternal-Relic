package org.eternalrelic.item;

import java.util.List;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

/**
 * 回响之环 —— 携带在背包中时替玩家挡下攻击的遗物。
 *
 * <p>它本身不带任何行为，价值全部来自「携带在背包中」这一条件：由
 * {@link org.eternalrelic.capability.carried.DamageWardEffect} 在攻击落下前出手，
 * 把这一击挡下、或化出金心分摊，并让环身碎裂。因此这里只负责提供名称与说明文字。</p>
 *
 * <p>碎裂的形态（{@code echo_ring_drained}）用的是同一个类：它同样没有行为，
 * 只是说明文字不同，而说明文字由各自的翻译键决定。</p>
 */
public class EchoRingItem extends Item {

    public EchoRingItem(Settings settings) {
        super(settings);
    }

    /**
     * 在物品提示框中补上一行效果说明（灰色小字）。
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
