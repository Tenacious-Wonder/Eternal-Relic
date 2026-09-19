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
     * <p>它是目前唯一带「入手表现」的遗物：第一次放进背包时会响一记心跳，
     * 并有光点自胸口涌出后收敛回来。</p>
     *
     * <p>固有稀有度为珍品：这是它自身决定的成色，与谁制作无关。</p>
     */
    public static final RelicDefinition AOTA_BRANCH = defineWithArrival(
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
     * <p>固有稀有度为珍品：这是能在关键时刻改写一次交手结果的东西。</p>
     */
    public static final RelicDefinition ECHO_RING = define(
            ModItems.ECHO_RING,
            MaterialRarity.TREASURE,
            null,
            new DamageWard(ModItems.ECHO_RING_DRAINED, 20.0F, 5, 10, 60.0F, 2400));

    /**
     * 回响之环（碎裂）—— 守护出手之后变成的形态，静置两分钟自行复原。
     *
     * <p>它自身没有任何效果，登记进遗物表只为一件事：<b>效果说明只有遗物界面会显示，
     * 而界面要求物品在遗物表里</b>。只注册物品、不进遗物表的话，语言文件里那段
     * 「已经碎裂，暂时无法再替你挡下攻击；静置一会儿便会自行复原」玩家永远看不到，
     * 提示框里也会少一行稀有度与「按左 Shift」的指路。</p>
     *
     * <p>稀有度沿用本体（珍品）：碎裂只是两分钟的状态，成色不该随状态变化。</p>
     */
    public static final RelicDefinition ECHO_RING_DRAINED = define(
            ModItems.ECHO_RING_DRAINED,
            MaterialRarity.TREASURE,
            null);

    /**
     * 引魂燃灯 —— 携带时收集击杀所得的魂火，按 G 键一次倾泻出去。
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

    /**
     * 斑驳的铜甲片 —— 放在背包里或附着在防具上时加点护甲的遗物。
     *
     * <p>它加的是固定点数而不是百分比：护甲这类以点数为单位的属性，基数可能为 0，
     * 乘任何百分比都还是 0，只有直接加数值才落得下去。</p>
     *
     * <p><b>单片只给半点，四个部位合起来才够看</b>：附着时每个部位各算一份，
     * 最多四份、合计 2 点。这个数值是按「四件都附上」来定的——若按单片 2 点算，
     * 四个部位就是 8 点护甲，比一整身铁甲还厚，明显过头了。</p>
     *
     * <p>固有稀有度为粗石：一块有年头的旧甲片，顶用，但称不上讲究。</p>
     */
    public static final RelicDefinition MOTTLED_COPPER_PLATE = define(
            ModItems.MOTTLED_COPPER_PLATE,
            MaterialRarity.ROUGH_STONE,
            RelicEffect.flatPerCopy(RelicAttribute.ARMOR, 0.5D, 4));

    /**
     * 勇气纹章 —— 放在背包里时，每隔一分钟替玩家攒下两颗金心，最多攒到四颗。
     *
     * <p>它没有持续的属性加成，也不在挨打时出手：价值全在「按时间攒」上，由
     * {@link org.eternalrelic.capability.carried.CourageEmblemEffect} 计时并补上金心。
     * 金心就是吸收，一旦给出便与纹章无关，把纹章收起来也不会收回。</p>
     *
     * <p>固有稀有度为成材：一枚做工规矩的蜡制纹章，用处实在，但谈不上稀罕。</p>
     */
    public static final RelicDefinition COURAGE_EMBLEM = define(
            ModItems.COURAGE_EMBLEM,
            MaterialRarity.LUMBER,
            null);

    /**
     * 附魔兔脚 —— 带着它挨打时，立刻换来一段速度。
     *
     * <p>它既不提供持续的属性加成，也不把这一击挡下来：价值全在「挨这一下换来跑得快」上，
     * 由 {@link org.eternalrelic.capability.carried.EnchantedRabbitFootEffect} 判定并施加，
     * 出手之后有 2 分钟冷却。</p>
     *
     * <p>固有稀有度为粗石：来历不明的小玩意，妙处全在那一层附魔光泽上。</p>
     */
    public static final RelicDefinition ENCHANTED_RABBIT_FOOT = define(
            ModItems.ENCHANTED_RABBIT_FOOT,
            MaterialRarity.ROUGH_STONE,
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
     * 守夜之瞳·左眼（耗尽）—— 取下左眼后落到玩家脚下的形态，与附魔之瓶合成可恢复原样。
     *
     * <p>登记的理由与 {@link #ECHO_RING_DRAINED} 完全相同：物品与语言文件都已备好，
     * 唯有进遗物表这一步漏了，于是「能量已耗尽，无法装入」那段说明永远显示不出来。</p>
     *
     * <p>固有稀有度沿用左眼本体的精萃。</p>
     */
    public static final RelicDefinition NIGHTWATCH_EYE_LEFT_DRAINED = define(
            ModItems.NIGHTWATCH_EYE_LEFT_DRAINED,
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

    /**
     * 守夜之瞳·右眼（耗尽）—— 与左眼的耗尽形态同理，登记进遗物表只为让说明文字显示出来。
     *
     * <p>固有稀有度沿用右眼本体的精萃。</p>
     */
    public static final RelicDefinition NIGHTWATCH_EYE_RIGHT_DRAINED = define(
            ModItems.NIGHTWATCH_EYE_RIGHT_DRAINED,
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
     * 登记一件携带生效时会先「亮相」的遗物。
     *
     * <p>「入手表现」指的是玩家刚开始携带它时的那一记心跳声，以及涌出后收敛回来的光点。
     * 只有确实值得亮相的遗物才走这条登记路径；其余遗物安静地生效，不响也不冒粒子。</p>
     *
     * @param item   遗物对应的物品
     * @param rarity 遗物固有稀有度（沿用材料档位）
     * @param effect 携带时生效的属性加成
     * @return 登记好的遗物定义
     */
    private static RelicDefinition defineWithArrival(Item item, MaterialRarity rarity, RelicEffect effect) {
        return define(item, rarity, effect, null, true);
    }

    /**
     * 登记一件不做入手表现的遗物。
     *
     * @param item   遗物对应的物品
     * @param rarity 遗物固有稀有度（沿用材料档位）
     * @param effect 携带时生效的属性加成，没有则为 {@code null}
     * @param ward   受到攻击时的守护效果，没有则为 {@code null}
     * @return 登记好的遗物定义
     */
    private static RelicDefinition define(Item item, MaterialRarity rarity, RelicEffect effect, DamageWard ward) {
        return define(item, rarity, effect, ward, false);
    }

    /**
     * 登记一件遗物。
     *
     * @param item          遗物对应的物品
     * @param rarity        遗物固有稀有度（沿用材料档位）
     * @param effect        携带时生效的属性加成，没有则为 {@code null}
     * @param ward          受到攻击时的守护效果，没有则为 {@code null}
     * @param arrivalEffect 刚开始携带时是否播放一记「入手」表现
     * @return 登记好的遗物定义
     */
    private static RelicDefinition define(Item item, MaterialRarity rarity, RelicEffect effect, DamageWard ward,
            boolean arrivalEffect) {
        RelicDefinition definition = new RelicDefinition(item, Registries.ITEM.getId(item), rarity, effect, ward,
                arrivalEffect);
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
