package org.eternalrelic.relic;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.eternalrelic.registry.ModRelics;

/**
 * 「遗物给所附着的物品加了多少属性」这件事的账本 —— <b>只算缝在这件东西上的那些遗物</b>，
 * 玩家背包里携带的那一份不算。
 *
 * <p>它与 {@link RelicEnchantmentBonus 补魔账本}是一对：那个回答「这件东西多了哪条附魔」，
 * 这个回答「这件东西多了几点护甲 / 韧性 / 生命」。两者的口径完全一致——都只看附着关系，
 * 因此提示框上画出来的份额，与玩家把这件东西穿在身上时实际拿到的份额永远对得上。</p>
 *
 * <p><b>为什么只收固定值（{@link RelicBonusKind#FLAT}）</b>：提示框要写的是「原值 + 遗物值」
 * 这种并排写法，只有以点数为单位的加成谈得上并排；百分比是对总量做乘法，没有可以并排的「原值」，
 * 因此不在这里列出。</p>
 *
 * <p><b>点数怎么取</b>：这件装备上的每一枚遗物只算一份，取
 * {@link RelicEffect#valueFor(int) valueFor(1)} —— 对「每份加一点」（坚铁甲片）与
 * 「只加一次」（铜甲片）两种登记都成立。同一件装备上缝了两枚加同一属性的遗物时，这里按属性合并求和。</p>
 *
 * <p><b>一件遗物可以给好几种属性</b>（鳞甲内衬既给韧性又给护甲值），因此这里把
 * {@link RelicDefinition#effects()} 逐条走一遍，各记各的。</p>
 *
 * <p>算出的份额画在提示框上，由客户端那侧的 {@code RelicAttributeTooltip} 负责。</p>
 */
public final class RelicEffectBonus {

    private RelicEffectBonus() {
    }

    /**
     * 收出这件物品身上「由附着的遗物带来」的属性加成。
     *
     * @param stack 待查看的物品
     * @return 属性 → 加成数值；没有附着遗物、或附着的不给属性时为空表
     */
    public static Map<RelicAttribute, Double> bonusOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return Map.of();
        }

        List<Item> attached = RelicAttachment.attachedTo(stack);
        if (attached.isEmpty()) {
            return Map.of();
        }

        Map<RelicAttribute, Double> bonuses = new EnumMap<>(RelicAttribute.class);

        for (Item relic : attached) {
            RelicDefinition definition = ModRelics.definitionOf(relic);

            if (definition == null || !definition.hasCarriedEffect()) {
                continue;
            }

            for (RelicEffect effect : definition.effects()) {
                if (effect.kind() != RelicBonusKind.FLAT) {
                    continue;
                }

                bonuses.merge(effect.attribute(), effect.valueFor(1), Double::sum);
            }
        }

        return bonuses;
    }
}
