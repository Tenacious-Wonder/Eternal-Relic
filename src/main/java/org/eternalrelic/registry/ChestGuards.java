package org.eternalrelic.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

import org.eternalrelic.bodypart.BodyPart;
import org.eternalrelic.relic.ChestGuard;

/**
 * <b>胸甲护具表</b> —— 缝在胸甲上的护具，各护住哪些部位、挨打时少掉几点伤害、
 * 能不能弹开远程攻击、以及替玩家挡下几点魔法伤害。
 *
 * <p>这是一份**由制作者选定的白名单**：一件遗物没登记在这里，就是没有「护具」这项本事，
 * 这是正常状态，不是漏写。想让某件遗物成为护具，在这里加一行即可，不必改能力类。</p>
 *
 * <p><b>三类东西共用这张表，因为它们在游戏里做的是同一件事</b>：</p>
 * <ul>
 *   <li><b>肩甲</b>（皮革 / 鳞片 / 龟壳 / 铁片 / 铜片，各分左、右、一套）—— 护左右两侧肩膀；</li>
 *   <li><b>胸甲片</b>（秘银 / 铜）—— 护正胸，只认前半，后背不算；</li>
 *   <li><b>斗篷</b>（巡夜斗篷）—— 护后背。</li>
 * </ul>
 *
 * <p>它们互不重叠，可以同时缝在一件胸甲上，挨打时<b>各算各的</b>。至于「哪几件不能同时缝」，
 * 那是配件类别（{@code relic/FittingCategory}）管的事，与本表无关。</p>
 *
 * <p>护住同一块的多件护具之间<b>取最狠的那一件，不相加</b>——否则几件一叠就能把轻击整个抹掉，
 * 护具会从「一件装备」变成「一堵墙」。这条规矩不在这张表里，由
 * {@code capability.attached.ChestGuardEffect} 在算的时候执行。</p>
 *
 * <p>与 {@code DayNightEmblems} 同一套做法：表在这里，怎么生效由那个能力类负责。
 * 日后要做「护腿片」「头盔片」，同样加一行即可——护住哪个部位由登记时写明的部位决定。</p>
 */
public final class ChestGuards {

    /** 皮革肩甲挨打时少掉的伤害点数。 */
    private static final float LEATHER_REDUCTION = 2.0F;

    /** 铜片肩甲与铜胸甲片挨打时少掉的伤害点数——最薄的一档。 */
    private static final float COPPER_REDUCTION = 1.5F;

    /** 鳞片肩甲、龟壳肩甲与铁片肩甲挨打时少掉的伤害点数——比皮革那只厚。 */
    private static final float HEAVY_REDUCTION = 2.5F;

    /** 秘银胸甲片挨打时少掉的伤害点数——全表最厚。 */
    private static final float MITHRIL_REDUCTION = 3.0F;

    /** 巡夜斗篷替玩家挡下的后背伤害点数。 */
    private static final float CLOAK_REDUCTION = 1.0F;

    /** 铜片肩甲与铜胸甲片把远程攻击弹开的概率。 */
    private static final float COPPER_DEFLECT_CHANCE = 0.15F;

    /** 龟壳肩甲把远程攻击弹开的概率。 */
    private static final float TURTLE_DEFLECT_CHANCE = 0.1F;

    /** 铁片肩甲把远程攻击弹开的概率——是龟壳那件的两倍。 */
    private static final float IRON_DEFLECT_CHANCE = 0.2F;

    /** 秘银胸甲片把远程攻击弹开的概率——比任何一档肩甲都高。 */
    private static final float MITHRIL_DEFLECT_CHANCE = 0.3F;

    /** 秘银胸甲片替玩家挡下的魔法伤害点数。 */
    private static final float MITHRIL_MAGIC_REDUCTION = 1.0F;

    /** 不会弹开远程攻击的护具填这个值。 */
    private static final float NO_DEFLECT = 0.0F;

    /** 不替玩家挡魔法伤害的护具填这个值。 */
    private static final float NO_MAGIC_REDUCTION = 0.0F;

    /** 全部已登记的护具，按登记顺序。 */
    private static final Map<Item, ChestGuard> BY_ITEM = new LinkedHashMap<>();

    private ChestGuards() {
    }

    /**
     * 由 {@link ModItems#register()} 调用，触发本类静态内容初始化（把全部护具登记进表里）。
     */
    static void register() {
        // ---- 肩甲：护住左右两侧肩膀，一套两侧都护。由薄到厚排 ----

        // 皮革肩甲：最基础的一档，只减伤、不弹远程
        register(ModItems.LEATHER_SHOULDER_GUARD_LEFT, LEATHER_REDUCTION, NO_DEFLECT,
                NO_MAGIC_REDUCTION, BodyPart.LEFT_SHOULDER);
        register(ModItems.LEATHER_SHOULDER_GUARD_RIGHT, LEATHER_REDUCTION, NO_DEFLECT,
                NO_MAGIC_REDUCTION, BodyPart.RIGHT_SHOULDER);
        register(ModItems.LEATHER_SHOULDER_GUARD_PAIR, LEATHER_REDUCTION, NO_DEFLECT,
                NO_MAGIC_REDUCTION, BodyPart.LEFT_SHOULDER, BodyPart.RIGHT_SHOULDER);

        // 铜片肩甲：四档里最薄，换来的是能拨箭（一成半）
        register(ModItems.COPPER_SHOULDER_GUARD_LEFT, COPPER_REDUCTION, COPPER_DEFLECT_CHANCE,
                NO_MAGIC_REDUCTION, BodyPart.LEFT_SHOULDER);
        register(ModItems.COPPER_SHOULDER_GUARD_RIGHT, COPPER_REDUCTION, COPPER_DEFLECT_CHANCE,
                NO_MAGIC_REDUCTION, BodyPart.RIGHT_SHOULDER);
        register(ModItems.COPPER_SHOULDER_GUARD_PAIR, COPPER_REDUCTION, COPPER_DEFLECT_CHANCE,
                NO_MAGIC_REDUCTION, BodyPart.LEFT_SHOULDER, BodyPart.RIGHT_SHOULDER);

        // 鳞片肩甲：减得更狠，但不会弹远程
        register(ModItems.SCUTE_SHOULDER_GUARD_LEFT, HEAVY_REDUCTION, NO_DEFLECT,
                NO_MAGIC_REDUCTION, BodyPart.LEFT_SHOULDER);
        register(ModItems.SCUTE_SHOULDER_GUARD_RIGHT, HEAVY_REDUCTION, NO_DEFLECT,
                NO_MAGIC_REDUCTION, BodyPart.RIGHT_SHOULDER);
        register(ModItems.SCUTE_SHOULDER_GUARD_PAIR, HEAVY_REDUCTION, NO_DEFLECT,
                NO_MAGIC_REDUCTION, BodyPart.LEFT_SHOULDER, BodyPart.RIGHT_SHOULDER);

        // 龟壳肩甲：厚度与鳞片相同，另有一手——一成机会把箭弹开
        register(ModItems.TURTLE_SHELL_SHOULDER_GUARD_LEFT, HEAVY_REDUCTION, TURTLE_DEFLECT_CHANCE,
                NO_MAGIC_REDUCTION, BodyPart.LEFT_SHOULDER);
        register(ModItems.TURTLE_SHELL_SHOULDER_GUARD_RIGHT, HEAVY_REDUCTION, TURTLE_DEFLECT_CHANCE,
                NO_MAGIC_REDUCTION, BodyPart.RIGHT_SHOULDER);
        register(ModItems.TURTLE_SHELL_SHOULDER_GUARD_PAIR, HEAVY_REDUCTION, TURTLE_DEFLECT_CHANCE,
                NO_MAGIC_REDUCTION, BodyPart.LEFT_SHOULDER, BodyPart.RIGHT_SHOULDER);

        // 铁片肩甲：厚度与鳞片、龟壳相同，弹开概率翻倍到两成；代价是挥砍变慢（登记在遗物表里）
        register(ModItems.IRON_SHOULDER_GUARD_LEFT, HEAVY_REDUCTION, IRON_DEFLECT_CHANCE,
                NO_MAGIC_REDUCTION, BodyPart.LEFT_SHOULDER);
        register(ModItems.IRON_SHOULDER_GUARD_RIGHT, HEAVY_REDUCTION, IRON_DEFLECT_CHANCE,
                NO_MAGIC_REDUCTION, BodyPart.RIGHT_SHOULDER);
        register(ModItems.IRON_SHOULDER_GUARD_PAIR, HEAVY_REDUCTION, IRON_DEFLECT_CHANCE,
                NO_MAGIC_REDUCTION, BodyPart.LEFT_SHOULDER, BodyPart.RIGHT_SHOULDER);

        // ---- 胸甲片：护住正胸（只认躯干前半，后背不算）----

        // 铜胸甲片：最普通的一片，减得少、弹得也少，不挡魔法；代价是走路略慢（登记在遗物表里）
        register(ModItems.COPPER_CHESTPLATE_PLATE, COPPER_REDUCTION, COPPER_DEFLECT_CHANCE,
                NO_MAGIC_REDUCTION, BodyPart.CHEST);

        // 秘银胸甲片：全表最厚的一件，三成弹开，另替玩家挡下一点魔法伤害
        register(ModItems.MITHRIL_CHESTPLATE_PLATE, MITHRIL_REDUCTION, MITHRIL_DEFLECT_CHANCE,
                MITHRIL_MAGIC_REDUCTION, BodyPart.CHEST);

        // ---- 斗篷：护住后背 ----
        // 巡夜斗篷：后背挨的近战与远程各减 1 点。它的另外两样本事（夜里的移速、盔甲韧性）
        // 不是「挨打那一刻」的事，分别由 NightWatchCloakEffect 与遗物表负责
        register(ModItems.NIGHTWATCH_CLOAK, CLOAK_REDUCTION, NO_DEFLECT,
                NO_MAGIC_REDUCTION, BodyPart.BACK);

        // 风行披风：与巡夜斗篷同一路——同样只护后背、同样减 1 点。
        // 它的另一半本事（二段跳）不是「挨打那一刻」的事，由 WindCloakEffect 负责。
        // 注意两件斗篷同属配件类别「斗篷」，因此一件胸甲上只能披一件（它们也护同一块，
        // 即便能同时缝上，护后背那一条也只会取最狠的那件，等于白缝）
        register(ModItems.WIND_CLOAK, CLOAK_REDUCTION, NO_DEFLECT,
                NO_MAGIC_REDUCTION, BodyPart.BACK);
    }

    /**
     * 登记一件护具。
     *
     * <p>登记填漏时**直接抛错**，让它在启动时就暴露：这张表只有开发者会写，
     * 静默跳过只会变成「进了游戏才发现某件护具完全不顶用」。</p>
     *
     * @param guard          护具本身
     * @param reduction      它护着的那一块挨打时少掉几点伤害
     * @param deflectChance  远程打中护着那一块时弹开的概率；0 表示不会弹
     * @param magicReduction 替玩家挡下的魔法伤害点数；0 表示不挡
     * @param parts          它护住的部位，至少写一个
     */
    public static void register(Item guard, float reduction, float deflectChance, float magicReduction,
            BodyPart... parts) {
        if (parts.length == 0) {
            throw new IllegalArgumentException(
                    "护具「" + Registries.ITEM.getId(guard) + "」没有写明护住哪一块身体");
        }

        if (reduction <= 0.0F) {
            throw new IllegalArgumentException(
                    "护具「" + Registries.ITEM.getId(guard) + "」的减伤点数必须大于 0");
        }

        if (deflectChance < 0.0F || deflectChance >= 1.0F) {
            throw new IllegalArgumentException(
                    "护具「" + Registries.ITEM.getId(guard) + "」的弹开概率必须落在 0（含）到 1（不含）之间");
        }

        if (magicReduction < 0.0F) {
            throw new IllegalArgumentException(
                    "护具「" + Registries.ITEM.getId(guard) + "」的魔法减伤点数不能是负数");
        }

        BY_ITEM.put(guard, new ChestGuard(Set.of(parts), reduction, deflectChance, magicReduction));
    }

    /**
     * @param item 待查询的物品
     * @return 这件物品的护具配置；不是护具时返回 {@code null}
     */
    public static ChestGuard guardOf(Item item) {
        return BY_ITEM.get(item);
    }

    /**
     * @return 全部已登记的护具，按登记顺序
     */
    public static Map<Item, ChestGuard> all() {
        return Collections.unmodifiableMap(BY_ITEM);
    }
}
