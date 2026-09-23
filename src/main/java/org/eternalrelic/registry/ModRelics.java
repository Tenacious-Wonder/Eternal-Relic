package org.eternalrelic.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
 *
 * <p><b>属性加成可以登记一条，也可以登记多条。</b>只给一种属性的（皮革内衬只给盔甲韧性）
 * 写一条，两种同时给的（鳞甲内衬既给韧性又给护甲值）把两条并排写上即可——
 * 每条各自算份数，互不干扰。给的是哪种数值、按什么方式加，见
 * {@link org.eternalrelic.relic.RelicEffect}。</p>
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
    public static final RelicDefinition ECHO_RING = defineWard(
            ModItems.ECHO_RING,
            MaterialRarity.TREASURE,
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
            MaterialRarity.TREASURE);

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
            MaterialRarity.TREASURE);

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
            MaterialRarity.LUMBER);

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
            MaterialRarity.ROUGH_STONE);

    /**
     * 蜂蜡吊坠 —— 带在身上时，被蜜蜂蜇伤不会中毒。
     *
     * <p>它既不给属性、也不在挨打时把这一击挡下来，价值全在「那一口毒蜇不进来」上：
     * <b>伤害照常挨</b>，只有蜜蜂顺手挂上的那条中毒被拿掉，由
     * {@link org.eternalrelic.capability.carried.BeeswaxPendantEffect} 判定
     * （拦下的时机见 {@link org.eternalrelic.mixin.LivingEntityMixin}）。</p>
     *
     * <p><b>只认蜜蜂</b>：毒箭、毒土豆、洞穴蜘蛛与药水给的中毒照旧生效——它挡的是
     * 「被蜜蜂蜇了一口」这件事，而不是「一切中毒」。</p>
     *
     * <p>固有稀有度为碎屑：一小块养蜂人手里剩下来的蜡，稀罕是谈不上的，
     * 好用全好在它刚好挡得住那一口。</p>
     */
    public static final RelicDefinition BEESWAX_PENDANT = define(
            ModItems.BEESWAX_PENDANT,
            MaterialRarity.DEBRIS);

    /**
     * 可怕狼牙吊坠 —— 带在身上时，十格内的野狼会被狼王的气息镇住而坐下；
     * 用骨头驯服狼的成功率由三分之一提高到六分之五。
     *
     * <p><b>它是第一件「按身边是谁」分强弱的遗物</b>：两条效果都只对狼生效，别的生物一概照原样。
     * 它没有任何属性加成，因此这里只登记身份与成色——具体判定分别由
     * {@link org.eternalrelic.capability.carried.WolfAweEffect}（慑服野狼）与
     * {@link org.eternalrelic.capability.carried.WolfTamingEffect}（驯服）负责。</p>
     *
     * <p>固有稀有度为粗石：一颗来路凶险的獠牙，顶用，但终究是件小东西。</p>
     */
    public static final RelicDefinition DREADFUL_WOLF_FANG_PENDANT = define(
            ModItems.DREADFUL_WOLF_FANG_PENDANT,
            MaterialRarity.ROUGH_STONE);

    /**
     * 永恒纹章 —— 钉在一件物品上，使那件物品不会被火烧、岩浆、爆炸、仙人掌与虚空毁掉，
     * 并视同带有「经验修补」。
     *
     * <p>它没有持续的属性加成，也不在挨打时出手：价值全在「钉住之后那件东西不会没」上。
     * 两条效果分别由 {@link org.eternalrelic.relic.ItemPreservation}（保全）与
     * {@link org.eternalrelic.relic.RelicEnchantmentBonus}（补魔）负责。</p>
     *
     * <p>固有稀有度为至宝：能让一件东西彻底免于损毁，这份量值得放在最上面两档。</p>
     */
    public static final RelicDefinition ETERNAL_EMBLEM = define(
            ModItems.ETERNAL_EMBLEM,
            MaterialRarity.SUPREME);

    /**
     * 川流纹章 —— 钉在头盔上视同带有「水下呼吸」，钉在靴子上视同带有「深海探索者」。
     *
     * <p>它没有持续的属性加成，也不在挨打时出手：价值全在「把那一件护具补成水下专用的」上。
     * 给哪条附魔取决于附着物是头盔还是靴子，两条都由
     * {@link org.eternalrelic.relic.RelicEnchantmentBonus} 负责。</p>
     *
     * <p>固有稀有度为成材：效果实用，但只在水下有用。</p>
     */
    public static final RelicDefinition STREAM_EMBLEM = define(
            ModItems.STREAM_EMBLEM,
            MaterialRarity.LUMBER);

    /**
     * 坚铁甲片 —— 缝在防具上，为穿着它的人加一点护甲。
     *
     * <p><b>放在背包里完全没有用</b>：它是第一件「只认附着份」的遗物，这份护甲加成的来源被限定为
     * 「正穿着的那件防具上缝了它」（见 {@link RelicDefinition#isAttachmentOnly()}）。
     * 这里照常登记护甲加成，由 {@link org.eternalrelic.capability.carried.CarriedRelicEffect}
     * 决定从哪一份计入。</p>
     *
     * <p><b>每缝一件各算一份</b>，四个部位都缝满合计 +4 点（两个护甲图标）——与斑驳的铜甲片
     * 同一套「各算一份」的规矩，只是单片给得更多、且必须先缝上去才管用。</p>
     *
     * <p>固有稀有度为成材：一块处理过的铁片，实在、耐用，但称不上稀罕。</p>
     */
    public static final RelicDefinition HARDENED_IRON_PLATE = defineAttachmentOnly(
            ModItems.HARDENED_IRON_PLATE,
            MaterialRarity.LUMBER,
            RelicEffect.flatPerCopy(RelicAttribute.ARMOR, 1.0D, 4));

    /**
     * 皮革内衬 —— 缝在防具上，为穿着它的人加一点盔甲韧性。
     *
     * <p>与坚铁甲片同一类：<b>放在背包里完全没有用</b>，这份加成的来源被限定为
     * 「正穿着的那件防具上缝了它」（见 {@link RelicDefinition#isAttachmentOnly()}）。</p>
     *
     * <p><b>它加的是盔甲韧性而不是护甲值</b>：这项属性在护甲条与提示框上都看不见，
     * 只在挨重击时保住减伤，因此是一件「看不出来、但确实在起作用」的遗物。
     * 每缝一件各算一份，四个部位都缝满合计 +2 点（相当于一件钻石甲自带的韧性）。</p>
     *
     * <p>固有稀有度为粗石：一块厚实的软皮，顶用，但称不上讲究。</p>
     */
    public static final RelicDefinition LEATHER_LINING = defineAttachmentOnly(
            ModItems.LEATHER_LINING,
            MaterialRarity.ROUGH_STONE,
            RelicEffect.flatPerCopy(RelicAttribute.ARMOR_TOUGHNESS, 0.5D, 4));

    /**
     * 鳞甲内衬 —— 缝在防具上，同时给出盔甲韧性与护甲值，偏重韧性那一边。
     *
     * <p>与坚铁甲片、皮革内衬同一类：<b>放在背包里完全没有用</b>，必须缝在正穿着的防具上。</p>
     *
     * <p><b>它是第一件同时给两种属性的遗物</b>：盔甲韧性 +1.5、护甲值 +0.25。
     * 两个数字各算各的份数——每缝一件各算一份，四个部位都缝满合计
     * <b>+6 韧性、+1 护甲</b>。分量压在韧性上，是用来扛重击的衬里；护甲那 0.25
     * 一半是为了「穿上之后护甲条也会动一下」的观感。</p>
     *
     * <p>它由 {@link ModItems#ARMADILLO_SCUTE 犰狳鳞甲} 缝在成品皮革内衬上做成。</p>
     *
     * <p>固有稀有度为成材：一层缝得整整齐齐的硬鳞，讲究，也耐用。</p>
     */
    public static final RelicDefinition SCUTE_LINING = defineAttachmentOnly(
            ModItems.SCUTE_LINING,
            MaterialRarity.LUMBER,
            RelicEffect.flatPerCopy(RelicAttribute.ARMOR_TOUGHNESS, 1.5D, 4),
            RelicEffect.flatPerCopy(RelicAttribute.ARMOR, 0.25D, 4));

    /**
     * 龟壳内衬 —— 缝在防具上，同时给出盔甲韧性与护甲值，比鳞甲内衬更偏护甲那一边。
     *
     * <p>与鳞甲内衬同一路数：<b>放在背包里完全没有用</b>，必须缝在正穿着的防具上。</p>
     *
     * <p>它也是两条属性一起给：盔甲韧性 +1.0、护甲值 +0.5，每缝一件各算一份，
     * 四个部位都缝满合计 <b>+4 韧性、+2 护甲</b>。与鳞甲内衬的分工是——
     * 这一件把分量更多放在护甲值上（一块厚重龟壳挡的是每一击），
     * 鳞甲内衬则偏向扛重击的韧性。</p>
     *
     * <p>它由三个海龟壳缝在成品皮革内衬上做成。</p>
     *
     * <p>固有稀有度为成材：一整块绿油油的硬壳，结实、有韧性。</p>
     */
    public static final RelicDefinition TURTLE_SHELL_LINING = defineAttachmentOnly(
            ModItems.TURTLE_SHELL_LINING,
            MaterialRarity.LUMBER,
            RelicEffect.flatPerCopy(RelicAttribute.ARMOR_TOUGHNESS, 1.0D, 4),
            RelicEffect.flatPerCopy(RelicAttribute.ARMOR, 0.5D, 4));

    /**
     * 皮革肩甲（左）—— 缝在胸甲上，护住玩家自身的左肩。
     *
     * <p>与坚铁甲片、皮革内衬同一类：<b>放在背包里完全没有用</b>，必须缝在正穿着的那件胸甲上，
     * 而且只认胸甲。这里登记的只是那 0.5 点盔甲韧性；「打中左肩时那一击少掉 2 点伤害」
     * 是另一件事，登记在 {@link ShoulderGuards 肩甲表} 里——一个长期挂在身上，
     * 一个只在挨打的那一刻算一次，结算时机不同，因此分成两张表。</p>
     *
     * <p>固有稀有度为粗石：一块厚实的皮革护片，与皮革内衬同一档。</p>
     */
    public static final RelicDefinition LEATHER_SHOULDER_GUARD_LEFT = defineAttachmentOnly(
            ModItems.LEATHER_SHOULDER_GUARD_LEFT,
            MaterialRarity.ROUGH_STONE,
            RelicEffect.flat(RelicAttribute.ARMOR_TOUGHNESS, 0.5D));

    /**
     * 皮革肩甲（右）—— 与左肩那只成对，护住玩家自身的右肩。
     *
     * <p>登记内容与左肩那只完全对称：同样 0.5 点盔甲韧性、同样只认胸甲上的附着份，
     * 护肩那一侧登记在 {@link ShoulderGuards}。</p>
     */
    public static final RelicDefinition LEATHER_SHOULDER_GUARD_RIGHT = defineAttachmentOnly(
            ModItems.LEATHER_SHOULDER_GUARD_RIGHT,
            MaterialRarity.ROUGH_STONE,
            RelicEffect.flat(RelicAttribute.ARMOR_TOUGHNESS, 0.5D));

    /**
     * 一套皮革肩甲 —— 左右两只合成而来的整体，两侧肩膀都护。
     *
     * <p><b>韧性给到 1.0，是左右两只相加的结果</b>：它由两片皮革做成，与分开缝两只拿到的总量
     * 一致，因此玩家把两只合成一套并不吃亏——合成换到的是「胸甲上少占一个附着格」
     * 与「两侧都护」，而不是靠减数值来平衡。</p>
     *
     * <p>护肩那一侧同样是两侧都护，减掉的点数与单只一样（2 点），登记在
     * {@link ShoulderGuards}。固有稀有度为粗石。</p>
     */
    public static final RelicDefinition LEATHER_SHOULDER_GUARD_PAIR = defineAttachmentOnly(
            ModItems.LEATHER_SHOULDER_GUARD_PAIR,
            MaterialRarity.ROUGH_STONE,
            RelicEffect.flat(RelicAttribute.ARMOR_TOUGHNESS, 1.0D));

    /**
     * 鳞片肩甲（左）—— 皮革肩甲缝上一层犰狳鳞甲之后的进阶形态，护住玩家自身的左肩。
     *
     * <p>与皮革肩甲同一路登记，只是数值高一档：<b>盔甲韧性 +1.0</b>（皮革那只 +0.5），
     * 护肩减伤则是 2.5 点（皮革那只 2 点），登记在 {@link ShoulderGuards 肩甲表}。
     * 同样是「只认附着份」、只缝胸甲。</p>
     *
     * <p>它由 {@link ModItems#LEATHER_SHOULDER_GUARD_LEFT 皮革肩甲（左）} 与
     * {@link ModItems#ARMADILLO_SCUTE 犰狳鳞甲} 缝制而成，与鳞甲内衬的来历是同一种做法。</p>
     *
     * <p>固有稀有度为成材：一层缝得整整齐齐的硬鳞，与鳞甲内衬同一档。</p>
     */
    public static final RelicDefinition SCUTE_SHOULDER_GUARD_LEFT = defineAttachmentOnly(
            ModItems.SCUTE_SHOULDER_GUARD_LEFT,
            MaterialRarity.LUMBER,
            RelicEffect.flat(RelicAttribute.ARMOR_TOUGHNESS, 1.0D));

    /**
     * 鳞片肩甲（右）—— 与左肩那只成对，护住玩家自身的右肩。
     *
     * <p>登记内容与左肩那只完全对称。</p>
     */
    public static final RelicDefinition SCUTE_SHOULDER_GUARD_RIGHT = defineAttachmentOnly(
            ModItems.SCUTE_SHOULDER_GUARD_RIGHT,
            MaterialRarity.LUMBER,
            RelicEffect.flat(RelicAttribute.ARMOR_TOUGHNESS, 1.0D));

    /**
     * 一套鳞片肩甲 —— 左右两只合成而来的整体，两侧肩膀都护。
     *
     * <p>韧性给到 2.0，同样是左右两只相加的结果（皮革那一套是 1.0）。护肩减伤与单只一样是
     * 2.5 点，但两侧都护，登记在 {@link ShoulderGuards}。固有稀有度为成材。</p>
     */
    public static final RelicDefinition SCUTE_SHOULDER_GUARD_PAIR = defineAttachmentOnly(
            ModItems.SCUTE_SHOULDER_GUARD_PAIR,
            MaterialRarity.LUMBER,
            RelicEffect.flat(RelicAttribute.ARMOR_TOUGHNESS, 2.0D));

    /**
     * 龟壳肩甲（左）—— 皮革肩甲缝上三块海龟壳之后的另一种进阶形态，护住玩家自身的左肩。
     *
     * <p><b>它与鳞片肩甲是并列的两条路，各有取舍</b>：两者护肩减伤相同（都是 2.5 点），
     * 而鳞片那条给 <b>+1.0 盔甲韧性</b>、龟壳这条只给 <b>+0.5</b>——换来的是龟壳独有的一手：
     * 打在左肩上的<b>远程攻击有 10% 会被整个弹开</b>（登记在 {@link ShoulderGuards 肩甲表}）。
     * 一条更耐打，一条能拨箭。</p>
     *
     * <p>同样是「只认附着份」、只缝胸甲。固有稀有度为成材：与龟壳内衬、鳞甲内衬同一档。</p>
     */
    public static final RelicDefinition TURTLE_SHELL_SHOULDER_GUARD_LEFT = defineAttachmentOnly(
            ModItems.TURTLE_SHELL_SHOULDER_GUARD_LEFT,
            MaterialRarity.LUMBER,
            RelicEffect.flat(RelicAttribute.ARMOR_TOUGHNESS, 0.5D));

    /**
     * 龟壳肩甲（右）—— 与左肩那只成对，护住玩家自身的右肩。
     *
     * <p>登记内容与左肩那只完全对称。</p>
     */
    public static final RelicDefinition TURTLE_SHELL_SHOULDER_GUARD_RIGHT = defineAttachmentOnly(
            ModItems.TURTLE_SHELL_SHOULDER_GUARD_RIGHT,
            MaterialRarity.LUMBER,
            RelicEffect.flat(RelicAttribute.ARMOR_TOUGHNESS, 0.5D));

    /**
     * 一套龟壳肩甲 —— 左右两只合成而来的整体，两侧肩膀都护，两侧的箭都可能被弹开。
     *
     * <p>韧性给到 1.0，同样是左右两只相加的结果。护肩减伤与弹开概率都与单只相同，
     * 但覆盖两侧，登记在 {@link ShoulderGuards}。固有稀有度为成材。</p>
     */
    public static final RelicDefinition TURTLE_SHELL_SHOULDER_GUARD_PAIR = defineAttachmentOnly(
            ModItems.TURTLE_SHELL_SHOULDER_GUARD_PAIR,
            MaterialRarity.LUMBER,
            RelicEffect.flat(RelicAttribute.ARMOR_TOUGHNESS, 1.0D));

    /**
     * 太阳纹章 —— 带在身上时，白天持续给「生命恢复」与「力量」。
     *
     * <p>它<b>没有任何属性加成</b>，价值全在白天那两条状态效果上：由
     * {@link org.eternalrelic.capability.carried.DayNightEmblemEffect} 按刻核对并续期，
     * 管哪一段、给哪两条登记在 {@link DayNightEmblems} 里
     * （★ 想让第三枚纹章换个时段或换两条效果，往那张表加一行即可，不必动能力类）。</p>
     *
     * <p><b>带在身上就生效</b>：放主背包、副手，或者缝在装备与盾牌上，都算
     * （与勇气纹章、斑驳的铜甲片同一条口径，由能力类自己判断）。</p>
     *
     * <p>固有稀有度为成材：与勇气纹章、川流纹章同一档的蜡制纹章。</p>
     */
    public static final RelicDefinition SUN_EMBLEM = define(
            ModItems.SUN_EMBLEM,
            MaterialRarity.LUMBER);

    /**
     * 月亮纹章 —— 缝在装备或盾牌上，夜晚持续给「生命恢复」与「速度」。
     *
     * <p>与太阳纹章同一路数，只是管夜晚、给的是速度。两枚可以分别缝在不同的部位上，
     * 但它们管的时段互补，因此同一时刻只会有一枚在给效果。</p>
     *
     * <p>固有稀有度为成材。</p>
     */
    public static final RelicDefinition MOON_EMBLEM = define(
            ModItems.MOON_EMBLEM,
            MaterialRarity.LUMBER);

    // ==================== 品阶样本（测试用） ====================

    /**
     * 七件品阶样本 —— 每件只登记一个材料档位，既没有携带效果也没有守护效果。
     *
     * <p>用途是检查遗物界面：界面按 {@code rarity} 选用对应的面板样式，
     * 把它们并排放在身上，就能一次看全七个档位的观感是否协调。
     * 正式发布前应连同物品注册、贴图与语言条目一并移除。</p>
     */
    public static final RelicDefinition SAMPLE_DEBRIS = define(
            ModItems.RELIC_SAMPLE_DEBRIS, MaterialRarity.DEBRIS);

    /** 品阶样本·粗石。 */
    public static final RelicDefinition SAMPLE_ROUGH = define(
            ModItems.RELIC_SAMPLE_ROUGH, MaterialRarity.ROUGH_STONE);

    /** 品阶样本·成材。 */
    public static final RelicDefinition SAMPLE_LUMBER = define(
            ModItems.RELIC_SAMPLE_LUMBER, MaterialRarity.LUMBER);

    /** 品阶样本·精萃。 */
    public static final RelicDefinition SAMPLE_ESSENCE = define(
            ModItems.RELIC_SAMPLE_ESSENCE, MaterialRarity.ESSENCE);

    /** 品阶样本·珍品。 */
    public static final RelicDefinition SAMPLE_TREASURE = define(
            ModItems.RELIC_SAMPLE_TREASURE, MaterialRarity.TREASURE);

    /** 品阶样本·至宝。 */
    public static final RelicDefinition SAMPLE_SUPREME = define(
            ModItems.RELIC_SAMPLE_SUPREME, MaterialRarity.SUPREME);

    /** 品阶样本·源质。 */
    public static final RelicDefinition SAMPLE_SOURCE = define(
            ModItems.RELIC_SAMPLE_SOURCE, MaterialRarity.SOURCE);

    /**
     * 守夜之瞳·左眼 —— 装入左眼后，在低光环境下看清周围。
     *
     * <p>它不靠「放在背包里」生效，而是由玩家右键装入，因此这里没有携带属性加成；
     * 装入过程与它索取的代价由
     * {@link org.eternalrelic.capability.worn.WornRelicEffect} 负责。</p>
     */
    public static final RelicDefinition NIGHTWATCH_EYE_LEFT = define(
            ModItems.NIGHTWATCH_EYE_LEFT,
            MaterialRarity.ESSENCE);

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
            MaterialRarity.ESSENCE);

    /**
     * 守夜之瞳·右眼 —— 装入右眼后，在低光环境下照见附近的活物。
     *
     * <p>与左眼一样属于装入型遗物，没有携带属性加成。</p>
     */
    public static final RelicDefinition NIGHTWATCH_EYE_RIGHT = define(
            ModItems.NIGHTWATCH_EYE_RIGHT,
            MaterialRarity.ESSENCE);

    /**
     * 守夜之瞳·右眼（耗尽）—— 与左眼的耗尽形态同理，登记进遗物表只为让说明文字显示出来。
     *
     * <p>固有稀有度沿用右眼本体的精萃。</p>
     */
    public static final RelicDefinition NIGHTWATCH_EYE_RIGHT_DRAINED = define(
            ModItems.NIGHTWATCH_EYE_RIGHT_DRAINED,
            MaterialRarity.ESSENCE);

    private ModRelics() {
    }

    /**
     * 由 {@link ModItems#register()} 调用，触发本类静态内容初始化（也就是把上面的遗物登记进名单）。
     */
    static void register() {
    }

    /**
     * 登记一件只有属性加成的遗物。
     *
     * @param item    遗物对应的物品
     * @param rarity  遗物固有稀有度（沿用材料档位）
     * @param effects 携带时生效的属性加成，可以登记多条；没有则一条都不写
     * @return 登记好的遗物定义
     */
    private static RelicDefinition define(Item item, MaterialRarity rarity, RelicEffect... effects) {
        return register(item, rarity, effects, null, false, false);
    }

    /**
     * 登记一件「携带生效时会先亮相」的遗物。
     *
     * <p>「入手表现」指的是玩家刚开始携带它时的那一记心跳声，以及涌出后收敛回来的光点。
     * 只有确实值得亮相的遗物才走这条登记路径；其余遗物安静地生效，不响也不冒粒子。</p>
     *
     * @param item    遗物对应的物品
     * @param rarity  遗物固有稀有度（沿用材料档位）
     * @param effects 携带时生效的属性加成，可以登记多条
     * @return 登记好的遗物定义
     */
    private static RelicDefinition defineWithArrival(Item item, MaterialRarity rarity, RelicEffect... effects) {
        return register(item, rarity, effects, null, true, false);
    }

    /**
     * 登记一件没有属性加成、只在受到攻击时出手守护的遗物。
     *
     * @param item   遗物对应的物品
     * @param rarity 遗物固有稀有度（沿用材料档位）
     * @param ward   受到攻击时的守护效果
     * @return 登记好的遗物定义
     */
    private static RelicDefinition defineWard(Item item, MaterialRarity rarity, DamageWard ward) {
        return register(item, rarity, new RelicEffect[0], ward, false, false);
    }

    /**
     * 登记一件「只认附着份」的遗物——它的属性加成放在背包里不算数，必须缝在装备上。
     *
     * @param item    遗物对应的物品
     * @param rarity  遗物固有稀有度（沿用材料档位）
     * @param effects 缝在装备上时生效的属性加成，可以登记多条
     * @return 登记好的遗物定义
     */
    private static RelicDefinition defineAttachmentOnly(Item item, MaterialRarity rarity, RelicEffect... effects) {
        return register(item, rarity, effects, null, false, true);
    }

    /**
     * 登记一件遗物。
     *
     * @param item           遗物对应的物品
     * @param rarity         遗物固有稀有度（沿用材料档位）
     * @param effects        携带时生效的属性加成，可以登记多条；没有则留空数组
     * @param ward           受到攻击时的守护效果，没有则为 {@code null}
     * @param arrivalEffect  刚开始携带时是否播放一记「入手」表现
     * @param attachmentOnly 属性加成是否只认附着份（放在背包里不算数）
     * @return 登记好的遗物定义
     */
    private static RelicDefinition register(Item item, MaterialRarity rarity, RelicEffect[] effects, DamageWard ward,
            boolean arrivalEffect, boolean attachmentOnly) {
        RelicDefinition definition = new RelicDefinition(item, Registries.ITEM.getId(item), rarity, List.of(effects),
                ward, arrivalEffect, attachmentOnly);
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
