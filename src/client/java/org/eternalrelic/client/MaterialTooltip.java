package org.eternalrelic.client;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Language;

import org.eternalrelic.registry.ModRelics;
import org.eternalrelic.registry.VanillaItemGrades;
import org.eternalrelic.registry.VanillaMaterialRarities;
import org.eternalrelic.relic.EquipmentGrade;
import org.eternalrelic.relic.MaterialRarity;
import org.eternalrelic.relic.RelicDefinition;

/**
 * 在物品提示框里补上本模组的品阶信息与外观描述。
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

    /** 外观描述每行最多几个字。 */
    private static final int FLAVOR_LINE_LENGTH = 8;

    /** 不该被挤到行首的中文标点。 */
    private static final String NO_LINE_START = "，。！？、；：）】》”’…";

    private MaterialTooltip() {
    }

    /**
     * 由客户端入口调用，挂上物品提示框的补充回调。
     */
    public static void register() {
        ItemTooltipCallback.EVENT.register(MaterialTooltip::appendRarity);
    }

    /**
     * 按物品类别追加成色或品阶说明，再补上外观描述。
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

        appendFlavor(lines, stack.getTranslationKey());

        if (relic != null) {
            // 效果说明一律搬到遗物界面里看，提示框只留一句指路，免得两处文字各写一遍
            lines.add(Text.translatable("item.eternal_relic.tooltip.hint")
                    .formatted(Formatting.DARK_GRAY));
        }
    }

    /**
     * 追加这件物品的外观描述。
     *
     * <p><b>提示框不会自己折行</b>，所以折行由这里做：文案里没写换行的，按「每行 8 个字」
     * 自动切；作者已经断好行的（英文就是如此）就照用——英文若也按字数硬切，会从单词中间断开。</p>
     *
     * <p>没有外观描述的物品直接跳过，所以这个方法是给所有物品调的，不必先判断是不是遗物。
     * 碎裂、耗尽这类形态各自挂自己的那段文字。</p>
     *
     * @param lines          提示框内容，就地追加
     * @param translationKey 物品的翻译键
     */
    private static void appendFlavor(List<Text> lines, String translationKey) {
        Language language = Language.getInstance();
        String key = translationKey + ".flavor";
        if (!language.hasTranslation(key)) {
            return;
        }

        for (String line : wrapFlavor(language.get(key))) {
            lines.add(Text.literal(line).formatted(Formatting.GRAY));
        }
    }

    /**
     * 把外观描述切成若干行。
     *
     * <p>含换行符时完全照作者的断法；否则每 {@link #FLAVOR_LINE_LENGTH} 个字一行。
     * 断行时顺带处理三种会让它难看的情况：</p>
     * <ul>
     *   <li><b>标点被挤到行首</b> → 让它跟着上一行走（「一行以逗号开头」很难看）；</li>
     *   <li><b>引号中间断开</b> → 引号没闭合就不换行，否则「“01”」会被拆成两行；</li>
     *   <li><b>末行只剩一两个字</b> → 并回上一行，免得单占一行像是断了。</li>
     * </ul>
     *
     * @param text 外观描述原文
     * @return 切好的各行
     */
    private static List<String> wrapFlavor(String text) {
        List<String> lines = new ArrayList<>();

        if (text.indexOf('\n') >= 0) {
            for (String part : text.split("\n")) {
                if (!part.isEmpty()) {
                    lines.add(part);
                }
            }

            return lines;
        }

        StringBuilder current = new StringBuilder();
        int openQuotes = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            current.append(c);

            if (c == '\u201C') {
                openQuotes++;
            } else if (c == '\u201D') {
                openQuotes = Math.max(0, openQuotes - 1);
            }

            if (current.length() < FLAVOR_LINE_LENGTH) {
                continue;
            }

            // 引号中间不换行，否则像「“01”」这样的内容会被拆成两行
            if (openQuotes > 0) {
                continue;
            }

            boolean nextIsPunctuation = i + 1 < text.length()
                    && NO_LINE_START.indexOf(text.charAt(i + 1)) >= 0;

            if (!nextIsPunctuation) {
                lines.add(current.toString());
                current.setLength(0);
            }
        }

        if (current.length() > 0) {
            lines.add(current.toString());
        }

        // 末行只剩一两个字时并回上一行——单占一行会像是断了，读起来也别扭
        if (lines.size() >= 2 && lines.get(lines.size() - 1).length() <= 2) {
            int last = lines.size() - 1;
            lines.set(last - 1, lines.get(last - 1) + lines.get(last));
            lines.remove(last);
        }

        return lines;
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
