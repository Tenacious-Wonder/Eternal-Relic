package org.eternalrelic.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

import org.eternalrelic.relic.RelicAttribute;
import org.eternalrelic.relic.MaterialRarity;
import org.eternalrelic.relic.RelicDefinition;
import org.eternalrelic.relic.RelicEffect;

/**
 * 本模组的遗物注册表 —— 所有遗物都在这里登记，也就是「遗物表」。
 *
 * <p>新增一件遗物需要三处配合：</p>
 * <ol>
 *   <li>{@link ModItems} 里注册物品（决定贴图与堆叠上限）；</li>
 *   <li>本类里补一条 {@code public static final} 遗物声明（写明效果属性与数值）；</li>
 *   <li>语言文件里补上显示名与说明文字。</li>
 * </ol>
 *
 * <p>需要让遗物拥有属性加成之外的行为（主动技能、事件触发等）时，往能力插槽里接，
 * 而不是把行为塞进这张表。</p>
 */
public final class ModRelics {

    /** 全部已登记的遗物，按登记顺序排列。 */
    private static final Map<Item, RelicDefinition> BY_ITEM = new LinkedHashMap<>();

    /**
     * 奥塔的枝叶 —— 携带时提升生命上限，背包中每多一件再额外提升。
     *
     * <p>固有稀有度为珍品：这是它自身决定的成色，与谁制作无关。</p>
     */
    public static final RelicDefinition AOTA_BRANCH = define(
            ModItems.AOTA_BRANCH,
            MaterialRarity.TREASURE,
            new RelicEffect(RelicAttribute.MAX_HEALTH, 0.12D, 0.02D, 20));

    private ModRelics() {
    }

    /**
     * 由 {@link ModItems#register()} 调用，触发本类静态内容初始化（也就是把上面的遗物登记进名单）。
     */
    static void register() {
    }

    /**
     * 登记一件遗物。
     *
     * @param item   遗物对应的物品
     * @param rarity 遗物固有稀有度（沿用材料档位）
     * @param effect 携带时生效的属性加成，没有则为 {@code null}
     * @return 登记好的遗物定义
     */
    private static RelicDefinition define(Item item, MaterialRarity rarity, RelicEffect effect) {
        RelicDefinition definition = new RelicDefinition(item, Registries.ITEM.getId(item), rarity, effect);
        BY_ITEM.put(item, definition);
        return definition;
    }

    /**
     * @param item 待查询的物品
     * @return 该物品对应的遗物定义；不是遗物时返回 {@code null}
     */
    public static RelicDefinition definitionOf(Item item) {
        return BY_ITEM.get(item);
    }

    /**
     * @return 全部已登记的遗物，按登记顺序
     */
    public static Collection<RelicDefinition> all() {
        return Collections.unmodifiableCollection(BY_ITEM.values());
    }
}
