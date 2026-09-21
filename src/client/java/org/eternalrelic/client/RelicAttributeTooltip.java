package org.eternalrelic.client;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;

import org.eternalrelic.relic.RelicAttribute;
import org.eternalrelic.relic.RelicEffectBonus;

/**
 * 把「遗物给这件装备加上的属性」画进物品提示框。
 *
 * <p><b>要画成什么样</b>：物品自己那一行原样不动，遗物那一份用金色的加号接在后面 ——
 * 原本 {@code +8 护甲值}、缝了一枚坚铁甲片再加 1 点，面板上就写 {@code +8+1 护甲值}。
 * 同一件装备上缝了两枚加护甲的遗物（铜甲片 +2 与坚铁甲片 +1）时，遗物那两份先自己合起来，
 * 写成 {@code +8+3 护甲值}：前半是这件装备自己的，后半全部来自遗物。</p>
 *
 * <p><b>只认附着份</b>：数值来自 {@link RelicEffectBonus}，它只看缝在这件东西上的遗物，
 * 不看玩家背包里带着什么——因此面板上写的份额，就是把这件东西穿在身上 / 拿在手里时实际生效的那一份。</p>
 *
 * <p><b>玩家怎么分清哪半是遗物的</b>：遗物那一份是<b>金色</b>的，与上面的附魔份额
 * （{@code RelicEnchantmentTooltip}）用的是同一种颜色，整套提示框里「金色 = 来自遗物」是一条统一规矩。</p>
 *
 * <p><b>找不到对应的属性行时另补一行</b>：护甲类遗物也能缝在盾牌上，而盾牌自己并没有护甲属性行，
 * 这时另起一行金色的 {@code +2 护甲值}，免得那份加成在面板上凭空消失。</p>
 *
 * <p><b>认不出那一行就什么都不做</b>：识别靠的是原版那行的文案结构（{@code attribute.modifier.*}）。
 * 万一某一行被别的模组改写过、认不出来，这里直接跳过——宁可少画一段金色，也不能把别人的行改坏。</p>
 */
@Environment(EnvType.CLIENT)
public final class RelicAttributeTooltip {

    /** 遗物那一份用的颜色：金色，与附魔份额一致。 */
    private static final Formatting RELIC_COLOR = Formatting.GOLD;

    /** 原版属性行的翻译键前缀（正加成 / 负加成 / 等于值三种都以此开头）。 */
    private static final String MODIFIER_KEY_PREFIX = "attribute.modifier.";

    private RelicAttributeTooltip() {
    }

    /**
     * 给提示框里的属性行补上遗物的份额。
     *
     * <p>调用时机是原版把整个提示框写完、交给本模组补充说明之前，因此此时属性行已经在表里了。</p>
     *
     * @param stack   提示框所属的物品
     * @param tooltip 已经写好的提示框内容，就地改写
     */
    public static void decorate(ItemStack stack, List<Text> tooltip) {
        Map<RelicAttribute, Double> bonuses = RelicEffectBonus.bonusOf(stack);
        if (bonuses.isEmpty()) {
            return;
        }

        Set<RelicAttribute> alreadyDrawn = EnumSet.noneOf(RelicAttribute.class);
        int lastModifierLine = -1;

        for (int i = 0; i < tooltip.size(); i++) {
            Text line = tooltip.get(i);

            if (!(line.getContent() instanceof TranslatableTextContent translatable)
                    || !translatable.getKey().startsWith(MODIFIER_KEY_PREFIX)) {
                continue;
            }

            lastModifierLine = i;

            RelicAttribute attribute = attributeOf(translatable);
            Double bonus = attribute == null ? null : bonuses.get(attribute);

            if (bonus == null || bonus <= 0.0D) {
                continue;
            }

            tooltip.set(i, line.copy().append(relicMark(bonus)));
            alreadyDrawn.add(attribute);
        }

        // 这件东西自己没有那条属性（铁甲本来就没有「盔甲韧性」行；盾牌连护甲行都没有），
        // 就补一行金色的。位置紧跟在最后一条属性行之后 —— 丢到提示框最下面会离属性区太远，
        // 玩家不容易把两件事联系起来。
        int insertAt = lastModifierLine < 0 ? tooltip.size() : lastModifierLine + 1;

        for (Map.Entry<RelicAttribute, Double> bonus : bonuses.entrySet()) {
            if (alreadyDrawn.contains(bonus.getKey()) || bonus.getValue() <= 0.0D) {
                continue;
            }

            tooltip.add(insertAt++, Text.translatable("attribute.modifier.plus.0",
                            format(bonus.getValue()),
                            Text.translatable(bonus.getKey().attribute().getTranslationKey()))
                    .formatted(RELIC_COLOR));
        }
    }

    /**
     * 从一行原版属性文本里认出它说的是哪个属性。
     *
     * <p>原版那行的参数里有一项是属性的显示名（本身就是一段可翻译文本），
     * 这里拿它的翻译键与遗物能影响的几种属性逐一比对。</p>
     *
     * @param line 原版写出的属性行
     * @return 对应的属性；认不出时为 {@code null}
     */
    private static RelicAttribute attributeOf(TranslatableTextContent line) {
        for (Object arg : line.getArgs()) {
            if (!(arg instanceof Text nameText)
                    || !(nameText.getContent() instanceof TranslatableTextContent name)) {
                continue;
            }

            for (RelicAttribute candidate : RelicAttribute.values()) {
                if (candidate.attribute().getTranslationKey().equals(name.getKey())) {
                    return candidate;
                }
            }
        }

        return null;
    }

    /**
     * 拼出表示「这一份来自遗物」的金色标记。
     *
     * @param value 遗物加上的点数
     * @return 金色的 {@code +数值}
     */
    private static MutableText relicMark(double value) {
        return Text.literal("+" + format(value)).formatted(RELIC_COLOR);
    }

    /**
     * 把一个点数写成面板上的样子：整数值不带小数点，小数最多留两位。
     *
     * <p>刻意不用 {@code DecimalFormat}：它按语言环境换小数点符号，而这里的数字要与紧挨着的
     * 原版数字看起来是同一种写法，所以固定用小数点。</p>
     *
     * @param value 点数
     * @return 写好的文字
     */
    private static String format(double value) {
        double rounded = Math.round(value * 100.0D) / 100.0D;

        if (rounded == Math.rint(rounded)) {
            return String.valueOf((long) rounded);
        }

        return String.valueOf(rounded);
    }
}
