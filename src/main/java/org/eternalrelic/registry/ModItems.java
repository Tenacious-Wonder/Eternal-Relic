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
import org.eternalrelic.item.EchoRingItem;
import org.eternalrelic.item.EnchantedRabbitFootItem;
import org.eternalrelic.item.NightwatchEyeItem;
import org.eternalrelic.item.RelicItem;
import org.eternalrelic.item.RoundCakeItem;
import org.eternalrelic.item.ShepherdBellItem;
import org.eternalrelic.item.SoulLanternItem;
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

    /** 本模组在创造模式物品栏中的专属分类的注册名。 */
    private static final RegistryKey<ItemGroup> RELIC_GROUP_KEY =
            RegistryKey.of(RegistryKeys.ITEM_GROUP, EternalRelic.id("relic_group"));

    /**
     * 盔甲配件在创造模式物品栏里独占的那一页。
     *
     * <p>肩甲、内衬、甲片、胸甲片都属于「装备配件」——它们能缝在装备上，而且同类只能缝一件
     * （见 {@link org.eternalrelic.relic.FittingCategory}）。数量已经有二十二件，
     * 再混在遗物那一页里会越来越难找，因此单开一页。</p>
     *
     * <p><b>纹章一类不在这里</b>：它们虽然也能钉在装备上，但不属于任何配件类别，
     * 是本模组的「遗物」，仍旧留在遗物那一页。</p>
     */
    private static final RegistryKey<ItemGroup> ARMOR_FITTING_GROUP_KEY =
            RegistryKey.of(RegistryKeys.ITEM_GROUP, EternalRelic.id("armor_fitting_group"));

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

        Registry.register(Registries.ITEM_GROUP, RELIC_GROUP_KEY, FabricItemGroup.builder()
                .icon(() -> new ItemStack(AOTA_BRANCH))
                .displayName(Text.translatable("itemGroup.eternal_relic.relic_group"))
                .build());

        Registry.register(Registries.ITEM_GROUP, ARMOR_FITTING_GROUP_KEY, FabricItemGroup.builder()
                .icon(() -> new ItemStack(MITHRIL_CHESTPLATE_PLATE))
                .displayName(Text.translatable("itemGroup.eternal_relic.armor_fitting_group"))
                .build());

        // 盔甲配件那一页：按「甲片 → 内衬 → 肩甲（由薄到厚）→ 胸甲片」的顺序摆，
        // 同一档的左 / 右 / 一套连在一起，翻起来一眼看得出有几档、每档有哪三件
        ItemGroupEvents.modifyEntriesEvent(ARMOR_FITTING_GROUP_KEY).register(entries -> {
            // 甲片：缝在四个部位防具上的金属片
            entries.add(MOTTLED_COPPER_PLATE);
            entries.add(HARDENED_IRON_PLATE);

            // 内衬：缝在防具内侧的衬里
            entries.add(LEATHER_LINING);
            entries.add(SCUTE_LINING);
            entries.add(TURTLE_SHELL_LINING);

            // 肩甲：皮革 → 鳞片 → 龟壳 → 铁片 → 铜片
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

            // 胸甲片：贴在胸甲正面的圆甲片
            entries.add(COPPER_CHESTPLATE_PLATE);
            entries.add(MITHRIL_CHESTPLATE_PLATE);
            entries.add(NIGHTWATCH_CLOAK);
        });

        ItemGroupEvents.modifyEntriesEvent(RELIC_GROUP_KEY).register(entries -> {
            entries.add(ModBlocks.RELIC_STATION_ITEM);
            entries.add(ModBlocks.CHESTPLATE_STATION_ITEM);
            entries.add(HAMMER);
            entries.add(SMALL_HAMMER);
            entries.add(AOTA_BRANCH);
            entries.add(ECHO_RING);
            entries.add(ECHO_RING_DRAINED);
            entries.add(SOUL_LANTERN);
            entries.add(COURAGE_EMBLEM);
            entries.add(ENCHANTED_RABBIT_FOOT);
            entries.add(BEESWAX_PENDANT);
            entries.add(DREADFUL_WOLF_FANG_PENDANT);
            entries.add(TRAVELER_PENDANT);
            entries.add(SCHOLAR_MONOCLE);
            entries.add(CAST_IRON_THUMB_RING);
            entries.add(HUNTER_BADGE);
            entries.add(SHEPHERD_BELL);
            entries.add(ROUND_CAKE);
            // 巡夜斗篷：它放在背包里就有用（夜里的移速），所以与遗物并排放在这一页；
            // 同时它也是一个「胸甲护具」，在「盔甲配件」那一页另有一份
            entries.add(NIGHTWATCH_CLOAK);
            entries.add(ETERNAL_EMBLEM);
            entries.add(STREAM_EMBLEM);
            entries.add(SUN_EMBLEM);
            entries.add(MOON_EMBLEM);
            // 盔甲配件（甲片 / 内衬 / 肩甲 / 胸甲片）已挪到专属分类「盔甲配件」那一页。
            // 半成品皮革内衬是材料、不是配件，仍旧留在这里
            entries.add(LEATHER_LINING_UNFINISHED);
            entries.add(ARMADILLO_SCUTE);
            entries.add(NIGHTWATCH_EYE_LEFT);
            entries.add(NIGHTWATCH_EYE_RIGHT);
            entries.add(NIGHTWATCH_EYE_LEFT_DRAINED);
            entries.add(NIGHTWATCH_EYE_RIGHT_DRAINED);

            entries.add(RELIC_SAMPLE_DEBRIS);
            entries.add(RELIC_SAMPLE_ROUGH);
            entries.add(RELIC_SAMPLE_LUMBER);
            entries.add(RELIC_SAMPLE_ESSENCE);
            entries.add(RELIC_SAMPLE_TREASURE);
            entries.add(RELIC_SAMPLE_SUPREME);
            entries.add(RELIC_SAMPLE_SOURCE);
        });
    }

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, EternalRelic.id(name), item);
    }
}
