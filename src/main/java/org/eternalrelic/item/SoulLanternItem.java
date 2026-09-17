package org.eternalrelic.item;

import java.util.List;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import org.eternalrelic.capability.carried.SoulLanternEffect;

/**
 * 引魂燃灯 —— 携带时收集击杀所得的魂火，按 G 键一次倾泻出去的遗物。
 *
 * <p>它本身只负责门面：把「攒了多少魂火、是否在冷却」写在提示框里——
 * 玩家不必打开箱子的界面就能知道这盏灯现在能不能放。名称与外观描述由语言文件承担。</p>
 *
 * <p>攒魂与释放由 {@link SoulLanternEffect} 负责；灯只需被携带（主背包或副手），
 * 不需要装入身体。</p>
 */
public class SoulLanternItem extends Item {

    public SoulLanternItem(Settings settings) {
        super(settings);
    }

    /**
     * 在物品提示框中补上魂火数与冷却状态。
     *
     * <p>效果说明不在这里——它已经写在遗物界面里了；提示框只留这两行**随时会变的**状态，
     * 好让玩家在箱子里扫一眼就知道这盏灯现在能不能放。</p>
     *
     * @param stack   正在查看的物品
     * @param world   玩家所在的世界；在部分界面里可能为 {@code null}
     * @param tooltip 待填充的提示框内容
     */
    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        int souls = SoulLanternEffect.soulsOf(stack);
        tooltip.add(Text.translatable("item.eternal_relic.soul_lantern.souls",
                        souls, SoulLanternEffect.MAX_SOULS)
                .formatted(souls > 0 ? Formatting.AQUA : Formatting.DARK_GRAY));

        if (world != null) {
            long remaining = SoulLanternEffect.cooldownRemainingTicks(stack, world.getTime());
            if (remaining > 0L) {
                tooltip.add(Text.translatable("item.eternal_relic.soul_lantern.cooling",
                                (remaining + 19L) / 20L)
                        .formatted(Formatting.DARK_RED));
            }
        }
    }
}
