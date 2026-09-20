package org.eternalrelic.client;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import org.eternalrelic.relic.RelicEnchantmentBonus;

/**
 * 把「遗物补上的附魔」画进物品提示框。
 *
 * <p><b>要画成什么样</b>：物品自己的等级原样不动，遗物那一份用金色的加号接在后面——
 * 原本 {@code 保护 II}、遗物再给 2 级，面板上就写 {@code 保护 II+II}（实际生效 4 级）。
 * 这样玩家一眼能分清哪几级是自己练的、哪几级是纹章给的。</p>
 *
 * <p><b>物品本来没有这条附魔时</b>（例如纹章给的经验修补），游戏那张附魔列表里根本没有这一行，
 * 所以由本类补一行，写成 {@code 经验修补+}：附魔名按它自己的等级显示，金色加号表明这一行来自遗物。
 * 经验修补在原版只有一个等级，名称后面本来就不带罗马数字，因此看起来就是「经验修补+」。</p>
 *
 * <p><b>对不上就不动那一行</b>：游戏把附魔逐行写进提示框时，顺序与物品自己的附魔表一致，
 * 但万一有哪一行被别的模组改写过（或数据里有一条认不出的附魔），行与行就会错位。
 * 这里在改写之前先核对「这一行的文字是不是这条附魔本该有的文字」，对不上就跳过——
 * 宁可少画一段金色，也不能把别人的行改坏。</p>
 */
public final class RelicEnchantmentTooltip {

    /** 遗物那一份用的颜色：金色，与原版附魔行的颜色区分开。 */
    private static final Formatting RELIC_COLOR = Formatting.GOLD;

    private RelicEnchantmentTooltip() {
    }

    /**
     * 给提示框里刚写好的那几行附魔补上遗物的份额。
     *
     * @param stack              提示框所属的物品
     * @param tooltip            已经写好的提示框内容，就地改写
     * @param firstEnchantLine   附魔段第一行在 {@code tooltip} 里的下标
     */
    public static void decorate(ItemStack stack, List<Text> tooltip, int firstEnchantLine) {
        Map<Enchantment, Integer> bonuses = RelicEnchantmentBonus.bonusOf(stack);
        if (bonuses.isEmpty()) {
            return;
        }

        Set<Enchantment> alreadyShown = new HashSet<>();
        NbtList actual = stack.getEnchantments();
        int line = firstEnchantLine;

        for (int i = 0; i < actual.size() && line < tooltip.size(); i++) {
            NbtCompound entry = actual.getCompound(i);
            Enchantment enchantment = enchantmentOf(entry);

            if (enchantment == null) {
                continue;
            }

            // ⚠️ 先记下「这条附魔物品本来就有」，再去核对文字。
            // 核对的目的是「怕改错行」，但它不能兼任「判断这条附魔是不是新补的」——
            // 一旦核对失败就跳过记录，收尾那一步会把它当成「遗物新补的附魔」再画一行，
            // 于是同一条附魔在提示框里出现两次。两种失败里，少画一段金色是可接受的，重复一行不是。
            alreadyShown.add(enchantment);

            Text original = tooltip.get(line);

            if (!original.equals(enchantment.getName(EnchantmentHelper.getLevelFromNbt(entry)))) {
                line++;
                continue;
            }

            Integer bonus = bonuses.get(enchantment);

            if (bonus != null && bonus > 0) {
                tooltip.set(line, original.copy().append(relicMark(bonus)));
            }

            line++;
        }

        for (Map.Entry<Enchantment, Integer> bonus : bonuses.entrySet()) {
            if (alreadyShown.contains(bonus.getKey())) {
                continue;
            }

            tooltip.add(bonus.getKey().getName(bonus.getValue()).copy().append(relicMark(bonus.getValue())));
        }
    }

    /**
     * 拼出表示「这一份来自遗物」的金色标记。
     *
     * @param level 遗物补上的等级
     * @return 金色的 {@code +}；等级大于 1 时后面跟上与原版一致的罗马数字
     */
    private static MutableText relicMark(int level) {
        MutableText mark = Text.literal("+");

        if (level > 1) {
            mark.append(Text.translatable("enchantment.level." + level));
        }

        return mark.formatted(RELIC_COLOR);
    }

    /**
     * 从一行附魔数据里认出是哪条附魔。
     *
     * @param entry 附魔表里的一项
     * @return 对应的附魔；认不出（例如数据来自已卸载的模组）时为 {@code null}
     */
    private static Enchantment enchantmentOf(NbtCompound entry) {
        return Registries.ENCHANTMENT.get(EnchantmentHelper.getIdFromNbt(entry));
    }
}
