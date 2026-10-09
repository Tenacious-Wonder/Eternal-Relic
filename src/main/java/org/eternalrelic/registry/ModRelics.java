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
     * 旅人吊坠 —— 带在身上时，骑乘坐骑的移动速度提升一成半。
     *
     * <p>它<b>没有任何属性加成</b>：这份加速落在玩家骑的那只坐骑身上，而不是玩家自己身上，
     * 因此走不了「属性加成一栏」——那一栏加的永远是玩家自己的属性，而骑乘时的速度只看坐骑的。
     * 挂上与摘下的时机由
     * {@link org.eternalrelic.capability.carried.TravelerPendantEffect} 每 5 刻核对一次。</p>
     *
     * <p><b>带多枚也只算一次</b>：这里登记的效果为空，能力类只问「带没带」，不问带了几枚。</p>
     *
     * <p>固有稀有度为粗石：一块磨得发亮的旧铜板，顶用，但称不上讲究。</p>
     */
    public static final RelicDefinition TRAVELER_PENDANT = define(
            ModItems.TRAVELER_PENDANT,
            MaterialRarity.ROUGH_STONE);

    /**
     * 学者单片眼镜 —— 带在身上时，每次获得经验都会额外得到一点。
     *
     * <p>它<b>没有任何属性加成</b>：经验是游戏在结算时算出来的一个数字，既不是玩家的属性，
     * 也不能靠「多挂一条」表示，因此这里只登记身份与成色，实际那一点由
     * {@link org.eternalrelic.mixin.PlayerEntityMixin} 在经验落到玩家身上的那一刻加上去。</p>
     *
     * <p><b>带多枚也只算一点</b>：这里登记的效果为空，注入那一处只问「带没带」。</p>
     *
     * <p>固有稀有度为粗石：一只做工讲究的小镜子，好用，但谈不上稀罕。</p>
     */
    public static final RelicDefinition SCHOLAR_MONOCLE = define(
            ModItems.SCHOLAR_MONOCLE,
            MaterialRarity.ROUGH_STONE);

    /**
     * 馆藏透镜 —— 举起来看时，十三格内的箱子正中心浮起一枚淡黄光斑，隔着方块也看得见。
     *
     * <p><b>它是本模组第一件「拿在手上用」而不是「放在背包里就生效」的遗物</b>：效果只在按住
     * 右键举镜的那段时间里存在，松手即散。因此这里只登记身份与成色，真正的判定由
     * {@link org.eternalrelic.capability.using.CuratorLensEffect} 负责，画面由客户端的
     * {@code ChestMarkRenderer} 负责。</p>
     *
     * <p><b>借的是原版望远镜的全套表现</b>：举起时画面放大、手上摆出举镜姿势、边框遮罩与那两记
     * 声响都照原版走，只有放大倍率改成了两倍。</p>
     *
     * <p>固有稀有度为粗石：一副做工讲究的观察镜，顶用，但谈不上稀罕。</p>
     */
    public static final RelicDefinition CURATOR_LENS = define(
            ModItems.CURATOR_LENS,
            MaterialRarity.ROUGH_STONE);

    /**
     * 铸铁拇指戒 —— 带在身上时，用的工具与武器有 5% 的机会不掉耐久。
     *
     * <p>它<b>没有任何属性加成</b>：耐久是在物品自己身上一点点扣掉的，既不是玩家的属性，
     * 也无法靠「多挂一条」表示，因此这里只登记身份与成色，真正的拦截写在
     * {@link org.eternalrelic.mixin.ItemStackDurabilityMixin} 里——耐久将要落下的那一刻
     * 掷一次骰子，中了就整次不扣。</p>
     *
     * <p><b>只认工具与武器</b>：护甲挨打时也掉耐久，而它走的是同一条路，因此那个注入点
     * 按物品类别把关（判据复用 {@code AttachTarget} 的分类）。</p>
     *
     * <p><b>带多枚也只算 5%</b>：这里登记的效果为空，注入那一处只问「带没带」。</p>
     *
     * <p>固有稀有度为粗石：一只没有纹路的铁戒指，顶用，但称不上讲究。</p>
     */
    public static final RelicDefinition CAST_IRON_THUMB_RING = define(
            ModItems.CAST_IRON_THUMB_RING,
            MaterialRarity.ROUGH_STONE);

    /**
     * 猎人徽章 —— 带在身上时，击杀生物有 5% 的机会额外多掉一件战利品。
     *
     * <p>它<b>没有任何属性加成</b>：掉落结果是游戏照掉落表算出来的，既不是玩家的属性，
     * 也无法靠「多挂一条」表示，因此这里只登记身份与成色。真正的加料写在
     * {@link org.eternalrelic.capability.carried.HunterBadgeEffect} 里——击杀时先记下
     * 掉落表给出的每一样东西，再从中随机挑一样、数量多给一个。</p>
     *
     * <p><b>只有玩家击杀才算</b>，且带多枚也只算 5%。</p>
     *
     * <p>固有稀有度为粗石：一枚饱经风霜的旧徽章，顶用，但称不上讲究。</p>
     */
    public static final RelicDefinition HUNTER_BADGE = define(
            ModItems.HUNTER_BADGE,
            MaterialRarity.ROUGH_STONE);

    /**
     * 轻巧盾徽 —— 带在身上时，举着盾牌的那段时间走得更快；钉在盾牌上另外给两点护甲。
     *
     * <p><b>它的两条好处走的是两条不同的路。</b>举盾加速是<b>有条件</b>的——盾一放下就该没，
     * 而这张表登记的属性只能表达「带着就算数」，因此那一条不进这张表，由
     * {@link org.eternalrelic.capability.carried.LightweightShieldBadgeEffect} 每 5 刻自己核对。</p>
     *
     * <p>这里登记的<b>只有护甲那一条</b>，而且走的是「只认附着份」——放在背包里不给护甲，
     * 必须真的钉在盾牌上才算数。两条各管各的，因此钉上去之后是「举盾更快 + 两点护甲」两样都有。</p>
     *
     * <p>固有稀有度为成材：一块正经做出来的木雕，配得上它给的那点分量。</p>
     */
    public static final RelicDefinition LIGHTWEIGHT_SHIELD_BADGE = defineAttachmentOnly(
            ModItems.LIGHTWEIGHT_SHIELD_BADGE,
            MaterialRarity.LUMBER,
            RelicEffect.flat(RelicAttribute.ARMOR, 2.0D));

    /**
     * 牧羊人铃铛 —— 拿在主手右键摇响，四十格内的羊会朝摇铃的人走过来，持续二十秒。
     *
     * <p>它<b>没有任何属性加成</b>：铃声不改变玩家的任何数值，改的是羊的行走目标，
     * 因此这里只登记身份与成色。招呼的时机、范围与冷却由
     * {@link org.eternalrelic.capability.carried.ShepherdBellEffect} 负责——
     * 由玩家摇铃触发，不是「放在背包里就生效」那一类。</p>
     *
     * <p><b>只招呼羊</b>：牛、猪、鸡一概不理，这是制作者定下的口径。</p>
     *
     * <p>固有稀有度为粗石：一只用旧了的黄铜铃铛，顶用，但称不上讲究。</p>
     */
    public static final RelicDefinition SHEPHERD_BELL = define(
            ModItems.SHEPHERD_BELL,
            MaterialRarity.ROUGH_STONE);

    /**
     * 一个圆形的饼 —— 吃下去，人当场回到自己的重生点，并在十秒里飞快回血。
     *
     * <p><b>它是本模组第一件消耗品，也是第一件「吃下去才生效」的遗物</b>：效果发生在它消失的
     * 那一刻，此后什么都不剩。因此这里既没有携带效果、也不在挨打时出手，只登记身份与成色；
     * 真正的那两件事由 {@link org.eternalrelic.capability.consumed.RoundCakeEffect}
     * 在吃完时执行。</p>
     *
     * <p>填饱肚子的份量与南瓜派逐字相同（直接沿用原版那一份食物数据）。</p>
     *
     * <p>固有稀有度为粗石：一块家常的吃食，难得的是那份心意。</p>
     */
    public static final RelicDefinition ROUND_CAKE = define(
            ModItems.ROUND_CAKE,
            MaterialRarity.ROUGH_STONE);

    /**
     * 红土蜡块 —— 拿在主手右键，把副手那件东西补回一段耐久的消耗品。
     *
     * <p><b>它和「一个圆形的饼」是同一路东西</b>：用掉才生效，效果发生在它消失的那一刻，此后
     * 什么都不剩。因此这里既没有携带效果、也不在挨打时出手，只登记身份与成色；真正那件事由
     * {@link org.eternalrelic.capability.consumed.RedClayWaxEffect} 在右键时执行。</p>
     *
     * <p>它除了当消耗品，本身还是可用的遗物素材——这一层写在那段说明文字里就够了，
     * 表里不必另外登记什么。</p>
     *
     * <p>固有稀有度为粗石：从恶地深处挖出来的粘土，算不上稀罕物件。</p>
     */
    public static final RelicDefinition RED_CLAY_WAX = define(
            ModItems.RED_CLAY_WAX,
            MaterialRarity.ROUGH_STONE);

    /**
     * 镀金骰子 —— 带在背包里时幸运 +2。
     *
     * <p>与「干枯的四叶草」（幸运 +1）同一条路数，只是这一枚成色更好、给得更多。
     * 幸运由游戏在跑掉落表与钓鱼时读取，本模组不必再做任何事。</p>
     *
     * <p>固有稀有度为粗石：一枚坠手的骰子，谈不上稀罕，但确实好用。</p>
     */
    public static final RelicDefinition GILDED_DIE = define(
            ModItems.GILDED_DIE,
            MaterialRarity.ROUGH_STONE,
            RelicEffect.flat(RelicAttribute.LUCK, 2.0D));

    /**
     * 远行绑腿 —— <b>一次性道具</b>：右键缠上，换来五天的脚力（移速 +12%）。
     *
     * <p><b>它没有任何属性加成</b>（制作者 2026-10-06 从「带在身上就加速」改成一次性道具）：
     * 物品一用就消失，加速由「远行的祝福」这条状态效果承担 —— 见
     * {@code registry/ModStatusEffects} 与 {@link org.eternalrelic.item.WandererGaitersItem}。
     * 这里只登记身份与成色。</p>
     *
     * <p>固有稀有度为粗石：一双结实的绑腿，走长路的人都会缠一副。</p>
     */
    public static final RelicDefinition WANDERER_GAITERS = define(
            ModItems.WANDERER_GAITERS,
            MaterialRarity.ROUGH_STONE);

    /**
     * 炽心纹章 —— 身上带着它时，近战打中谁，谁就烧起来。
     *
     * <p><b>放在背包里就生效</b>，缝在正穿着的防具上也生效（走 {@code define}，
     * 由 {@link org.eternalrelic.capability.carried.EmberheartEmblemEffect} 判定）。
     * 它<b>没有任何属性加成</b>，价值全在那一次点火上。</p>
     *
     * <p>★ <b>纹章的通用口径（制作者 2026-10-06 定，做新纹章时照这条走）</b>：
     * <b>除了「给附着物补附魔」的那几枚（走 {@code EnchantingRelics}，必须缝上去才有对象可补），
     * 其余纹章一律「放在背包里就生效」</b>，缝在装备上只是多一条生效途径、不是前提。
     * 因此新纹章不要顺手写成 {@code defineAttachmentOnly} —— 那条路是留给装备配件（甲片 / 内衬 /
     * 肩甲 / 胸甲片 / 斗篷）的。</p>
     *
     * <p><b>只认近战</b>：判据是「伤害的直接来源就是玩家本人」，因此箭与火球点不着对手
     * ——这与近战受击部位判定用的是同一把尺子。</p>
     *
     * <p>固有稀有度为精萃：原版火焰附加 II 是 8 秒，它每次都给 10 秒，而且不占附魔位。</p>
     */
    public static final RelicDefinition EMBERHEART_EMBLEM = define(
            ModItems.EMBERHEART_EMBLEM,
            MaterialRarity.ESSENCE);

    /**
     * 沙漏药瓶 —— 右键把身上所有增益各延长 9 秒；一共三次，用尽变成空瓶。
     *
     * <p>它<b>没有携带效果</b>：要点一下才生效，因此真正那件事写在
     * {@link org.eternalrelic.item.HourglassVialItem} 里，这里只登记身份与成色。</p>
     *
     * <p>固有稀有度为成材：一次能给两三条药水续命，关键时刻真能救人。</p>
     */
    public static final RelicDefinition HOURGLASS_VIAL = define(
            ModItems.HOURGLASS_VIAL,
            MaterialRarity.LUMBER);

    /**
     * 空的沙漏药瓶 —— 三次用完之后的形态，一点效果也没有。
     *
     * <p>登记进遗物表只为一件事：<b>效果说明只有遗物界面会显示，而界面要求物品在遗物表里</b>。
     * 它的 {@code effect} 与 {@code ward} 都是空的，因此不会带来任何额外效果
     * （与「碎裂的回响之环」「黯淡的余烬吊坠」同一套做法）。</p>
     */
    public static final RelicDefinition HOURGLASS_VIAL_EMPTY = define(
            ModItems.HOURGLASS_VIAL_EMPTY,
            MaterialRarity.LUMBER);

    /**
     * 剥皮小刀 —— 带在背包里时，击杀动物有机会多掉一件。
     *
     * <p>与猎人徽章共用掉落表那一处注入，两者可以一起带、各掷各的骰子。
     * 判据（只认动物）与加料过程见
     * {@link org.eternalrelic.capability.carried.SkinningKnifeEffect}。</p>
     *
     * <p>固有稀有度为<b>碎屑</b>：一把用得旧了的小刀，算不上什么稀罕物件
     * （制作者 2026-10-06 从粗石降下来）。</p>
     */
    public static final RelicDefinition SKINNING_KNIFE = define(
            ModItems.SKINNING_KNIFE,
            MaterialRarity.DEBRIS);

    /**
     * 唤马哨 —— 右键把三百格内的马、驴、骡叫到身边来。
     *
     * <p>没有携带效果：要点一下才生效。做法是「先搬近、再让它们自己跑完最后一段」，
     * 见 {@link org.eternalrelic.capability.carried.HorseWhistleEffect}。</p>
     *
     * <p>固有稀有度为<b>碎屑</b>：一个能吹响的哨子，出门在外的人腰间都挂一个
     * （制作者 2026-10-06 从粗石降下来）。</p>
     */
    public static final RelicDefinition HORSE_WHISTLE = define(
            ModItems.HORSE_WHISTLE,
            MaterialRarity.DEBRIS);

    /**
     * 破阵之书 —— <b>一次性道具</b>：右键读一遍，学会「破阵」（举盾蓄力冲刺）。
     *
     * <p><b>它没有任何携带效果</b>：真正那件事是"教出一门手艺"，而手艺记在玩家自己身上
     * （见 {@code skill/ShieldRushSkill}）。登记进遗物表还有一个实际用处 ——
     * <b>只有登记了，玩家才能在遗物界面（按 Shift）里读到用法</b>，
     * 与「黯淡的余烬吊坠」「空的沙漏药瓶」同一套做法。</p>
     *
     * <p>固有稀有度为成材：一门能反复用的手艺，比一件用完就没的东西贵重。</p>
     */
    public static final RelicDefinition FORMATION_BREAKER_TOME = define(
            ModItems.FORMATION_BREAKER_TOME,
            MaterialRarity.LUMBER);

    /**
     * 荆棘之誓 —— 带在身上时，把挨打时<b>实际掉的那部分血</b>的五分之一扎回给打你的人（最多 20 点）。
     *
     * <p>它<b>没有任何属性加成</b>，也不替玩家挡伤害：价值全在「动手的人也要付代价」上，
     * 由 {@link org.eternalrelic.capability.carried.ThornsOathEffect} 出手——
     * 取值的那一刻在 {@code mixin/PlayerDamageMixin} 里（护甲与保护附魔都算完之后），
     * 因此穿好甲的人反得少、裸着挨打的人反得多，这是制作者指定的口径。</p>
     *
     * <p><b>反伤不会再被反伤</b>：两个各带一枚的人互砍，不会一直弹到某一方死掉
     * （做法是让反伤走原版「荆棘」那个伤害类型，理由见那个类的文档）。</p>
     *
     * <p>固有稀有度为珍品：一件能把交手结果往回收一点的东西，够得上这一档。</p>
     */
    public static final RelicDefinition THORNS_OATH = define(
            ModItems.THORNS_OATH,
            MaterialRarity.TREASURE);

    /**
     * 无声软靴 —— 带在身上时，潜行状态下不再发出振动，也更不容易被怪物发现。
     *
     * <p>它<b>没有任何属性加成</b>：改动落在两处游戏内部——「振动要不要发出去」与
     * 「被怪物发现的距离」，判断都由
     * {@link org.eternalrelic.capability.carried.SilentBootsEffect} 给出。</p>
     *
     * <p><b>不潜行就等于没戴</b>，这是刻意的：软靴护的是「蹑手蹑脚」这件事，玩家自己一眼就懂。</p>
     *
     * <p>固有稀有度为精萃：它不改任何数值，改的是「别人能不能察觉到你」。</p>
     */
    public static final RelicDefinition SILENT_BOOTS = define(
            ModItems.SILENT_BOOTS,
            MaterialRarity.ROUGH_STONE);

    /**
     * 拾荒符石（物品 id 仍是 {@code scavenger_magnet}）—— 带在身上时，身边三格内的掉落物自己进到身上。
     *
     * <p>它<b>没有任何属性加成</b>：价值全在「走过去捡」这个动作被省掉上，由
     * {@link org.eternalrelic.capability.carried.ScavengerMagnetEffect} 每 5 刻清扫一次。</p>
     *
     * <p><b>与拾荒口袋配套</b>：身上带着一口装了它的口袋时，吸过来的东西优先塞进那口口袋，
     * 不占玩家自己的背包；没有这样的口袋就照常进背包。两条路用的是同一份判断
     * （见 {@link org.eternalrelic.relic.PocketStorage#insertIntoMagnetPocket}）。</p>
     *
     * <p>固有稀有度为成材：一件省事的家什，谈不上稀罕，但用过就回不去。</p>
     */
    public static final RelicDefinition SCAVENGER_MAGNET = define(
            ModItems.SCAVENGER_MAGNET,
            MaterialRarity.DEBRIS);

    /**
     * 拾荒口袋 —— 一口 54 格的便携背包，主手右键打开。
     *
     * <p>它<b>本身就是一件普通容器</b>：里面装了什么与「有没有带着磁石」无关，
     * 因此这里没有效果可登记，只登记身份与成色。内容怎么存、怎么在拾取时优先收货，
     * 都写在 {@link org.eternalrelic.relic.PocketStorage} 里。</p>
     *
     * <p>固有稀有度为成材：与磁石同一档。</p>
     */
    public static final RelicDefinition SCAVENGER_POCKET = define(
            ModItems.SCAVENGER_POCKET,
            MaterialRarity.DEBRIS);

    /**
     * 风行披风 —— 缝在胸甲上：跑得更快、人在空中还能再蹬一次，并且护住后背。
     *
     * <p><b>只认附着份</b>：它是「装备配件」，放背包里完全没有用（与肩甲、内衬、斗篷同一口径）。
     * 两样本事各走各的路：<b>二段跳</b>由
     * {@link org.eternalrelic.capability.attached.WindCloakEffect} 负责（按键在客户端读、
     * 出手在服务端），<b>护后背</b>登记在 {@link ChestGuards 胸甲护具表} 里——
     * 护具那套「按部位减伤」的机制是现成的，这一件只是多了一行登记。</p>
     *
     * <p>固有稀有度为精萃：它不改任何数值，改的是玩家在空中的行动方式，够得上这一档。</p>
     */
    public static final RelicDefinition WIND_CLOAK = defineAttachmentOnly(
            ModItems.WIND_CLOAK,
            MaterialRarity.ESSENCE,
            new RelicEffect(RelicAttribute.MOVEMENT_SPEED, 0.15D, 0.0D, 1));

    /**
     * 褪色的护身符 —— 带在身上时生命上限多出半颗心。
     *
     * <p>它<b>没有任何额外的本事</b>：价值全在那一点生命上限上，因此这里登记一条固定值加成即可，
     * 连能力类都不必写。固定值而不是百分比——半颗心就该是半颗心，不随别的加成一起放大。</p>
     *
     * <p>固有稀有度为碎屑：一件褪了色的旧护符，护住的是"再挨一下"的余地。</p>
     */
    public static final RelicDefinition FADED_CHARM = define(
            ModItems.FADED_CHARM,
            MaterialRarity.DEBRIS,
            RelicEffect.flat(RelicAttribute.MAX_HEALTH, 1.0D));

    /**
     * 磕碰的罗盘 —— 拿在手上右键，在聊天栏报出坐标与朝向。
     *
     * <p>它<b>什么都不改</b>：既不加属性也不出手，只是把玩家本来要点 F3 才看得到的两样东西
     * 说出来（判定见 {@link org.eternalrelic.item.CrackedCompassItem}），因此这里只登记身份与成色。</p>
     *
     * <p>固有稀有度为碎屑：一件磕出了裂的旧罗盘，指针还转，只是转得不太准。</p>
     */
    public static final RelicDefinition CRACKED_COMPASS = define(
            ModItems.CRACKED_COMPASS,
            MaterialRarity.DEBRIS);

    /**
     * 干枯的四叶草 —— 带在身上时幸运 +1。
     *
     * <p>幸运是原版属性，由服务端在跑掉落表与钓鱼时读取，因此这一件也只需要登记一行。</p>
     *
     * <p>固有稀有度为碎屑：压干了的四叶草，据说能带来一点好运。</p>
     */
    public static final RelicDefinition DRIED_CLOVER = define(
            ModItems.DRIED_CLOVER,
            MaterialRarity.DEBRIS,
            RelicEffect.flat(RelicAttribute.LUCK, 1.0D));

    /**
     * 戴克森应急制氧球 —— 右键换来五分钟的水下呼吸，之后十分钟不能再点。
     *
     * <p>它<b>没有携带效果</b>：要点一下才生效，因此价值不在属性上。施加与冷却都由
     * {@link org.eternalrelic.item.DaiksonOxygenOrbItem} 负责。</p>
     *
     * <p>固有稀有度为粗石：一件做工扎实的小装置，用得上，但谈不上稀罕。</p>
     */
    public static final RelicDefinition DAIKSON_OXYGEN_ORB = define(
            ModItems.DAIKSON_OXYGEN_ORB,
            MaterialRarity.LUMBER);

    /**
     * 平凡的自然符石（物品 id 仍是 {@code natural_rune}）—— 踩在自然地面上时走得快一点。
     *
     * <p>它<b>没有任何属性登记</b>：加成的条件是"脚下是不是自然地面"，会随走动不停变化，
     * 没法写死在遗物表里，因此由 {@link org.eternalrelic.capability.carried.NaturalRuneEffect}
     * 每 5 刻核对一次、按需挂上或摘掉（与巡夜斗篷同一套做法）。</p>
     *
     * <p>固有稀有度为粗石。</p>
     */
    public static final RelicDefinition NATURAL_RUNE = define(
            ModItems.NATURAL_RUNE,
            MaterialRarity.DEBRIS);

    /**
     * 古旧弓油 —— 涂在弓弩上，让射出去的箭更有杀伤。
     *
     * <p><b>只认附着份，且只认弓与弩</b>：放在背包里没有任何用，必须涂在正拿着的弓弩上。
     * 加伤不能走属性（箭的伤害与玩家的攻击力无关），因此由
     * {@link org.eternalrelic.capability.attached.BowOilEffect} 配一处注入来加。</p>
     *
     * <p>固有稀有度为粗石。</p>
     */
    public static final RelicDefinition OLD_BOW_OIL = defineAttachmentOnly(
            ModItems.OLD_BOW_OIL,
            MaterialRarity.DEBRIS);

    /**
     * 古旧剑带 —— 缠在近战武器上，挥砍更有力。
     *
     * <p><b>只认附着份，且只认剑 / 斧 / 三叉戟</b>。加的是玩家自己的「攻击力」属性，
     * 因此这里登记一行固定值即可，不必写能力类——近战伤害本来就照着这个属性算。</p>
     *
     * <p>固有稀有度为粗石。</p>
     */
    public static final RelicDefinition OLD_SWORD_BAND = defineAttachmentOnly(
            ModItems.OLD_SWORD_BAND,
            MaterialRarity.DEBRIS,
            RelicEffect.flatPerCopy(RelicAttribute.ATTACK_DAMAGE, 0.5D, 1));

    /**
     * 颠倒吊坠 —— 带在身上时，受伤偶尔会反过来：不掉血、改成回等量的血，代价是等量的经验点数。
     *
     * <p>它<b>没有任何属性加成</b>：价值全在"那一下被反过来"上，由
     * {@link org.eternalrelic.capability.carried.ReversalPendantEffect} 在伤害结算的中段出手。</p>
     *
     * <p><b>算的是最终值</b>（护甲与附魔都算完之后真正会扣掉的血），因此它的代价随"你有多耐打"变化：
     * 穿好甲的人省经验、裸着挨打的人费经验。经验不够时这一下照常挨——它不保证救命。</p>
     *
     * <p>固有稀有度为粗石。</p>
     */
    public static final RelicDefinition REVERSAL_PENDANT = define(
            ModItems.REVERSAL_PENDANT,
            MaterialRarity.ROUGH_STONE);

    // ==================== 会报信的四件小东西 ====================

    /**
     * 旧怀表 —— 拿在手上右键，报出此刻的钟点与距天黑 / 天亮还有多久。
     *
     * <p>它<b>什么都不改</b>：既不加属性也不出手，只是把世界时间折算成一句人话
     * （判定见 {@link org.eternalrelic.item.PocketWatchItem}），因此这里只登记身份与成色。</p>
     *
     * <p>固有稀有度为碎屑：一只走得还算准的旧表，值不了几个钱。</p>
     */
    public static final RelicDefinition POCKET_WATCH = define(
            ModItems.POCKET_WATCH,
            MaterialRarity.DEBRIS);

    /**
     * 气象瓶 —— 拿在手上右键，报出接下来的天气与大约还有多久变天。
     *
     * <p>读的是世界自己的天气计时器，因此这里同样只登记身份与成色，
     * 判定见 {@link org.eternalrelic.item.WeatherBottleItem}。</p>
     *
     * <p>固有稀有度为碎屑。</p>
     */
    public static final RelicDefinition WEATHER_BOTTLE = define(
            ModItems.WEATHER_BOTTLE,
            MaterialRarity.DEBRIS);

    /**
     * 回声螺壳 —— 拿在手上右键，报出四周活物的数量与最近那只敌对生物的方向。
     *
     * <p>判定见 {@link org.eternalrelic.item.EchoConchItem}。</p>
     *
     * <p>固有稀有度为碎屑。</p>
     */
    public static final RelicDefinition ECHO_CONCH = define(
            ModItems.ECHO_CONCH,
            MaterialRarity.DEBRIS);

    /**
     * 蜡封手账 —— 蹲下右键记下脚下的地点，平时右键报出那个地点在哪个方向、离多远。
     *
     * <p>记下的那一页写在物品自己的数据里，判定见
     * {@link org.eternalrelic.item.WaxSealedJournalItem}。</p>
     *
     * <p>固有稀有度为碎屑。</p>
     */
    public static final RelicDefinition WAX_SEALED_JOURNAL = define(
            ModItems.WAX_SEALED_JOURNAL,
            MaterialRarity.DEBRIS);

    // ==================== 招呼别的东西的两件 ====================

    /**
     * 驯兽哨 —— 拿在手上右键，把 30 格内自己的宠物叫到身边。
     *
     * <p><b>它是本模组第一件「把生物搬过来」的遗物</b>：牧羊人铃铛只是让羊自己走过去，
     * 这一件是真的把伙伴传送到你脚边，判定见 {@link org.eternalrelic.item.BeastWhistleItem}。</p>
     *
     * <p>固有稀有度为粗石：走丢的伙伴能叫回来，这份便利值这一档。</p>
     */
    public static final RelicDefinition BEAST_WHISTLE = define(
            ModItems.BEAST_WHISTLE,
            MaterialRarity.ROUGH_STONE);

    /**
     * 回音石 —— 拿在手上右键，把 20 格内的怪物引向自己。
     *
     * <p><b>它是一件双刃剑</b>：不伤害任何东西，只是让怪认定「你在那里」，
     * 判定见 {@link org.eternalrelic.item.EchoStoneItem}。</p>
     *
     * <p>固有稀有度为粗石。</p>
     */
    public static final RelicDefinition ECHO_STONE = define(
            ModItems.ECHO_STONE,
            MaterialRarity.ROUGH_STONE);

    // ==================== 踩冰不滑的那一件 ====================

    /**
     * 雪地靴钉 —— 带在身上时，踩在冰面上不再打滑。
     *
     * <p>它<b>没有属性可登记</b>：改的是脚下方块的摩擦系数，由
     * {@link org.eternalrelic.capability.carried.SnowGripStudsEffect} 判断、
     * {@code mixin/SnowGripStudsMixin} 动手。</p>
     *
     * <p>固有稀有度为碎屑（制作者定的）：一套套在鞋上的防滑钉，做工简单、值不了几个钱。</p>
     */
    public static final RelicDefinition SNOW_GRIP_STUDS = define(
            ModItems.SNOW_GRIP_STUDS,
            MaterialRarity.DEBRIS);

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
     * 是另一件事，登记在 {@link ChestGuards 胸甲护具表} 里——一个长期挂在身上，
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
     * 护肩那一侧登记在 {@link ChestGuards}。</p>
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
     * {@link ChestGuards}。固有稀有度为粗石。</p>
     */
    public static final RelicDefinition LEATHER_SHOULDER_GUARD_PAIR = defineAttachmentOnly(
            ModItems.LEATHER_SHOULDER_GUARD_PAIR,
            MaterialRarity.ROUGH_STONE,
            RelicEffect.flat(RelicAttribute.ARMOR_TOUGHNESS, 1.0D));

    /**
     * 鳞片肩甲（左）—— 皮革肩甲缝上一层犰狳鳞甲之后的进阶形态，护住玩家自身的左肩。
     *
     * <p>与皮革肩甲同一路登记，只是数值高一档：<b>盔甲韧性 +1.0</b>（皮革那只 +0.5），
     * 护肩减伤则是 2.5 点（皮革那只 2 点），登记在 {@link ChestGuards 胸甲护具表}。
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
     * 2.5 点，但两侧都护，登记在 {@link ChestGuards}。固有稀有度为成材。</p>
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
     * 打在左肩上的<b>远程攻击有 10% 会被整个弹开</b>（登记在 {@link ChestGuards 胸甲护具表}）。
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
     * 但覆盖两侧，登记在 {@link ChestGuards}。固有稀有度为成材。</p>
     */
    public static final RelicDefinition TURTLE_SHELL_SHOULDER_GUARD_PAIR = defineAttachmentOnly(
            ModItems.TURTLE_SHELL_SHOULDER_GUARD_PAIR,
            MaterialRarity.LUMBER,
            RelicEffect.flat(RelicAttribute.ARMOR_TOUGHNESS, 1.0D));

    /**
     * 铁片肩甲（左）—— 皮革肩甲缝上一大块铁板的形态，护住玩家自身的左肩。
     *
     * <p><b>它是四档肩甲里第一件带「代价」的</b>：护肩减伤 2.5 点、远程弹开概率 20%
     * （都是全档最高），换来的是<b>挥砍慢 4%</b>——那一条就是这里登记的唯一属性，
     * 而且是负的百分比（见 {@link RelicAttribute#ATTACK_SPEED}）。</p>
     *
     * <p>按百分比扣而不是扣固定点数：游戏里攻击速度的基数随武器而变
     * （剑快、斧慢，都记在同一个属性上），扣固定点数会让原本就慢的武器慢得不成比例；
     * 按比例扣才是"无论拿什么，挥砍都慢这一成"。</p>
     *
     * <p><b>它不给盔甲韧性</b>——这是刻意的：好处已经是最高的减伤与最高的弹开概率，
     * 再加韧性就全面压过龟壳肩甲了。同样是「只认附着份」、只缝胸甲。</p>
     *
     * <p>固有稀有度为成材：与鳞片、龟壳那两档同一档，配得上它这份分量。</p>
     */
    public static final RelicDefinition IRON_SHOULDER_GUARD_LEFT = defineAttachmentOnly(
            ModItems.IRON_SHOULDER_GUARD_LEFT,
            MaterialRarity.LUMBER,
            new RelicEffect(RelicAttribute.ATTACK_SPEED, -0.04D, 0.0D, 1));

    /**
     * 铁片肩甲（右）—— 与左肩那只成对，护住玩家自身的右肩。
     *
     * <p>登记内容与左肩那只完全对称：同样是挥砍慢 4%、只认胸甲上的附着份。
     * 护肩那一侧登记在 {@link ChestGuards}。</p>
     *
     * <p>左右各缝一只时，两件是两件不同的遗物、各挂各的那一条，合计慢 8%——
     * 这正是用户要的「每多装备一个就再慢 4%」。</p>
     */
    public static final RelicDefinition IRON_SHOULDER_GUARD_RIGHT = defineAttachmentOnly(
            ModItems.IRON_SHOULDER_GUARD_RIGHT,
            MaterialRarity.LUMBER,
            new RelicEffect(RelicAttribute.ATTACK_SPEED, -0.04D, 0.0D, 1));

    /**
     * 一套铁片肩甲 —— 左右两只合成而来的整体，两侧肩膀都护。
     *
     * <p>减伤与弹开概率都与单只相同，但覆盖两侧；代价也只算一枚（挥砍慢 4%）。
     * 因此"合成一套"换到的是「胸甲上少占一个附着格」「两侧都护」以及
     * 「代价减半」这三样——比前几档多一样，是这一件最划算的地方。固有稀有度为成材。</p>
     */
    public static final RelicDefinition IRON_SHOULDER_GUARD_PAIR = defineAttachmentOnly(
            ModItems.IRON_SHOULDER_GUARD_PAIR,
            MaterialRarity.LUMBER,
            new RelicEffect(RelicAttribute.ATTACK_SPEED, -0.04D, 0.0D, 1));

    /**
     * 铜片肩甲（左）—— 皮革肩甲缝上一大块铜板的形态，护住玩家自身的左肩。
     *
     * <p><b>它比皮革肩甲还薄，却比它多一手</b>：护肩减伤只有 1.5 点（皮革那只 2 点），
     * 换来的是远程打中左肩时有一成半的几率被整个弹开——不用等到凑齐鳞甲或龟壳，
     * 铜就能做出会拨箭的肩甲。代价与铁片肩甲一样：<b>挥砍慢 4%</b>（见
     * {@link RelicAttribute#ATTACK_SPEED}），每缝一枚各算一枚。</p>
     *
     * <p><b>它不给盔甲韧性</b>——与铁片肩甲同一条口径：好处全在"少挨一点、偶尔拨开"上，
     * 不再额外送韧性。同样是「只认附着份」、只缝胸甲。</p>
     *
     * <p>固有稀有度为成材：与鳞片、龟壳、铁片那几档同一档。</p>
     */
    public static final RelicDefinition COPPER_SHOULDER_GUARD_LEFT = defineAttachmentOnly(
            ModItems.COPPER_SHOULDER_GUARD_LEFT,
            MaterialRarity.LUMBER,
            new RelicEffect(RelicAttribute.ATTACK_SPEED, -0.04D, 0.0D, 1));

    /**
     * 铜片肩甲（右）—— 与左肩那只成对，护住玩家自身的右肩。
     *
     * <p>登记内容与左肩那只完全对称：同样是挥砍慢 4%、只认胸甲上的附着份。
     * 护肩那一侧登记在 {@link ChestGuards}。</p>
     */
    public static final RelicDefinition COPPER_SHOULDER_GUARD_RIGHT = defineAttachmentOnly(
            ModItems.COPPER_SHOULDER_GUARD_RIGHT,
            MaterialRarity.LUMBER,
            new RelicEffect(RelicAttribute.ATTACK_SPEED, -0.04D, 0.0D, 1));

    /**
     * 一套铜片肩甲 —— 左右两只合成而来的整体，两侧肩膀都护。
     *
     * <p>减伤与弹开概率都与单只相同，但覆盖两侧；代价也只算一枚（挥砍慢 4%）。固有稀有度为成材。</p>
     */
    public static final RelicDefinition COPPER_SHOULDER_GUARD_PAIR = defineAttachmentOnly(
            ModItems.COPPER_SHOULDER_GUARD_PAIR,
            MaterialRarity.LUMBER,
            new RelicEffect(RelicAttribute.ATTACK_SPEED, -0.04D, 0.0D, 1));

    /**
     * 秘银胸甲片 —— 贴在胸甲正面的一块甲片，护住正胸。
     *
     * <p>这里登记的只有 <b>+1 点盔甲韧性</b>；「正胸挨打少掉 3 点、三成的箭被弹开、
     * 魔法伤害减 1 点」这三件事登记在 {@link ChestGuards 胸甲护具表} 里——
     * 一个长期挂在玩家身上，另几个只在挨打的那一刻算一次，结算时机不同，因此分成两处。</p>
     *
     * <p><b>它只护正胸，后背一点都挡不住</b>：一块贴在胸前的甲片护不住后背，这是刻意的。
     * 同样是「只认附着份」、只缝胸甲；类别上自成一类，因此一件胸甲上只能有一片，
     * 但可以与肩甲同时缝着。</p>
     *
     * <p>固有稀有度为珍品：一块来路不明的金属，本事在现有配件里最强。</p>
     */
    public static final RelicDefinition MITHRIL_CHESTPLATE_PLATE = defineAttachmentOnly(
            ModItems.MITHRIL_CHESTPLATE_PLATE,
            MaterialRarity.TREASURE,
            RelicEffect.flat(RelicAttribute.ARMOR_TOUGHNESS, 1.0D));

    /**
     * 铜胸甲片 —— 最普通的一片胸甲片，护住正胸。
     *
     * <p>这里登记的只有 <b>移动速度 −3%</b> 这条代价；「正胸挨打少掉 1.5 点、一成半的箭被弹开」
     * 登记在 {@link ChestGuards 胸甲护具表} 里。它<b>不挡魔法伤害，也不给盔甲韧性</b>——
     * 那是秘银那一片的本事，两片同属「胸甲片」类别，一件胸甲上只能挑一片。</p>
     *
     * <p>移动速度与肩甲的挥砍速度同属「负面百分比」：按比例扣，所以穿着它跑多快都是慢这一成，
     * 不会因为骑马 / 疾跑而变样。同样是「只认附着份」、只缝胸甲。</p>
     *
     * <p>固有稀有度为碎屑：最普通、最常规的一片，适合新手。</p>
     */
    public static final RelicDefinition COPPER_CHESTPLATE_PLATE = defineAttachmentOnly(
            ModItems.COPPER_CHESTPLATE_PLATE,
            MaterialRarity.DEBRIS,
            new RelicEffect(RelicAttribute.MOVEMENT_SPEED, -0.03D, 0.0D, 1));

    /**
     * 巡夜斗篷 —— 披在胸甲外的一件乌黑斗篷。
     *
     * <p><b>它的三样本事分属三条路，因此这里的效果一栏是空的</b>：</p>
     * <ul>
     *   <li><b>夜里（所处亮度低于 7）移速 +10%</b> —— 带着就行、不必缝；这是全项目第一条
     *       <b>看天色的属性加成</b>，由 {@code capability.carried.NightWatchCloakEffect}
     *       每 5 刻核对一次、按需挂上或摘下。⚠️ 下界与末地没有昼夜，那里永远不生效；</li>
     *   <li><b>缝在胸甲上时盔甲韧性 +0.5</b> —— 同样由那个能力类负责：「只在缝着时给」
     *       与「带着就给」是两种口径，没法一起写进属性那一栏；</li>
     *   <li><b>后背受到伤害少 1 点</b> —— 登记在 {@link ChestGuards 胸甲护具表} 里，
     *       与肩甲、胸甲片同一套机制。</li>
     * </ul>
     *
     * <p>它<b>不是</b>「只认附着份」的遗物：放在背包里也能在夜里拿到移速，只是没有韧性与护背。
     * 缝在胸甲上时，它与肩甲、内衬、甲片、胸甲片并列，各自占一个配件类别（斗篷自成一类）。</p>
     *
     * <p>固有稀有度为成材：与内衬、鳞片那几档同一档。</p>
     */
    public static final RelicDefinition NIGHTWATCH_CLOAK = define(
            ModItems.NIGHTWATCH_CLOAK,
            MaterialRarity.LUMBER);

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

    /**
     * 雷电纹章 —— <b>雷雨天</b>时移速 +15%、攻击 +2（普通下雨不算）。
     *
     * <p><b>效果一栏是空的</b>：它的加成看天气，一天要挂上摘下好几回，因此走
     * {@link org.eternalrelic.capability.carried.ConditionalAttributeEffect} 加
     * {@link ConditionalRelics} 那一路 —— 与巡夜斗篷（看天色）同一个道理，
     * 只是那一件独用一个能力类，这一族共用一张表。</p>
     *
     * <p>固有稀有度为成材：与太阳、月亮同一档的蜡制纹章。</p>
     */
    public static final RelicDefinition THUNDER_EMBLEM = define(
            ModItems.THUNDER_EMBLEM,
            MaterialRarity.LUMBER);

    /**
     * 无缺吊坠 —— 血量<b>满</b>时移速 +10%、攻击 +1；<b>一受伤立刻失效</b>。
     *
     * <p>同样没有属性加成写在这张表里，走条件遗物那一路。稀有度定为粗石：
     * 它不给"更多东西"，只把"保住满血"变成一件有回报的事 ——
     * 在此之前，挨打对玩家只有坏处。</p>
     */
    public static final RelicDefinition FLAWLESS_PENDANT = define(
            ModItems.FLAWLESS_PENDANT,
            MaterialRarity.ROUGH_STONE);

    /**
     * 噬血护符 —— <b>亲手击杀</b>生物后，按它最大生命的 10% 回血，单次最多 4 点。
     *
     * <p>它<b>没有属性加成</b>，价值全在击杀那一刻的续航上，由
     * {@link org.eternalrelic.capability.carried.BloodFeastEffect} 结算
     * （走的是现成的击杀事件，不需要额外注入）。</p>
     *
     * <p>稀有度定为粗石：与无缺吊坠同一档 —— 两件都是"改变一处小取舍"的护符，
     * 而不是"给更多东西"。</p>
     */
    public static final RelicDefinition BLOOD_FEAST_CHARM = define(
            ModItems.BLOOD_FEAST_CHARM,
            MaterialRarity.ROUGH_STONE);

    /**
     * 动能器柄 —— 附在<b>武器或工具</b>上，连着命中同一个目标 3 次，第 4 次造成 <b>1.5 倍</b>伤害。
     *
     * <p><b>只认附着</b>（制作者 2026-10-07 定）：放在背包里不算，必须让它真的长在某件武器或工具上。
     * 因此这里用 {@code defineAttachmentOnly}；至于"能附到哪几类东西上"，由
     * {@link AttachableRelics} 那一行限定（武器与工具两类）。</p>
     *
     * <p>它<b>没有属性加成</b>（改的是"某一击的伤害"，不是一条持续属性），
     * 也不在挨打时做任何事：价值全在平砍的节奏上，由
     * {@link org.eternalrelic.capability.carried.KineticHiltEffect}（记账）与
     * {@link org.eternalrelic.mixin.KineticHiltMixin}（换掉伤害数字）一起完成。</p>
     *
     * <p>稀有度定为粗石：它不改数值面板，只把"一直用同一件东西打同一个人"这件小事串起来。</p>
     */
    public static final RelicDefinition KINETIC_HILT = defineAttachmentOnly(
            ModItems.KINETIC_HILT,
            MaterialRarity.ROUGH_STONE);

    /**
     * 绿宝石徽章（原名"绿宝石徽章"）—— 与村民交易时<b>少付 25%</b>。
     *
     * <p><b>没有属性加成</b>，价值全在交易价格上，由
     * {@link org.eternalrelic.capability.carried.EmeraldBadgeEffect}（算折扣、记原价）与
     * {@link org.eternalrelic.mixin.EmeraldBadgeMixin}（在正确的时刻叫它）一起完成。</p>
     *
     * <p>稀有度从<b>粗石提升为精萃</b>（制作者 2026-10-07 定）：它给的不是战力，
     * 而是"在这世界的人情往来里少花四分之一"—— 按"改变了多宽的玩法面"来评，这一条够得上精萃。</p>
     */
    public static final RelicDefinition EMERALD_BADGE = define(
            ModItems.EMERALD_BADGE,
            MaterialRarity.ESSENCE);

    /**
     * 末影吊坠 —— <b>末影人不会因为你盯着它而发怒</b>（动手打它照样还手）。
     *
     * <p><b>没有属性加成</b>：它改的是另一种生物的一次判断，由
     * {@link org.eternalrelic.mixin.EnderPendantMixin} 与
     * {@link org.eternalrelic.capability.carried.EnderPendantEffect} 完成。</p>
     *
     * <p>稀有度定为成材：与可怕狼牙吊坠（镇住野狼）同一档 —— 都是"与某种生物讲和"。</p>
     */
    public static final RelicDefinition ENDER_PENDANT = define(
            ModItems.ENDER_PENDANT,
            MaterialRarity.LUMBER);

    /**
     * 繁花纹章 —— 缝在胸甲上，穿着它挨<b>近战</b>打之后随机得到一条 1 级增益，持续 10 秒。
     *
     * <p>它<b>没有任何属性加成</b>，也不在挨打时挡伤害：价值全在「挨一下、换一条随机的增益」上，
     * 由 {@link org.eternalrelic.capability.attached.BloomEmblemEffect} 负责。
     * 抽的是哪十一条、以及怎么做到「不与原有 buff 叠加」，都写在那一个类里。</p>
     *
     * <p><b>放在背包里就生效</b>，缝在胸甲上也算。若要缝上去，只能走遗物装卸台、
     * 而且只能缝胸甲（见 {@link AttachableRelics} 里那一行）——那两条管的是「怎么附上去」。
     * 因此它没有打开 {@code attachmentOnly}（那一栏管的是属性加成从哪儿算，而这件纹章没有属性加成），
     * 生效与否由能力类自己问。</p>
     *
     * <p>固有稀有度为成材：与勇气、川流、太阳、月亮四枚同一档的蜡制纹章。</p>
     */
    public static final RelicDefinition BLOOM_EMBLEM = define(
            ModItems.BLOOM_EMBLEM,
            MaterialRarity.LUMBER);

    /**
     * 余烬吊坠 —— 血量低于三成时再挨打，在身周炸开一圈，把凑上来的敌人一起掀开。
     *
     * <p>它没有属性加成，<b>也不是「守护」</b>——这一击照常挨，只是挨完之后反扑一次。
     * 守护那一栏（{@link DamageWard}）管的是「把这一击整个挡下、或改成给金心」，
     * 与这里要的「挨了再还手」是两件事，所以它没有登记守护，而是自己挂挨打判定
     * （见 {@link org.eternalrelic.capability.carried.EmberPendantEffect}）。</p>
     *
     * <p>固有稀有度为精萃：一次能救命的临场反扑，但只有命悬一线时才轮得到它。</p>
     */
    public static final RelicDefinition EMBER_PENDANT = define(
            ModItems.EMBER_PENDANT,
            MaterialRarity.ESSENCE);

    /**
     * 黯淡的余烬吊坠 —— 用尽 21 次之后的余烬吊坠，里头已经烧空。
     *
     * <p>它自身没有任何效果，登记进遗物表只为一件事：<b>效果说明只有遗物界面会显示，
     * 而界面要求物品在遗物表里</b>。不进遗物表的话，语言文件里那句「已经烧尽、用附魔之瓶
     * 在工作台上修一修就能再用」玩家永远看不到，提示框里也会少一行稀有度与
     * 「按左 Shift」的指路——回响之环的碎裂形态走的是同一条路。</p>
     *
     * <p>稀有度沿用本体（精萃）：烧空了只是它此刻的状态，成色不该随状态变化。</p>
     */
    public static final RelicDefinition EMBER_PENDANT_DULL = define(
            ModItems.EMBER_PENDANT_DULL,
            MaterialRarity.ESSENCE);

    /**
     * 归乡石 —— 拿在手上右键记下脚下这一处，再右键一次立刻回到那里
     * （代价是一个经验等级，之后冷却三分钟）。
     *
     * <p>它没有属性加成，也不在挨打时出手：价值全在「把位置存下来、之后再兑现」上，
     * 由 {@link org.eternalrelic.capability.carried.HomestoneEffect} 负责；
     * 位置与冷却都写在石头自己的数据里，因此身上带着两块时各记各的、各冷各的。</p>
     *
     * <p>固有稀有度为精萃：一件随时抽身的器物，但跨维度不行——那一条是刻意的取舍。</p>
     */
    public static final RelicDefinition HOMESTONE = define(
            ModItems.HOMESTONE,
            MaterialRarity.ESSENCE);

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
