package org.eternalrelic.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

import org.eternalrelic.bodypart.BodyPart;
import org.eternalrelic.relic.ChestplatePlate;

/**
 * <b>胸甲片表</b> —— 哪件胸甲片护住正胸、挨打时少掉几点伤害、能不能弹开远程攻击、
 * 以及替玩家挡下几点魔法伤害。
 *
 * <p>这是一份**由制作者选定的白名单**：一件遗物没登记在这里，就是没有「胸甲片」这项本事，
 * 这是正常状态，不是漏写。想让某件遗物成为胸甲片，在这里加一行即可，不必改能力类。</p>
 *
 * <p><b>它护的是「正胸」那一块，不是整个躯干</b>：背后挨的刀落在
 * {@link BodyPart#BACK} 上，这张表一点都挡不住——胸前贴的一块甲片护不住后背。</p>
 *
 * <p><b>它只缝在胸甲上</b>（登记在可附遗物表里），而且属于「胸甲片」这一类配件，
 * 因此<b>一件胸甲上只能有一片</b>（见 {@code relic/FittingCategory}）。它与肩甲、
 * 内衬、甲片是不同的类别，可以同时缝在一件胸甲上，挨打时各算各的。</p>
 *
 * <p>与 {@code ShoulderGuards} 同一套做法：表在这里，怎么生效由
 * {@code capability.attached.ChestplatePlateEffect} 负责。日后要做「护腿片」「头盔片」，
 * 照这里的做法另开一张表即可——护住哪个部位由登记时写明的部位决定。</p>
 */
public final class ChestplatePlates {

    /** 铜胸甲片挨打时少掉的伤害点数 —— 只有秘银那一片的一半。 */
    private static final float COPPER_REDUCTION = 1.5F;

    /** 铜胸甲片把远程攻击弹开的概率 —— 同样只有秘银那一片的一半。 */
    private static final float COPPER_DEFLECT_CHANCE = 0.15F;

    /** 秘银胸甲片挨打时少掉的伤害点数。 */
    private static final float MITHRIL_REDUCTION = 3.0F;

    /** 秘银胸甲片把远程攻击弹开的概率 —— 比任何一档肩甲都高。 */
    private static final float MITHRIL_DEFLECT_CHANCE = 0.3F;

    /** 秘银胸甲片替玩家挡下的魔法伤害点数。 */
    private static final float MITHRIL_MAGIC_REDUCTION = 1.0F;

    /** 不替玩家挡魔法伤害的胸甲片填这个值。 */
    private static final float NO_MAGIC_REDUCTION = 0.0F;

    /** 全部已登记的胸甲片，按登记顺序。 */
    private static final Map<Item, ChestplatePlate> BY_ITEM = new LinkedHashMap<>();

    private ChestplatePlates() {
    }

    /**
     * 由 {@link ModItems#register()} 调用，触发本类静态内容初始化（把已登记的胸甲片登记进表里）。
     */
    static void register() {
        // 铜胸甲片：最普通的一片——护正胸、近战与远程各减 1.5 点、一成半的箭被弹开，不挡魔法。
        // 代价是穿着它走路略慢（那一条登记在遗物表里）
        register(ModItems.COPPER_CHESTPLATE_PLATE, COPPER_REDUCTION, COPPER_DEFLECT_CHANCE,
                NO_MAGIC_REDUCTION, BodyPart.CHEST);

        // 秘银胸甲片：护正胸、近战与远程各减 3 点、三成的箭被弹开、魔法伤害减 1 点。
        // 它是第一件「替玩家挡魔法」的护具
        register(ModItems.MITHRIL_CHESTPLATE_PLATE, MITHRIL_REDUCTION, MITHRIL_DEFLECT_CHANCE,
                MITHRIL_MAGIC_REDUCTION, BodyPart.CHEST);
    }

    /**
     * 登记一件胸甲片。
     *
     * <p>登记填漏时**直接抛错**，让它在启动时就暴露：这张表只有开发者会写，
     * 静默跳过只会变成「进了游戏才发现这件胸甲片完全不顶用」。</p>
     *
     * @param plate         胸甲片本身
     * @param reduction     它护着的那一块挨打时少掉几点伤害
     * @param deflectChance 远程打中护着那一块时弹开的概率；0 表示不会弹
     * @param magicReduction 替玩家挡下的魔法伤害点数；0 表示不挡
     * @param parts         它护住的部位，至少写一个
     */
    public static void register(Item plate, float reduction, float deflectChance, float magicReduction,
            BodyPart... parts) {
        if (parts.length == 0) {
            throw new IllegalArgumentException(
                    "胸甲片「" + Registries.ITEM.getId(plate) + "」没有写明护住哪一块身体");
        }

        if (reduction <= 0.0F) {
            throw new IllegalArgumentException(
                    "胸甲片「" + Registries.ITEM.getId(plate) + "」的减伤点数必须大于 0");
        }

        if (deflectChance < 0.0F || deflectChance >= 1.0F) {
            throw new IllegalArgumentException(
                    "胸甲片「" + Registries.ITEM.getId(plate) + "」的弹开概率必须落在 0（含）到 1（不含）之间");
        }

        if (magicReduction < 0.0F) {
            throw new IllegalArgumentException(
                    "胸甲片「" + Registries.ITEM.getId(plate) + "」的魔法减伤点数不能是负数");
        }

        BY_ITEM.put(plate, new ChestplatePlate(Set.of(parts), reduction, deflectChance, magicReduction));
    }

    /**
     * @param item 待查询的物品
     * @return 这件物品的胸甲片配置；不是胸甲片时返回 {@code null}
     */
    public static ChestplatePlate plateOf(Item item) {
        return BY_ITEM.get(item);
    }

    /**
     * @return 全部已登记的胸甲片，按登记顺序
     */
    public static Map<Item, ChestplatePlate> all() {
        return Collections.unmodifiableMap(BY_ITEM);
    }
}
