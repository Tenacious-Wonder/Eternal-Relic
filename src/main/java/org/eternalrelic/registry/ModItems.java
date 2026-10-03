package org.eternalrelic.registry;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.FoodComponents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;

import org.eternalrelic.EternalRelic;
import org.eternalrelic.item.AotaBranchItem;
import org.eternalrelic.item.BeastWhistleItem;
import org.eternalrelic.item.CrackedCompassItem;
import org.eternalrelic.item.DaiksonOxygenOrbItem;
import org.eternalrelic.item.EchoConchItem;
import org.eternalrelic.item.EchoRingItem;
import org.eternalrelic.item.EchoStoneItem;
import org.eternalrelic.item.EnchantedRabbitFootItem;
import org.eternalrelic.item.NightwatchEyeItem;
import org.eternalrelic.item.PocketWatchItem;
import org.eternalrelic.item.RelicItem;
import org.eternalrelic.item.RoundCakeItem;
import org.eternalrelic.item.ScavengerPocketItem;
import org.eternalrelic.item.ShepherdBellItem;
import org.eternalrelic.item.SoulLanternItem;
import org.eternalrelic.item.WaxSealedJournalItem;
import org.eternalrelic.item.WeatherBottleItem;
import org.eternalrelic.relic.NightwatchEye;

/**
 * 本模组的物品注册入口。
 *
 * <p>每件物品以「静态常量 + 私有 register 方法」的形式声明：静态字段初始化时即完成注册，
 * 因此 {@link #register()} 只需被调用一次来触发类加载。</p>
 *
 * <p>遗物类物品还要在 {@link ModRelics} 里登记身份与携带效果，登记内容与这里的字段一一对应。</p>
 */
public final class ModItems {

    /**
     * 本模组在创造模式物品栏里的六个分类。
     *
     * <p><b>按「这东西是什么」分页，每页内部再按稀有度从低到高排</b>
     * （碎屑 → 粗石 → 成材 → 精萃 → 珍品 → 至宝 → 源质）—— 这是制作者定的规矩，
     * 为的是在创造模式里翻两下就能找到东西。六页各自装什么，见 {@link #register()} 里那六段登记。</p>
     *
     * <p>⚠️ <b>新增物品时必须挑一页登记进去</b>，否则它虽然做出来了，却不会出现在任何一个分类里。
     * <b>一件东西可以同时出现在多页</b>（巡夜斗篷既是佩饰、也是缝在胸甲上的配件），
     * 但同一页里只能有一份。</p>
     */
    private static final RegistryKey<ItemGroup> PENDANT_GROUP_KEY = relicGroup("pendant_group");

    /** 纹章那一页：五枚可以叠着钉在装备或武器上的纹章。 */
    private static final RegistryKey<ItemGroup> EMBLEM_GROUP_KEY = relicGroup("emblem_group");

    /** 装备配件那一页：缝在装备与武器上的东西（配件与涂装）。 */
    private static final RegistryKey<ItemGroup> ARMOR_FITTING_GROUP_KEY = relicGroup("armor_fitting_group");

    /** 器物那一页：灯、环、义眼、口袋、钟表、螺壳这类随身器物。 */
    private static final RegistryKey<ItemGroup> VESSEL_GROUP_KEY = relicGroup("vessel_group");

    /** 消耗品那一页：用掉就没的东西。 */
    private static final RegistryKey<ItemGroup> CONSUMABLE_GROUP_KEY = relicGroup("consumable_group");

    /** 材料与工具那一页：做遗物用的材料、工具与工作方块。 */
    private static final RegistryKey<ItemGroup> MATERIAL_GROUP_KEY = relicGroup("material_group");

    /**
     * 奥塔的枝叶 —— 携带在背包中时提升生命上限的遗物。
     */
    public static final Item AOTA_BRANCH = register("aota_branch",
            new AotaBranchItem(new Item.Settings().maxCount(1)));

    /**
     * 回响之环 —— 携带在背包中时，替玩家挡下攻击的遗物。
     */
    public static final Item ECHO_RING = register("echo_ring",
            new EchoRingItem(new Item.Settings().maxCount(1)));

    /**
     * 回响之环（碎裂）—— 替玩家挡下攻击后碎成的形态，冷却走完自行恢复原样。
     */
    public static final Item ECHO_RING_DRAINED = register("echo_ring_drained",
            new EchoRingItem(new Item.Settings().maxCount(1)));

    /**
     * 引魂燃灯 —— 携带时收集击杀所得的魂火，按 G 键一次倾泻出去的遗物。
     */
    public static final Item SOUL_LANTERN = register("soul_lantern",
            new SoulLanternItem(new Item.Settings().maxCount(1)));

    /**
     * 斑驳的铜甲片 —— 放在背包里时多给两点护甲的遗物。
     */
    public static final Item MOTTLED_COPPER_PLATE = register("mottled_copper_plate",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 勇气纹章 —— 放在背包里时，每隔一分钟替玩家攒下两颗金心的遗物。
     */
    public static final Item COURAGE_EMBLEM = register("courage_emblem",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 附魔兔脚 —— 挨打时换来一段速度的遗物，外观沿用原版兔子脚并常驻附魔光效。
     */
    public static final Item ENCHANTED_RABBIT_FOOT = register("enchanted_rabbit_foot",
            new EnchantedRabbitFootItem(new Item.Settings().maxCount(1)));

    /**
     * 蜂蜡吊坠 —— 带在身上时，被蜜蜂蜇伤不会中毒的遗物。
     *
     * <p>它是本模组第一件「拦下状态效果」的遗物：<b>伤害照常挨</b>，被拿掉的只有蜜蜂顺手
     * 蜇进去的那口毒；而且只认蜜蜂——毒箭、毒土豆、洞穴蜘蛛与药水给的中毒照旧生效。
     * 判定与拦截的时机见
     * {@link org.eternalrelic.capability.carried.BeeswaxPendantEffect}。</p>
     */
    public static final Item BEESWAX_PENDANT = register("beeswax_pendant",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 可怕狼牙吊坠 —— 带在身上时，身边的野狼会被狼王的气息镇住而坐下，喂骨头也更容易让狼认主。
     *
     * <p>两条效果都<b>只对狼生效</b>：别的生物一概照原样。判定分别见
     * {@link org.eternalrelic.capability.carried.WolfAweEffect}（慑服野狼）与
     * {@link org.eternalrelic.capability.carried.WolfTamingEffect}（驯服）。</p>
     */
    public static final Item DREADFUL_WOLF_FANG_PENDANT = register("dreadful_wolf_fang_pendant",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 旅人吊坠 —— 带在身上时，骑乘坐骑赶路更快。
     *
     * <p><b>它是本模组第一件「作用在坐骑身上」的遗物</b>：骑乘时的速度由坐骑自己的移动速度决定，
     * 与骑手的移动速度毫无关系，所以这份加速必须挂到坐骑身上去，不能像其它遗物那样加在玩家身上。
     * 挂上与摘下的时机见
     * {@link org.eternalrelic.capability.carried.TravelerPendantEffect}。</p>
     */
    public static final Item TRAVELER_PENDANT = register("traveler_pendant",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 学者单片眼镜 —— 带在身上时，每次获得经验都额外多给一点。
     *
     * <p><b>它是本模组第一件「改变经验结算」的遗物</b>：经验是游戏自己算出来的一个数字，
     * 因此只能在那一步把数字改掉，见
     * {@link org.eternalrelic.mixin.PlayerEntityMixin}。</p>
     */
    public static final Item SCHOLAR_MONOCLE = register("scholar_monocle",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 铸铁拇指戒 —— 带在身上时，用的工具与武器偶尔不掉耐久。
     *
     * <p><b>它是本模组第一件「改变耐久损耗」的遗物</b>：耐久是在游戏内部扣的，
     * 因此只能在那一步替玩家把这一次拦下来，见
     * {@link org.eternalrelic.mixin.ItemStackDurabilityMixin}。</p>
     */
    public static final Item CAST_IRON_THUMB_RING = register("cast_iron_thumb_ring",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 猎人徽章 —— 带在身上时，击杀生物有机会额外多掉一件战利品。
     *
     * <p><b>它是本模组第一件「改变掉落结果」的遗物</b>：死掉的生物掉什么是游戏照掉落表算出来的，
     * 因此只能守在「跑掉落表」那一步加料，见
     * {@link org.eternalrelic.mixin.LivingEntityMixin}。</p>
     */
    public static final Item HUNTER_BADGE = register("hunter_badge",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 牧羊人铃铛 —— 拿在主手右键摇响，把四十格内的羊叫到身边的遗物。
     *
     * <p><b>它是本模组第一件「让别的生物自己动起来」的遗物</b>：此前所有遗物的作用对象不是玩家
     * 就是物品，这一件改的是羊的行走目标——铃声一响，范围内的羊自己调头朝你走过来。
     * 摇铃不消耗物品，唯一的门槛是那 30 秒冷却，判定见
     * {@link org.eternalrelic.capability.carried.ShepherdBellEffect}。</p>
     */
    public static final Item SHEPHERD_BELL = register("shepherd_bell",
            new ShepherdBellItem(new Item.Settings().maxCount(1)));

    /**
     * 一个圆形的饼 —— 吃下去回到重生点，并给一段快速回血。
     *
     * <p><b>它是本模组第一件消耗品，也是第一件「吃下去才生效」的遗物</b>：效果发生在它消失的
     * 那一刻，因此行为写在 {@link RoundCakeItem} 里，接的是游戏给食物留的「吃完时」那个口子，
     * 不必改动游戏内部代码。</p>
     *
     * <p>填饱肚子的份量直接用原版南瓜派那一份（{@link FoodComponents#PUMPKIN_PIE}），
     * 与原版逐字一致，不另外手写一遍。可堆叠，与南瓜派相同。</p>
     */
    public static final Item ROUND_CAKE = register("round_cake",
            new RoundCakeItem(new Item.Settings().food(FoodComponents.PUMPKIN_PIE)));

    /**
     * 荆棘之誓 —— 带在身上时，把挨打实际掉的那部分血的五分之一扎回给打你的人（最多 20 点）。
     *
     * <p>它<b>不替玩家挡伤害</b>：这一击照常挨，只是让动手的人也要付一点代价。
     * 判定与出手时机见 {@link org.eternalrelic.capability.carried.ThornsOathEffect}。</p>
     */
    public static final Item THORNS_OATH = register("thorns_oath",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 无声软靴 —— 带在身上时，潜行状态下不发出振动，也更不容易被怪物发现。
     *
     * <p>两条效果都要动游戏内部代码（振动源头与索敌倍率），判断集中在
     * {@link org.eternalrelic.capability.carried.SilentBootsEffect}。</p>
     */
    public static final Item SILENT_BOOTS = register("silent_boots",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 拾荒符石 —— 带在身上时，身边三格内的掉落物自己进到身上。
     *
     * <p><b>往哪儿进由口袋决定</b>：身上带着一口装了它的拾荒口袋时，东西优先塞进那口口袋；
     * 没有这样的口袋才照常进玩家背包。判定见
     * {@link org.eternalrelic.capability.carried.ScavengerMagnetEffect}。</p>
     */
    public static final Item SCAVENGER_MAGNET = register("scavenger_magnet",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 拾荒口袋 —— 拿在主手右键打开的一口 54 格便携背包（界面直接用原版大箱子那一套）。
     *
     * <p><b>它是本模组第一件「物品自己装东西」的东西</b>：内容记在口袋自己的数据里，
     * 丢出去、放进箱子、交给别人都跟着走（见 {@link org.eternalrelic.relic.PocketStorage}）。
     * 口袋里放一枚拾荒符石，捡到的东西就会优先往口袋里走。</p>
     */
    public static final Item SCAVENGER_POCKET = register("scavenger_pocket",
            new ScavengerPocketItem(new Item.Settings().maxCount(1)));

    /**
     * 风行披风 —— 缝在胸甲上的一件披风：人在空中还能再蹬一次，并且护住后背。
     *
     * <p><b>它是第一件管到「怎么动」的遗物</b>：此前所有遗物改的都是数值、物品数据或挨打的结果，
     * 这一件改的是玩家在空中的动作。二段跳的判定与服务端出手见
     * {@link org.eternalrelic.capability.attached.WindCloakEffect}，
     * 护后背那一半登记在 {@link ChestGuards 胸甲护具表}（与巡夜斗篷同一路）。</p>
     */
    public static final Item WIND_CLOAK = register("wind_cloak",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 颠倒吊坠 —— 带在身上时，受伤偶尔会反过来：不掉血、改成回血，代价是等量的经验点数。
     *
     * <p>判定见 {@link org.eternalrelic.capability.carried.ReversalPendantEffect}，
     * 出手点与荆棘之誓同一处（护甲与附魔都算完之后）。</p>
     */
    public static final Item REVERSAL_PENDANT = register("reversal_pendant",
            new RelicItem(new Item.Settings().maxCount(1)));

    // ==================== 会报信的四件小东西 ====================

    /**
     * 旧怀表 —— 拿在手上右键，报出此刻的钟点，以及距天黑 / 天亮还有多久。
     *
     * <p>它与磕碰的罗盘是同一路数：自己什么都不改，只把玩家本来要靠估摸的东西报成一个数。
     * 报的是世界时间，因此阴天下雨也照常走针。判定见 {@link PocketWatchItem}。</p>
     */
    public static final Item POCKET_WATCH = register("pocket_watch",
            new PocketWatchItem(new Item.Settings().maxCount(1)));

    /**
     * 气象瓶 —— 拿在手上右键，报出接下来的天气，以及大约还有多久变天。
     *
     * <p><b>它读的是世界自己的天气计时器</b>，因此不是猜：原版的天气到点就翻面，
     * 那个「还有多少刻变天」的数本来就存在。判定见 {@link WeatherBottleItem}。</p>
     */
    public static final Item WEATHER_BOTTLE = register("weather_bottle",
            new WeatherBottleItem(new Item.Settings().maxCount(1)));

    /**
     * 回声螺壳 —— 拿在手上右键，报出四周活物的数量，以及最近那只敌对生物的方向与距离。
     *
     * <p>它只报「有几只、在哪个方向」，不报具体位置：墙后面的东西一样只算个数。
     * 判定见 {@link EchoConchItem}。</p>
     */
    public static final Item ECHO_CONCH = register("echo_conch",
            new EchoConchItem(new Item.Settings().maxCount(1)));

    /**
     * 蜡封手账 —— 蹲下右键记下脚下的地点，平时右键报出那个地点在哪个方向、离多远。
     *
     * <p><b>它记在物品自己身上</b>（与拾荒口袋、装备附件同一条道理）：手账被丢进箱子、
     * 交给队友、带过维度，记下的那一页都跟着它走。判定见 {@link WaxSealedJournalItem}。</p>
     */
    public static final Item WAX_SEALED_JOURNAL = register("wax_sealed_journal",
            new WaxSealedJournalItem(new Item.Settings().maxCount(1)));

    // ==================== 招呼别的东西的两件 ====================

    /**
     * 驯兽哨 —— 拿在手上右键，把 30 格内自己的宠物叫到身边。
     *
     * <p>只认「认你为主」的那些：队友的狗、野生的狼都不会理你；跨维度的也不招呼。
     * 判定见 {@link BeastWhistleItem}。</p>
     */
    public static final Item BEAST_WHISTLE = register("beast_whistle",
            new BeastWhistleItem(new Item.Settings().maxCount(1)));

    /**
     * 回音石 —— 拿在手上右键，把 20 格内的怪物引向自己。
     *
     * <p><b>这是一件双刃剑</b>：它不伤害任何东西，只是让怪当场认定「你在那里」——
     * 引开、聚堆、或者自找麻烦，用法由玩家定。判定见 {@link EchoStoneItem}。</p>
     */
    public static final Item ECHO_STONE = register("echo_stone",
            new EchoStoneItem(new Item.Settings().maxCount(1)));

    // ==================== 踩冰不滑的那一件 ====================

    /**
     * 雪地靴钉 —— 带在身上时，踩在冰面上不再打滑。
     *
     * <p><b>它补的是原版的一处「说不上是缺陷的缺陷」</b>：冰与浮冰的摩擦系数是 0.98
     * （普通地面只有 0.6），踩上去刹不住。判定见
     * {@link org.eternalrelic.capability.carried.SnowGripStudsEffect}，
     * 动手的位置在 {@code mixin/SnowGripStudsMixin}。</p>
     */
    public static final Item SNOW_GRIP_STUDS = register("snow_grip_studs",
            new RelicItem(new Item.Settings().maxCount(1)));

    // ==================== 碎屑档的小东西 ====================

    /**
     * 褪色的护身符 —— 带在身上时生命上限多出半颗心（1 点）。
     */
    public static final Item FADED_CHARM = register("faded_charm",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 磕碰的罗盘 —— 拿在手上右键，在聊天栏报出坐标与朝向。
     *
     * <p>它不给任何数值，只把玩家本来要点 F3 才看得到的两样东西说出来，判定见
     * {@link CrackedCompassItem}。</p>
     */
    public static final Item CRACKED_COMPASS = register("cracked_compass",
            new CrackedCompassItem(new Item.Settings().maxCount(1)));

    /**
     * 干枯的四叶草 —— 带在身上时幸运 +1。
     *
     * <p>幸运是原版属性（{@code generic.luck}），只由服务端在跑掉落表与钓鱼时读取。</p>
     */
    public static final Item DRIED_CLOVER = register("dried_clover",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 戴克森应急制氧球 —— 右键换来五分钟水下呼吸，之后十分钟不能再点。
     *
     * <p>借的是原版「水下呼吸」效果，因此氧气条的表现与游戏各处完全一致，判定见
     * {@link DaiksonOxygenOrbItem}。</p>
     */
    public static final Item DAIKSON_OXYGEN_ORB = register("daikson_oxygen_orb",
            new DaiksonOxygenOrbItem(new Item.Settings().maxCount(1)));

    /**
     * 平凡的自然符石 —— 踩在自然地面（草 / 土 / 沙 / 石 / 雪）上时走得快一点。
     *
     * <p>它是第二件「条件性属性加成」，条件由
     * {@link org.eternalrelic.capability.carried.NaturalRuneEffect} 每 5 刻核对一次。</p>
     */
    public static final Item NATURAL_RUNE = register("natural_rune",
            new RelicItem(new Item.Settings().maxCount(1)));

    // ==================== 缝在武器上的两件 ====================

    /**
     * 古旧弓油 —— 涂在弓弩上，让射出去的箭更有杀伤。
     *
     * <p>只认附着份，且只认弓与弩：箭的伤害与玩家的攻击力无关，
     * 因此它由 {@link org.eternalrelic.capability.attached.BowOilEffect} 配一处注入来加。</p>
     */
    public static final Item OLD_BOW_OIL = register("old_bow_oil",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 古旧剑带 —— 缠在近战武器上，挥砍更有力。
     *
     * <p>只认附着份，且只认剑 / 斧 / 三叉戟。它加的是玩家自己的「攻击力」属性，
     * 因此只要在遗物表里登记一行即可，不必另写能力类。</p>
     */
    public static final Item OLD_SWORD_BAND = register("old_sword_band",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 永恒纹章 —— 钉在任意防具、武器或工具上，使那件东西不再被毁掉，并视同带有经验修补。
     */
    public static final Item ETERNAL_EMBLEM = register("eternal_emblem",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 川流纹章 —— 钉在头盔或靴子上，把那一件护具补成水下专用的。
     *
     * <p>它是第一件「同一条纹章按附着部位给不同附魔」的遗物：头盔补水下呼吸、靴子补深海探索者，
     * 因此只能钉在这两处，钉在别的地方什么也给不了。给哪条见 {@link EnchantingRelics}。</p>
     */
    public static final Item STREAM_EMBLEM = register("stream_emblem",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 太阳纹章 —— 带在身上时，白天持续给「生命恢复」与「力量」。
     *
     * <p>与月亮纹章同属<b>时段纹章</b>：只在世界处于它管的那一段时生效，管哪一段、给哪两条
     * 登记在 {@link DayNightEmblems} 里，实际施加由
     * {@link org.eternalrelic.capability.carried.DayNightEmblemEffect} 负责。
     * <b>放在背包里就生效</b>，也可以缝在装备或盾牌上。</p>
     */
    public static final Item SUN_EMBLEM = register("sun_emblem",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 月亮纹章 —— 带在身上时，夜晚持续给「生命恢复」与「速度」。
     */
    public static final Item MOON_EMBLEM = register("moon_emblem",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 坚铁甲片 —— 缝在防具上的护甲片，为穿着它的人加一点护甲。
     *
     * <p>它是第一件「放在背包里毫无用处」的遗物：必须缝在正穿着的防具上才算数，
     * 见 {@link ModRelics} 里那一行的说明。</p>
     */
    public static final Item HARDENED_IRON_PLATE = register("hardened_iron_plate",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 皮革内衬 —— 缝在防具上的软衬，为穿着它的人加一点盔甲韧性。
     *
     * <p>与坚铁甲片同类：放在背包里毫无用处，必须缝在正穿着的防具上才算数。
     * 它由「皮革内衬（半成品）」烤制而成，见 {@link #LEATHER_LINING_UNFINISHED}。</p>
     */
    public static final Item LEATHER_LINING = register("leather_lining",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 皮革内衬（半成品）—— 加工到一半的内衬，放进熔炉或篝火烤过之后才能缝到防具上。
     *
     * <p>它是普通材料、<b>不是遗物</b>：没有登记进遗物表，也没有任何效果，
     * 因此提示框上不会出现「遗物稀有度」那一行。可以堆叠。</p>
     */
    public static final Item LEATHER_LINING_UNFINISHED = register("leather_lining_unfinished",
            new Item(new Item.Settings()));

    /**
     * 鳞甲内衬 —— 缝着犰狳鳞甲的衬里，为穿着它的人同时加一点盔甲韧性与护甲。
     *
     * <p>与坚铁甲片、皮革内衬同类：放在背包里毫无用处，必须缝在正穿着的防具上才算数。
     * 它是第一件<b>同时给两种属性</b>的遗物，见 {@link ModRelics} 里那一行的说明。</p>
     */
    public static final Item SCUTE_LINING = register("scute_lining",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 龟壳内衬 —— 缝着大块龟壳的衬里，比鳞甲内衬更偏护甲值那一边。
     *
     * <p>与鳞甲内衬同类：放在背包里毫无用处，必须缝在正穿着的防具上才算数。</p>
     */
    public static final Item TURTLE_SHELL_LINING = register("turtle_shell_lining",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 皮革肩甲（左）—— 缝在胸甲上的肩部护片，护住玩家自身的左肩。
     *
     * <p>与坚铁甲片、皮革内衬同类：<b>放在背包里完全没有用</b>，必须缝在正穿着的那件胸甲上才算数，
     * 而且<b>只认胸甲</b>——缝在头盔、护腿或靴子上什么也不给。</p>
     *
     * <p>一对肩甲分左右两只：这一只护左肩，{@link #LEATHER_SHOULDER_GUARD_RIGHT} 护右肩，
     * 两只可以合成 {@link #LEATHER_SHOULDER_GUARD_PAIR 一套}。三件的具体效果见遗物表。</p>
     */
    public static final Item LEATHER_SHOULDER_GUARD_LEFT = register("leather_shoulder_guard_left",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 皮革肩甲（右）—— 与左肩那只成对，护住玩家自身的右肩。
     */
    public static final Item LEATHER_SHOULDER_GUARD_RIGHT = register("leather_shoulder_guard_right",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 一套皮革肩甲 —— 左右两只合成而来的整体，一块顶两只。
     *
     * <p>它护的是<b>两侧肩膀</b>，减伤也比单只更高。做成"合成一套"这条路，
     * 是为了让玩家在胸甲上只占掉<b>一个</b>附着格：胸甲上最多缝六枚遗物，
     * 分缝两只就要占掉两格。</p>
     */
    public static final Item LEATHER_SHOULDER_GUARD_PAIR = register("leather_shoulder_guard_pair",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 鳞片肩甲（左）—— 缝在胸甲上的鳞面护片，护住玩家自身的左肩，比皮革那只更厚。
     *
     * <p>与皮革肩甲同一路数：<b>放在背包里完全没有用</b>，必须缝在正穿着的那件胸甲上，
     * 而且只认胸甲。它由对应的<b>皮革肩甲缝上犰狳鳞甲</b>做成，因此是同一件护具的进阶形态，
     * 减伤与盔甲韧性都比皮革那只高一档（两件的具体差别见遗物表与肩甲表）。</p>
     */
    public static final Item SCUTE_SHOULDER_GUARD_LEFT = register("scute_shoulder_guard_left",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 鳞片肩甲（右）—— 与左肩那只成对，护住玩家自身的右肩。
     */
    public static final Item SCUTE_SHOULDER_GUARD_RIGHT = register("scute_shoulder_guard_right",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 一套鳞片肩甲 —— 左右两只合成而来的整体，两侧肩膀都护。
     *
     * <p>与皮革那套一样，做成一件是为了让玩家在胸甲上只占掉<b>一个</b>附着格；
     * 韧性同样是左右两只相加的结果。</p>
     */
    public static final Item SCUTE_SHOULDER_GUARD_PAIR = register("scute_shoulder_guard_pair",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 龟壳肩甲（左）—— 缝在胸甲上的龟壳护片，护住玩家自身的左肩。
     *
     * <p>与皮革、鳞片两档同一路数：<b>放在背包里完全没有用</b>，必须缝在正穿着的那件胸甲上，
     * 而且只认胸甲。打在左肩上的箭有几率被整个弹开——会弹的两档里，它是概率较低的那一档
     * （10%；铁片那档是 20%）。</p>
     *
     * <p>它由<b>皮革肩甲（左）缝上三块海龟壳</b>做成，与鳞片肩甲并列成两条进阶路线：
     * 鳞片那条给的盔甲韧性更高，龟壳这条韧性低一些，换来一手弹开远程的本事。</p>
     */
    public static final Item TURTLE_SHELL_SHOULDER_GUARD_LEFT = register("turtle_shell_shoulder_guard_left",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 龟壳肩甲（右）—— 与左肩那只成对，护住玩家自身的右肩。
     */
    public static final Item TURTLE_SHELL_SHOULDER_GUARD_RIGHT = register("turtle_shell_shoulder_guard_right",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 一套龟壳肩甲 —— 左右两只合成而来的整体，两侧肩膀都护，两侧的箭都可能被弹开。
     *
     * <p>与前两档一样，做成一件是为了让玩家在胸甲上只占掉<b>一个</b>附着格；
     * 韧性同样是左右两只相加的结果。</p>
     */
    public static final Item TURTLE_SHELL_SHOULDER_GUARD_PAIR = register("turtle_shell_shoulder_guard_pair",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 铁片肩甲（左）—— 缝在胸甲上的铁板护片，护住玩家自身的左肩。
     *
     * <p>与前三档肩甲同一路数：<b>放在背包里完全没有用</b>，必须缝在正穿着的那件胸甲上，
     * 而且只认胸甲。它是四档里最重的一件：打在护着那一侧的远程攻击有<b>两成</b>的几率被整个弹开
     * （龟壳那档只有一成），代价是每缝一枚都让<b>挥砍慢 4%</b>——好处与代价都登记在
     * {@code registry/ChestGuards} 与遗物表里，这个类只管注册物品。</p>
     */
    public static final Item IRON_SHOULDER_GUARD_LEFT = register("iron_shoulder_guard_left",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 铁片肩甲（右）—— 与左肩那只成对，护住玩家自身的右肩。
     */
    public static final Item IRON_SHOULDER_GUARD_RIGHT = register("iron_shoulder_guard_right",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 一套铁片肩甲 —— 左右两只合成而来的整体，两侧肩膀都护，两侧的箭都可能被弹开。
     *
     * <p>与前三档一样，做成一件是为了让玩家在胸甲上只占掉<b>一个</b>附着格。
     * 挥砍的代价也<b>只算一枚</b>（4%），比左右各缝一只（合计 8%）轻——这正是合成一套的意义。</p>
     */
    public static final Item IRON_SHOULDER_GUARD_PAIR = register("iron_shoulder_guard_pair",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 铜片肩甲（左）—— 缝在胸甲上的铜板护片，护住玩家自身的左肩。
     *
     * <p>与铁片肩甲同一路数：<b>放在背包里完全没有用</b>，必须缝在正穿着的那件胸甲上，
     * 而且只认胸甲。它是四档里<b>最薄的一件</b>（打在护着那一侧时只少掉 1.5 点，皮革那只还有 2 点），
     * 换来的是不用等好材料就能拨箭：远程打中那一侧时有<b>一成半</b>的几率被整个弹开。
     * 代价与铁片相同——每缝一枚挥砍慢 4%，登记在遗物表里。</p>
     */
    public static final Item COPPER_SHOULDER_GUARD_LEFT = register("copper_shoulder_guard_left",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 铜片肩甲（右）—— 与左肩那只成对，护住玩家自身的右肩。
     */
    public static final Item COPPER_SHOULDER_GUARD_RIGHT = register("copper_shoulder_guard_right",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 一套铜片肩甲 —— 左右两只合成而来的整体，两侧肩膀都护，两侧的箭都可能被弹开。
     *
     * <p>与其它几档一样，做成一件是为了让玩家在胸甲上只占掉<b>一个</b>附着格；
     * 挥砍的代价也只算一枚（4%）。</p>
     */
    public static final Item COPPER_SHOULDER_GUARD_PAIR = register("copper_shoulder_guard_pair",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 秘银胸甲片 —— 贴在胸甲正面的一块圆形甲片，护住正胸。
     *
     * <p>它是本模组第一件<b>胸甲片</b>：与肩甲、内衬、甲片是并列的配件类别，
     * 因此它可以与肩甲同时缝在一件胸甲上，但<b>一件胸甲上只能有一片胸甲片</b>
     * （见 {@code relic/FittingCategory}）。</p>
     *
     * <p>它护的是<b>正胸</b>——背后挨的刀落在后背那一块上，它一点都挡不住，弹开远程
     * 也只对正面射来的箭有效。另一手是替玩家挡下魔法伤害，那一条不看部位。
     * 具体数值见 {@code registry/ChestGuards} 与遗物表。</p>
     */
    public static final Item MITHRIL_CHESTPLATE_PLATE = register("mithril_chestplate_plate",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 铜胸甲片 —— 最普通的一片胸甲片，护住正胸。
     *
     * <p>与秘银胸甲片同属「胸甲片」这一类，因此一件胸甲上只能二选一。它的本事只有秘银那一片的
     * 一半：减得少、弹开概率也低，而且<b>不挡魔法、不给盔甲韧性</b>；代价是穿着走路略慢
     * （那一条登记在遗物表里）。</p>
     */
    public static final Item COPPER_CHESTPLATE_PLATE = register("copper_chestplate_plate",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 巡夜斗篷 —— 披在胸甲外的一件乌黑斗篷，夜里让人保持清醒。
     *
     * <p><b>它的三样本事分属三条路，因此这里只登记身份与成色</b>：夜里（且所处亮度低于 7）的
     * 移速加成、缝在胸甲上时给的盔甲韧性，两样都由
     * {@link org.eternalrelic.capability.carried.NightWatchCloakEffect} 按条件挂上挂下；
     * 「后背挨打少掉 1 点」那一条登记在 {@code registry/ChestGuards} 胸甲护具表里。</p>
     *
     * <p>它是全项目第一件<b>条件性属性加成</b>：带着它不一定立刻见效，得等天黑。</p>
     */
    public static final Item NIGHTWATCH_CLOAK = register("nightwatch_cloak",
            new RelicItem(new Item.Settings().maxCount(1)));

    /**
     * 犰狳鳞甲 —— 做鳞甲内衬用的硬鳞片。
     *
     * <p>它是普通材料、<b>不是遗物</b>：没有登记进遗物表，也没有任何效果，
     * 因此提示框上不会出现「遗物稀有度」那一行。可以堆叠。</p>
     *
     * <p>⚠️ 本模组做在 1.20.1 上，而这个版本的游戏里<b>还没有犰狳这种生物</b>，
     * 因此这件材料目前没有正经来源，只能用指令取得。日后犰狳真的进了这个版本
     * （或另定了别的来源），再给它补掉落或配方。</p>
     */
    public static final Item ARMADILLO_SCUTE = register("armadillo_scute",
            new Item(new Item.Settings()));

    // ==================== 锤子（武器 + 装卸台的合成材料） ====================

    /** 两把锤子的攻击速度修正。玩家的基础攻速是 4.0，因此 4.0 - 3.4 = 0.6。 */
    private static final float HAMMER_ATTACK_SPEED = -3.4F;

    /** 两把锤子的耐久。原版工具都是在注册时用 maxDamage 指定的，不走材料那一套。 */
    private static final int HAMMER_DURABILITY = 100;

    /**
     * 铁锤 —— 大的那把。既是武器，也是制作遗物装卸台的材料。
     *
     * <p>伤害 8：玩家基础 1 + 石材的 1 + 传入的 6。比石斧略低一档，攻速 0.6 明显偏慢——
     * 它是一件抡起来砸的东西。</p>
     */
    public static final Item HAMMER = register("hammer",
            new SwordItem(ToolMaterials.STONE, 6, HAMMER_ATTACK_SPEED,
                    new Item.Settings().maxDamage(HAMMER_DURABILITY)));

    /**
     * 小铁锤 —— 小的那把。伤害 4（基础 1 + 木材的 0 + 传入的 3），攻速与耐久同大锤。
     */
    public static final Item SMALL_HAMMER = register("small_hammer",
            new SwordItem(ToolMaterials.WOOD, 3, HAMMER_ATTACK_SPEED,
                    new Item.Settings().maxDamage(HAMMER_DURABILITY)));

    // ==================== 品阶样本（测试用） ====================

    /**
     * 品阶样本 —— 七件没有任何效果的测试遗物，各自对应一个材料档位。
     *
     * <p>它们只用来检查遗物界面在七个档位下的表现（面板样式与品质配色），不具备任何实际功能，
     * 正式发布前应连同贴图、模型与语言条目一并移除。</p>
     */
    public static final Item RELIC_SAMPLE_DEBRIS = register("relic_sample_debris",
            new RelicItem(new Item.Settings()));

    /** 品阶样本·粗石。 */
    public static final Item RELIC_SAMPLE_ROUGH = register("relic_sample_rough",
            new RelicItem(new Item.Settings()));

    /** 品阶样本·成材。 */
    public static final Item RELIC_SAMPLE_LUMBER = register("relic_sample_lumber",
            new RelicItem(new Item.Settings()));

    /** 品阶样本·精萃。 */
    public static final Item RELIC_SAMPLE_ESSENCE = register("relic_sample_essence",
            new RelicItem(new Item.Settings()));

    /** 品阶样本·珍品。 */
    public static final Item RELIC_SAMPLE_TREASURE = register("relic_sample_treasure",
            new RelicItem(new Item.Settings()));

    /** 品阶样本·至宝。 */
    public static final Item RELIC_SAMPLE_SUPREME = register("relic_sample_supreme",
            new RelicItem(new Item.Settings()));

    /** 品阶样本·源质。 */
    public static final Item RELIC_SAMPLE_SOURCE = register("relic_sample_source",
            new RelicItem(new Item.Settings()));

    /**
     * 守夜之瞳·左眼 —— 右键装入左眼，在低光环境下看清周围。
     */
    public static final Item NIGHTWATCH_EYE_LEFT = register("nightwatch_eye_left",
            new NightwatchEyeItem(new Item.Settings().maxCount(1), NightwatchEye.LEFT, false));

    /**
     * 守夜之瞳·右眼 —— 右键装入右眼，在低光环境下照见附近的活物。
     */
    public static final Item NIGHTWATCH_EYE_RIGHT = register("nightwatch_eye_right",
            new NightwatchEyeItem(new Item.Settings().maxCount(1), NightwatchEye.RIGHT, false));

    /**
     * 守夜之瞳·左眼（耗尽）—— 被取下后能量耗尽，需与附魔之瓶合成才能恢复原样。
     */
    public static final Item NIGHTWATCH_EYE_LEFT_DRAINED = register("nightwatch_eye_left_drained",
            new NightwatchEyeItem(new Item.Settings().maxCount(1), NightwatchEye.LEFT, true));

    /**
     * 守夜之瞳·右眼（耗尽）—— 被取下后能量耗尽，需与附魔之瓶合成才能恢复原样。
     */
    public static final Item NIGHTWATCH_EYE_RIGHT_DRAINED = register("nightwatch_eye_right_drained",
            new NightwatchEyeItem(new Item.Settings().maxCount(1), NightwatchEye.RIGHT, true));

    private ModItems() {
    }

    /**
     * 由 {@link EternalRelic#onInitialize()} 调用，触发本类静态字段初始化并完成物品注册。
     */
    public static void register() {
        ModRelics.register();
        AttachableRelics.register();
        ChestGuards.register();
        DayNightEmblems.register();
        EnchantingRelics.register();
        VanillaMaterialRarities.register();
        VanillaItemGrades.register();

        // 六个分类：图标各挑一件代表物；显示名走语言文件，键名由分类 id 拼出来（见 registerGroup）
        registerGroup(PENDANT_GROUP_KEY, TRAVELER_PENDANT);
        registerGroup(EMBLEM_GROUP_KEY, COURAGE_EMBLEM);
        registerGroup(ARMOR_FITTING_GROUP_KEY, MITHRIL_CHESTPLATE_PLATE);
        registerGroup(VESSEL_GROUP_KEY, SOUL_LANTERN);
        registerGroup(CONSUMABLE_GROUP_KEY, ROUND_CAKE);
        registerGroup(MATERIAL_GROUP_KEY, HAMMER);

        // ==================== 装备配件（缝在装备与武器上）====================
        // 排序：先按小类聚拢（甲片 → 内衬 → 肩甲 → 胸甲片 → 斗篷 → 武器涂装），
        // 每个小类内部再按稀有度从低到高；肩甲内部按 左 / 右 / 整套 挨着排
        ItemGroupEvents.modifyEntriesEvent(ARMOR_FITTING_GROUP_KEY).register(entries -> {
            // 甲片
            entries.add(MOTTLED_COPPER_PLATE);             // 斑驳的铜甲片（粗石）
            entries.add(HARDENED_IRON_PLATE);              // 坚铁甲片（成材）

            // 内衬
            entries.add(LEATHER_LINING);                   // 皮革内衬（粗石）
            entries.add(SCUTE_LINING);                     // 鳞甲内衬（成材）
            entries.add(TURTLE_SHELL_LINING);              // 龟壳内衬（成材）

            // 肩甲：皮革（粗石）→ 鳞片 → 龟壳 → 铁片 → 铜片（成材）
            entries.add(LEATHER_SHOULDER_GUARD_LEFT);
            entries.add(LEATHER_SHOULDER_GUARD_RIGHT);
            entries.add(LEATHER_SHOULDER_GUARD_PAIR);
            entries.add(SCUTE_SHOULDER_GUARD_LEFT);
            entries.add(SCUTE_SHOULDER_GUARD_RIGHT);
            entries.add(SCUTE_SHOULDER_GUARD_PAIR);
            entries.add(TURTLE_SHELL_SHOULDER_GUARD_LEFT);
            entries.add(TURTLE_SHELL_SHOULDER_GUARD_RIGHT);
            entries.add(TURTLE_SHELL_SHOULDER_GUARD_PAIR);
            entries.add(IRON_SHOULDER_GUARD_LEFT);
            entries.add(IRON_SHOULDER_GUARD_RIGHT);
            entries.add(IRON_SHOULDER_GUARD_PAIR);
            entries.add(COPPER_SHOULDER_GUARD_LEFT);
            entries.add(COPPER_SHOULDER_GUARD_RIGHT);
            entries.add(COPPER_SHOULDER_GUARD_PAIR);

            // 胸甲片
            entries.add(COPPER_CHESTPLATE_PLATE);          // 铜胸甲片（碎屑）
            entries.add(MITHRIL_CHESTPLATE_PLATE);         // 秘银胸甲片（珍品）

            // 斗篷
            entries.add(NIGHTWATCH_CLOAK);                 // 巡夜斗篷（成材）
            entries.add(WIND_CLOAK);                       // 风行披风（精萃）

            // 武器涂装
            entries.add(OLD_BOW_OIL);                      // 古旧弓油（碎屑，涂在弓弩上）
            entries.add(OLD_SWORD_BAND);                   // 古旧剑带（碎屑，缠在近战武器上）
        });

        // ==================== 佩饰（挂在身上、随身携带的小件）====================
        // 排序：先按小类聚拢（吊坠 → 护符 → 符石 → 戒指 → 眼镜 → 徽章 → 草药），
        // 每个小类内部再按稀有度从低到高
        ItemGroupEvents.modifyEntriesEvent(PENDANT_GROUP_KEY).register(entries -> {
            // 吊坠
            entries.add(BEESWAX_PENDANT);            // 蜂蜡吊坠（碎屑）
            entries.add(TRAVELER_PENDANT);           // 旅人吊坠（粗石）
            entries.add(DREADFUL_WOLF_FANG_PENDANT); // 可怕狼牙吊坠（粗石）
            entries.add(REVERSAL_PENDANT);           // 颠倒吊坠（粗石）

            // 护符
            entries.add(FADED_CHARM);                // 褪色的护身符（碎屑）

            // 符石
            entries.add(NATURAL_RUNE);               // 平凡的自然符石（碎屑）
            entries.add(SCAVENGER_MAGNET);           // 拾荒符石（碎屑）

            // 戒指
            entries.add(CAST_IRON_THUMB_RING);       // 铸铁拇指戒（粗石）

            // 眼镜
            entries.add(SCHOLAR_MONOCLE);            // 学者单片眼镜（粗石）

            // 徽章
            entries.add(HUNTER_BADGE);               // 猎人徽章（粗石）

            // 草药
            entries.add(DRIED_CLOVER);               // 干枯的四叶草（碎屑）
        });

        // ==================== 纹章（可以叠着钉在装备与武器上）====================
        ItemGroupEvents.modifyEntriesEvent(EMBLEM_GROUP_KEY).register(entries -> {
            // 成材
            entries.add(COURAGE_EMBLEM);         // 勇气纹章
            entries.add(SUN_EMBLEM);             // 太阳纹章
            entries.add(MOON_EMBLEM);            // 月亮纹章
            entries.add(STREAM_EMBLEM);          // 川流纹章

            // 至宝
            entries.add(ETERNAL_EMBLEM);         // 永恒纹章
        });

        // ==================== 器物（随身带着的各种器物）====================
        // 排序：先按小类聚拢（报信小物 → 收纳与照明 → 护身器物 → 义眼 → 招呼生物 →
        // 身上小件 → 装置），每个小类内部再按稀有度从低到高
        ItemGroupEvents.modifyEntriesEvent(VESSEL_GROUP_KEY).register(entries -> {
            // 报信小物：看一眼就收起来的那几件
            entries.add(CRACKED_COMPASS);        // 磕碰的罗盘（碎屑）
            entries.add(POCKET_WATCH);           // 旧怀表（碎屑）
            entries.add(WEATHER_BOTTLE);         // 气象瓶（碎屑）
            entries.add(ECHO_CONCH);             // 回声螺壳（碎屑）
            entries.add(WAX_SEALED_JOURNAL);     // 蜡封手账（碎屑）

            // 收纳与照明
            entries.add(SCAVENGER_POCKET);       // 拾荒口袋（碎屑）
            entries.add(SOUL_LANTERN);           // 引魂燃灯（珍品）

            // 护身器物
            entries.add(AOTA_BRANCH);            // 奥塔的枝叶（珍品）
            entries.add(ECHO_RING);              // 回响之环（珍品）
            entries.add(ECHO_RING_DRAINED);      // 回响之环（碎裂）
            entries.add(THORNS_OATH);            // 荆棘之誓（珍品）

            // 义眼
            entries.add(NIGHTWATCH_EYE_LEFT);           // 守夜之瞳·左眼（精萃）
            entries.add(NIGHTWATCH_EYE_RIGHT);          // 守夜之瞳·右眼（精萃）
            entries.add(NIGHTWATCH_EYE_LEFT_DRAINED);   // 左眼（耗尽）
            entries.add(NIGHTWATCH_EYE_RIGHT_DRAINED);  // 右眼（耗尽）

            // 招呼生物
            entries.add(SHEPHERD_BELL);          // 牧羊人铃铛（粗石）
            entries.add(BEAST_WHISTLE);          // 驯兽哨（粗石）
            entries.add(ECHO_STONE);             // 回音石（粗石）

            // 身上小件
            entries.add(SNOW_GRIP_STUDS);        // 雪地靴钉（碎屑）
            entries.add(ENCHANTED_RABBIT_FOOT);  // 附魔兔脚（粗石）
            entries.add(SILENT_BOOTS);           // 无声软靴（粗石）

            // 装置
            entries.add(DAIKSON_OXYGEN_ORB);     // 戴克森应急制氧球（成材）
        });

        // ==================== 消耗品（用掉就没的东西）====================
        // ⚠️ 目前只有一件。以后做吃的、一次性用的遗物都往这一页加
        ItemGroupEvents.modifyEntriesEvent(CONSUMABLE_GROUP_KEY).register(entries -> {
            entries.add(ROUND_CAKE);             // 一个圆形的饼
        });

        // ==================== 材料与工具 ====================
        ItemGroupEvents.modifyEntriesEvent(MATERIAL_GROUP_KEY).register(entries -> {
            // 材料
            entries.add(LEATHER_LINING_UNFINISHED);          // 皮革内衬（半成品）
            entries.add(ARMADILLO_SCUTE);                    // 犰狳鳞甲

            // 工具
            entries.add(HAMMER);                             // 铁锤
            entries.add(SMALL_HAMMER);                       // 小铁锤

            // 工作方块
            entries.add(ModBlocks.RELIC_STATION_ITEM);       // 遗物装卸台
            entries.add(ModBlocks.CHESTPLATE_STATION_ITEM);  // 胸甲台

            // 四个埋藏块：长得和原版方块一样，拿刷子刷能刷出东西
            entries.add(ModBlocks.RELIC_GRASS_ITEM);
            entries.add(ModBlocks.RELIC_SAND_ITEM);
            entries.add(ModBlocks.RELIC_GRAVEL_ITEM);
            entries.add(ModBlocks.RELIC_PODZOL_ITEM);

            // 裂石砖的台阶与楼梯：1.20.1 原版没有这对，遗迹做旧要用
            entries.add(ModBlocks.CRACKED_STONE_BRICK_SLAB_ITEM);
            entries.add(ModBlocks.CRACKED_STONE_BRICK_STAIRS_ITEM);

            // ⚠️ 品阶样本：测试用。正式发布前要连同物品注册、贴图与语言条目一起删掉
            entries.add(RELIC_SAMPLE_DEBRIS);
            entries.add(RELIC_SAMPLE_ROUGH);
            entries.add(RELIC_SAMPLE_LUMBER);
            entries.add(RELIC_SAMPLE_ESSENCE);
            entries.add(RELIC_SAMPLE_TREASURE);
            entries.add(RELIC_SAMPLE_SUPREME);
            entries.add(RELIC_SAMPLE_SOURCE);
        });
    }

    /**
     * 拼出一个创造模式分类的注册名。
     *
     * @param name 分类的短名（同时也是语言键 {@code itemGroup.eternal_relic.<name>} 的后半段）
     * @return 该分类的注册名
     */
    private static RegistryKey<ItemGroup> relicGroup(String name) {
        return RegistryKey.of(RegistryKeys.ITEM_GROUP, EternalRelic.id(name));
    }

    /**
     * 注册一个创造模式分类 —— 图标用一件代表物，显示名由分类 id 到语言文件里取。
     *
     * @param key  分类的注册名
     * @param icon 这一页的代表物，拿来当图标
     */
    private static void registerGroup(RegistryKey<ItemGroup> key, Item icon) {
        Registry.register(Registries.ITEM_GROUP, key, FabricItemGroup.builder()
                .icon(() -> new ItemStack(icon))
                .displayName(Text.translatable("itemGroup.eternal_relic." + key.getValue().getPath()))
                .build());
    }

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, EternalRelic.id(name), item);
    }
}
