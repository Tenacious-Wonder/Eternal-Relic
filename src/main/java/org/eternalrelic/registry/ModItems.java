package org.eternalrelic.registry;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
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
     * 而且只认胸甲。它是三档里唯一会动远程的那一件——打在左肩上的箭有几率被整个弹开。</p>
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
        ShoulderGuards.register();
        DayNightEmblems.register();
        EnchantingRelics.register();
        VanillaMaterialRarities.register();
        VanillaItemGrades.register();

        Registry.register(Registries.ITEM_GROUP, RELIC_GROUP_KEY, FabricItemGroup.builder()
                .icon(() -> new ItemStack(AOTA_BRANCH))
                .displayName(Text.translatable("itemGroup.eternal_relic.relic_group"))
                .build());

        ItemGroupEvents.modifyEntriesEvent(RELIC_GROUP_KEY).register(entries -> {
            entries.add(ModBlocks.RELIC_STATION_ITEM);
            entries.add(HAMMER);
            entries.add(SMALL_HAMMER);
            entries.add(AOTA_BRANCH);
            entries.add(ECHO_RING);
            entries.add(ECHO_RING_DRAINED);
            entries.add(SOUL_LANTERN);
            entries.add(MOTTLED_COPPER_PLATE);
            entries.add(COURAGE_EMBLEM);
            entries.add(ENCHANTED_RABBIT_FOOT);
            entries.add(BEESWAX_PENDANT);
            entries.add(DREADFUL_WOLF_FANG_PENDANT);
            entries.add(ETERNAL_EMBLEM);
            entries.add(STREAM_EMBLEM);
            entries.add(SUN_EMBLEM);
            entries.add(MOON_EMBLEM);
            entries.add(HARDENED_IRON_PLATE);
            entries.add(LEATHER_LINING);
            entries.add(LEATHER_LINING_UNFINISHED);
            entries.add(SCUTE_LINING);
            entries.add(TURTLE_SHELL_LINING);
            entries.add(LEATHER_SHOULDER_GUARD_LEFT);
            entries.add(LEATHER_SHOULDER_GUARD_RIGHT);
            entries.add(LEATHER_SHOULDER_GUARD_PAIR);
            entries.add(SCUTE_SHOULDER_GUARD_LEFT);
            entries.add(SCUTE_SHOULDER_GUARD_RIGHT);
            entries.add(SCUTE_SHOULDER_GUARD_PAIR);
            entries.add(TURTLE_SHELL_SHOULDER_GUARD_LEFT);
            entries.add(TURTLE_SHELL_SHOULDER_GUARD_RIGHT);
            entries.add(TURTLE_SHELL_SHOULDER_GUARD_PAIR);
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
