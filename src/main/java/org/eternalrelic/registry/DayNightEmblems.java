package org.eternalrelic.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.world.World;

/**
 * <b>时段纹章表</b> —— 哪一枚纹章管白天还是夜晚、在那一段里给身上挂哪两条状态效果。
 *
 * <p>「太阳纹章」与「月亮纹章」共用同一套规矩：<b>缝在正穿着 / 正拿着的装备（或副手的盾牌）上</b>时，
 * 只要世界处于它管的那一段，就持续给两条状态效果；玩家身上本来已经有其中一条时，
 * 改成在原有等级上再抬一级。两枚只有「管哪一段」与「给哪两条」不同，
 * 因此共用这一张表与一个能力类（见 {@code capability.carried.DayNightEmblemEffect}）。</p>
 *
 * <p><b>要做第三枚同类纹章时（例如「黄昏纹章」），在这里加一行即可</b>，不必改能力类，也不必再抄一个类。</p>
 */
public final class DayNightEmblems {

    /** 一枚时段纹章管的是哪一段，以及那一段里给哪两条状态效果。 */
    public record Blessing(TimeOfDay time, List<StatusEffect> effects) {

        /**
         * 收下登记进来的效果，并复制一份存起来，让这份清单此后只读。
         */
        public Blessing {
            effects = List.copyOf(effects);
        }
    }

    /** 时段纹章管的那一段。 */
    public enum TimeOfDay {

        /** 白天 —— 天是亮的。 */
        DAY,

        /** 夜晚 —— 天是暗的。 */
        NIGHT;

        /**
         * 这个世界此刻是否处于本段。
         *
         * <p>问的是<b>世界自己</b>（时间与天气），<b>不看玩家站在哪</b> ——
         * 因此在地下挖矿时白天照样是白天，洞里的黑暗不算数。
         * 下界与末地没有昼夜（原版那里两个判断都不成立），两段都不给效果。</p>
         *
         * @param world 玩家所在的世界
         * @return 此刻是否算本段
         */
        public boolean matches(World world) {
            return this == DAY ? world.isDay() : world.isNight();
        }
    }

    /** 全部已登记的时段纹章，按登记顺序。 */
    private static final Map<Item, Blessing> BY_ITEM = new LinkedHashMap<>();

    private DayNightEmblems() {
    }

    /**
     * 由 {@link ModItems#register()} 调用，触发本类静态内容初始化（也就是把纹章登记进表里）。
     */
    static void register() {
        // 太阳纹章：白天给生命恢复与力量
        register(ModItems.SUN_EMBLEM, TimeOfDay.DAY,
                StatusEffects.REGENERATION,
                StatusEffects.STRENGTH);

        // 月亮纹章：夜晚给生命恢复与速度
        register(ModItems.MOON_EMBLEM, TimeOfDay.NIGHT,
                StatusEffects.REGENERATION,
                StatusEffects.SPEED);
    }

    /**
     * 登记一枚时段纹章。
     *
     * <p>登记填漏时**直接抛错**，让它在启动时就暴露——这张表只有开发者会写，
     * 静默跳过只会变成「进了游戏才发现某枚纹章什么都不给」。</p>
     *
     * @param emblem  纹章本身
     * @param time    它管哪一段
     * @param effects 那一段里给的状态效果，至少写一条
     */
    public static void register(Item emblem, TimeOfDay time, StatusEffect... effects) {
        if (effects.length == 0) {
            throw new IllegalArgumentException(
                    "时段纹章「" + Registries.ITEM.getId(emblem) + "」没有写明给哪几条效果");
        }

        BY_ITEM.put(emblem, new Blessing(time, List.of(effects)));
    }

    /**
     * @param item 待查询的物品
     * @return 这枚纹章的时段与效果；不是时段纹章时返回 {@code null}
     */
    public static Blessing of(Item item) {
        return BY_ITEM.get(item);
    }

    /**
     * @return 全部已登记的时段纹章，按登记顺序
     */
    public static Map<Item, Blessing> all() {
        return Collections.unmodifiableMap(BY_ITEM);
    }
}
