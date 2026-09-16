package org.eternalrelic.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

import org.eternalrelic.relic.DamageWard;
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

    /**
     * 回响之环 —— 每次挨打时替玩家出手一次：不超过 20 点的攻击整击挡下；更重的攻击改为当场化出金心
     * （伤害越高给得越多，20 点给 5 颗、60 点及以上给 10 颗）；出手之后碎裂 2 分钟（2400 刻）。
     *
     * <p>它没有持续的属性加成，价值全在「那一下」：由
     * {@link org.eternalrelic.capability.carried.DamageWardEffect} 在攻击落下前出手。
     * 只有「被谁打的」攻击才算数，药水与状态效果造成的伤害不会触发。</p>
     *
     * <p>固有稀有度为至宝：这是能在关键时刻改写一次交手结果的东西，本身就该是顶级成色。</p>
     */
    public static final RelicDefinition ECHO_RING = define(
            ModItems.ECHO_RING,
            MaterialRarity.SUPREME,
            null,
            new DamageWard(ModItems.ECHO_RING_DRAINED, 20.0F, 5, 10, 60.0F, 2400));

    /**
     * 引魂之灯 —— 携带时收集击杀所得的魂火，按 G 键一次倾泻出去。
     *
     * <p>它没有持续的属性加成，价值全在「攒」与「放」之间：由
     * {@link org.eternalrelic.capability.carried.SoulLanternEffect} 记录击杀、
     * 结算瞬间冲击与残留的魂火。灯不必装入身体，放在背包或拿在手上即可。</p>
     *
     * <p>固有稀有度为珍品：这是一件能主动改写战局的进攻型遗物。</p>
     */
    public static final RelicDefinition SOUL_LANTERN = define(
            ModItems.SOUL_LANTERN,
            MaterialRarity.TREASURE,
            null);

    // ==================== 品阶样本（测试用） ====================

    /**
     * 七件品阶样本 —— 每件只登记一个材料档位，既没有携带效果也没有守护效果。
     *
     * <p>用途是检查遗物界面：界面按 {@code rarity} 选用对应的面板样式，
     * 把它们并排放在身上，就能一次看全七个档位的观感是否协调。
     * 正式发布前应连同物品注册、贴图与语言条目一并移除。</p>
     */
    public static final RelicDefinition SAMPLE_DEBRIS = define(
            ModItems.RELIC_SAMPLE_DEBRIS, MaterialRarity.DEBRIS, null);

    /** 品阶样本·粗石。 */
    public static final RelicDefinition SAMPLE_ROUGH = define(
            ModItems.RELIC_SAMPLE_ROUGH, MaterialRarity.ROUGH_STONE, null);

    /** 品阶样本·成材。 */
    public static final RelicDefinition SAMPLE_LUMBER = define(
            ModItems.RELIC_SAMPLE_LUMBER, MaterialRarity.LUMBER, null);

    /** 品阶样本·精萃。 */
    public static final RelicDefinition SAMPLE_ESSENCE = define(
            ModItems.RELIC_SAMPLE_ESSENCE, MaterialRarity.ESSENCE, null);

    /** 品阶样本·珍品。 */
    public static final RelicDefinition SAMPLE_TREASURE = define(
            ModItems.RELIC_SAMPLE_TREASURE, MaterialRarity.TREASURE, null);

    /** 品阶样本·至宝。 */
    public static final RelicDefinition SAMPLE_SUPREME = define(
            ModItems.RELIC_SAMPLE_SUPREME, MaterialRarity.SUPREME, null);

    /** 品阶样本·源质。 */
    public static final RelicDefinition SAMPLE_SOURCE = define(
            ModItems.RELIC_SAMPLE_SOURCE, MaterialRarity.SOURCE, null);

    /**
     * 守夜之瞳·左眼 —— 装入左眼后，在低光环境下看清周围。
     *
     * <p>它不靠「放在背包里」生效，而是由玩家右键装入，因此这里没有携带属性加成；
     * 装入过程与它索取的代价由
     * {@link org.eternalrelic.capability.worn.WornRelicEffect} 负责。</p>
     */
    public static final RelicDefinition NIGHTWATCH_EYE_LEFT = define(
            ModItems.NIGHTWATCH_EYE_LEFT,
            MaterialRarity.ESSENCE,
            null);

    /**
     * 守夜之瞳·右眼 —— 装入右眼后，在低光环境下照见附近的活物。
     *
     * <p>与左眼一样属于装入型遗物，没有携带属性加成。</p>
     */
    public static final RelicDefinition NIGHTWATCH_EYE_RIGHT = define(
            ModItems.NIGHTWATCH_EYE_RIGHT,
            MaterialRarity.ESSENCE,
            null);

    private ModRelics() {
    }

    /**
     * 由 {@link ModItems#register()} 调用，触发本类静态内容初始化（也就是把上面的遗物登记进名单）。
     */
    static void register() {
    }

    /**
     * 登记一件只有携带属性加成的遗物。
     *
     * @param item   遗物对应的物品
     * @param rarity 遗物固有稀有度（沿用材料档位）
     * @param effect 携带时生效的属性加成，没有则为 {@code null}
     * @return 登记好的遗物定义
     */
    private static RelicDefinition define(Item item, MaterialRarity rarity, RelicEffect effect) {
        return define(item, rarity, effect, null);
    }

    /**
     * 登记一件遗物。
     *
     * @param item   遗物对应的物品
     * @param rarity 遗物固有稀有度（沿用材料档位）
     * @param effect 携带时生效的属性加成，没有则为 {@code null}
     * @param ward   受到攻击时的守护效果，没有则为 {@code null}
     * @return 登记好的遗物定义
     */
    private static RelicDefinition define(Item item, MaterialRarity rarity, RelicEffect effect, DamageWard ward) {
        RelicDefinition definition = new RelicDefinition(item, Registries.ITEM.getId(item), rarity, effect, ward);
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
