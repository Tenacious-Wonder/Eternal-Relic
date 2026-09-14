package org.eternalrelic.client;

import java.util.List;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import org.eternalrelic.registry.ModRelics;
import org.eternalrelic.registry.VanillaItemGrades;
import org.eternalrelic.registry.VanillaMaterialRarities;
import org.eternalrelic.relic.EquipmentGrade;
import org.eternalrelic.relic.MaterialRarity;
import org.eternalrelic.relic.RelicDefinition;

/**
 * 在物品提示框里补上本模组的品阶信息。
 *
 * <p>材料显示「成色」，工具装备显示「品阶」；两者都只针对已收录的原版物品，
 * 其他模组的物品保持原样，本模组不去替它们定级。</p>
 *
 * <p>装备的品阶来自材料与锻造熟练度两个因素。玩家目前还没有熟练度，因此这里显示的是
 * 由材料决定的基准品阶；等熟练度系统加入后，把玩家的加成传给
 * {@link VanillaItemGrades#gradeOf(net.minecraft.item.Item, int)} 即可显示实际品阶。</p>
 */
@Environment(EnvType.CLIENT)
public final class MaterialTooltip {

    private MaterialTooltip() {
    }

    /**
     * 由客户端入口调用，挂上物品提示框的补充回调。
     */
    public static void register() {
        ItemTooltipCallback.EVENT.register(MaterialTooltip::appendRarity);
    }

    /**
     * 按物品类别追加成色或品阶说明。
     *
     * @param stack   正在查看的物品
     * @param context 提示框场景（普通查看或高级提示）
     * @param lines   提示框已有内容，就地追加
     */
    private static void appendRarity(ItemStack stack, TooltipContext context, List<Text> lines) {
        MaterialRarity rarity = VanillaMaterialRarities.rarityOf(stack.getItem());
        if (rarity != null) {
            lines.add(Text.translatable("rarity.eternal_relic.material",
                            colored(rarity.displayName(), rarity.color()))
                    .formatted(Formatting.DARK_GRAY));
        }

        EquipmentGrade grade = VanillaItemGrades.baseGradeOf(stack.getItem());
        if (grade != null) {
            lines.add(Text.translatable("rarity.eternal_relic.grade",
                            colored(grade.displayName(), grade.color()))
                    .formatted(Formatting.DARK_GRAY));
        }

        // 遗物的稀有度来自遗物表，沿用材料档位
        RelicDefinition relic = ModRelics.definitionOf(stack.getItem());
        if (relic != null) {
            lines.add(Text.translatable("rarity.eternal_relic.relic",
                            colored(relic.rarity().displayName(), relic.rarity().color()))
                    .formatted(Formatting.DARK_GRAY));
        }
    }

    /**
     * 把一段文字染成指定颜色。
     *
     * @param text  文字内容
     * @param color RGB 颜色
     * @return 带颜色的文本
     */
    private static Text colored(String text, int color) {
        return Text.literal(text).styled(style -> style.withColor(TextColor.fromRgb(color)));
    }
}
