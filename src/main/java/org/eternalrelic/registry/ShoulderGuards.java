package org.eternalrelic.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

import org.eternalrelic.bodypart.BodyPart;
import org.eternalrelic.relic.ShoulderGuard;

/**
 * <b>肩甲表</b> —— 哪件肩甲护住哪一侧肩膀、挨打时少掉几点伤害、能不能弹开远程攻击。
 *
 * <p>这是一份**由制作者选定的白名单**：一件遗物没登记在这里，就是没有「护肩」这项本事，
 * 这是正常状态，不是漏写。想让某件遗物护肩，在这里加一行即可，不必改能力类。</p>
 *
 * <p><b>三档肩甲的分工完全一样</b>：左肩甲只护左肩、右肩甲只护右肩，两只合成的一套两侧都护。
 * 差别只在厚度与本事上：皮革那只每件减 {@value #LEATHER_REDUCTION} 点，鳞片与龟壳那两只
 * 每件减 {@value #HEAVY_REDUCTION} 点，而<b>只有龟壳那只会把远程攻击弹开</b>。
 * 一套的长处始终是「两侧都护」，而不是减得更狠或弹得更勤；玩家之所以要合成，
 * 是为了在胸甲上少占一个附着格。</p>
 *
 * <p><b>护住同一侧的多件不会相加</b>：胸甲上同时缝了护住同一侧的好几件肩甲时，
 * 减伤只认最多的那一件，弹开概率也只认最高的那一件。这条规矩不在这张表里，
 * 而由 {@code capability.attached.ShoulderGuardEffect} 在算的时候执行——
 * 表只负责如实登记每一件各是多少。</p>
 *
 * <p>与 {@code DayNightEmblems} 同一套做法：表在这里，怎么生效由那个能力类负责。
 * 日后要做新的肩甲，加一行就行。</p>
 */
public final class ShoulderGuards {

    /** 皮革肩甲挨打时少掉的伤害点数。 */
    private static final float LEATHER_REDUCTION = 2.0F;

    /** 鳞片肩甲与龟壳肩甲挨打时少掉的伤害点数——都比皮革那只厚。 */
    private static final float HEAVY_REDUCTION = 2.5F;

    /** 龟壳肩甲把远程攻击弹开的概率。 */
    private static final float TURTLE_DEFLECT_CHANCE = 0.1F;

    /** 不会弹开远程攻击的肩甲填这个值。 */
    private static final float NO_DEFLECT = 0.0F;

    /** 全部已登记的肩甲，按登记顺序。 */
    private static final Map<Item, ShoulderGuard> BY_ITEM = new LinkedHashMap<>();

    private ShoulderGuards() {
    }

    /**
     * 由 {@link ModItems#register()} 调用，触发本类静态内容初始化（把三档九件肩甲登记进表里）。
     */
    static void register() {
        // 皮革肩甲：左护左肩、右护右肩、一套两侧都护；不会弹开远程
        register(ModItems.LEATHER_SHOULDER_GUARD_LEFT, LEATHER_REDUCTION, NO_DEFLECT,
                BodyPart.LEFT_SHOULDER);

        register(ModItems.LEATHER_SHOULDER_GUARD_RIGHT, LEATHER_REDUCTION, NO_DEFLECT,
                BodyPart.RIGHT_SHOULDER);

        register(ModItems.LEATHER_SHOULDER_GUARD_PAIR, LEATHER_REDUCTION, NO_DEFLECT,
                BodyPart.LEFT_SHOULDER, BodyPart.RIGHT_SHOULDER);

        // 鳞片肩甲：分工与皮革那只一模一样，只是更厚；同样不会弹开远程
        register(ModItems.SCUTE_SHOULDER_GUARD_LEFT, HEAVY_REDUCTION, NO_DEFLECT,
                BodyPart.LEFT_SHOULDER);

        register(ModItems.SCUTE_SHOULDER_GUARD_RIGHT, HEAVY_REDUCTION, NO_DEFLECT,
                BodyPart.RIGHT_SHOULDER);

        register(ModItems.SCUTE_SHOULDER_GUARD_PAIR, HEAVY_REDUCTION, NO_DEFLECT,
                BodyPart.LEFT_SHOULDER, BodyPart.RIGHT_SHOULDER);

        // 龟壳肩甲：厚度与鳞片那只相同，另有一手——打在护着那一侧的远程攻击，
        // 有 10% 的机会被整个弹开（箭会掉头崩回去）。三件各自算各自的概率
        register(ModItems.TURTLE_SHELL_SHOULDER_GUARD_LEFT, HEAVY_REDUCTION, TURTLE_DEFLECT_CHANCE,
                BodyPart.LEFT_SHOULDER);

        register(ModItems.TURTLE_SHELL_SHOULDER_GUARD_RIGHT, HEAVY_REDUCTION, TURTLE_DEFLECT_CHANCE,
                BodyPart.RIGHT_SHOULDER);

        register(ModItems.TURTLE_SHELL_SHOULDER_GUARD_PAIR, HEAVY_REDUCTION, TURTLE_DEFLECT_CHANCE,
                BodyPart.LEFT_SHOULDER, BodyPart.RIGHT_SHOULDER);
    }

    /**
     * 登记一件护肩的遗物。
     *
     * <p>登记填漏时**直接抛错**，让它在启动时就暴露：这张表只有开发者会写，
     * 静默跳过只会变成「进了游戏才发现某件肩甲完全不顶用」。</p>
     *
     * @param guard         肩甲本身
     * @param reduction     它护着的那一侧挨打时少掉几点伤害
     * @param deflectChance 远程打中护着那一侧时弹开的概率；0 表示不会弹
     * @param parts         它护住的部位，至少写一个
     */
    public static void register(Item guard, float reduction, float deflectChance, BodyPart... parts) {
        if (parts.length == 0) {
            throw new IllegalArgumentException(
                    "肩甲「" + Registries.ITEM.getId(guard) + "」没有写明护住哪一侧肩膀");
        }

        if (reduction <= 0.0F) {
            throw new IllegalArgumentException(
                    "肩甲「" + Registries.ITEM.getId(guard) + "」的减伤点数必须大于 0");
        }

        if (deflectChance < 0.0F || deflectChance >= 1.0F) {
            throw new IllegalArgumentException(
                    "肩甲「" + Registries.ITEM.getId(guard) + "」的弹开概率必须落在 0（含）到 1（不含）之间");
        }

        BY_ITEM.put(guard, new ShoulderGuard(Set.of(parts), reduction, deflectChance));
    }

    /**
     * @param item 待查询的物品
     * @return 这件物品的护肩配置；不是肩甲时返回 {@code null}
     */
    public static ShoulderGuard guardOf(Item item) {
        return BY_ITEM.get(item);
    }

    /**
     * @return 全部已登记的肩甲，按登记顺序
     */
    public static Map<Item, ShoulderGuard> all() {
        return Collections.unmodifiableMap(BY_ITEM);
    }
}
