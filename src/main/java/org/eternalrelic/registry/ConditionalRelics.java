package org.eternalrelic.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * <b>条件遗物表</b> —— 哪一件遗物，要在什么条件下，才给身上挂哪几条属性加成。
 *
 * <p>此前的属性加成只有两种形态：<b>带着就一直有</b>（风行披风、奥塔的枝叶那一大批，
 * 写进 {@link ModRelics 遗物表}即可），以及<b>看天色</b>（巡夜斗篷，自己写了一个能力类）。
 * 这一张表是第三种：<b>看玩家的某个状态</b> —— 血量满不满、天在不在打雷。
 * 这类条件以后还会加（黄昏、饥饿、水下……），因此做成表：<b>加一件只需在这里写一行</b>，
 * 不必再抄一个能力类。</p>
 *
 * <p><b>表里没有属性加成以外的内容。</b>给状态效果的那些（太阳 / 月亮 / 雷鸣之外的纹章）
 * 走 {@link DayNightEmblems} 那条路，两者互不干涉。</p>
 *
 * <p>表里的每一行由 {@link org.eternalrelic.capability.carried.ConditionalAttributeEffect}
 * 每 5 刻核对一次：条件成立就挂上加成，不成立就摘掉。<b>加成是临时的</b>，不写进存档。</p>
 */
public final class ConditionalRelics {

    /** 一件遗物生效要看的那件事。 */
    public enum Condition {

        /** <b>满血</b>：此刻血量正好是上限（差一点都不算）。 */
        FULL_HEALTH,

        /** <b>雷雨</b>：所在世界正在雷暴（普通下雨不算）。 */
        THUNDERING;

        /**
         * 这位玩家此刻是否满足本条件。
         *
         * @param player 目标玩家
         * @return 满足为 {@code true}
         */
        public boolean matches(ServerPlayerEntity player) {
            return switch (this) {
                case FULL_HEALTH -> player.getHealth() >= player.getMaxHealth();
                case THUNDERING -> player.getWorld().isThundering();
            };
        }
    }

    /**
     * 一条属性加成。
     *
     * @param attribute 改哪条属性
     * @param value     加多少
     * @param operation 怎么加（乘算百分比还是直接加固定值）
     */
    public record Boost(EntityAttribute attribute, double value,
            EntityAttributeModifier.Operation operation) {
    }

    /**
     * 一件遗物的一整行。
     *
     * @param condition 它看哪件事
     * @param boosts    条件成立时给的加成，至少一条
     */
    public record Entry(Condition condition, List<Boost> boosts) {

        /**
         * 收下登记进来的加成，并复制一份存起来，让这份清单此后只读。
         */
        public Entry {
            boosts = List.copyOf(boosts);
        }
    }

    /** 全部已登记的条件遗物，按登记顺序。 */
    private static final Map<Item, Entry> BY_ITEM = new LinkedHashMap<>();

    private ConditionalRelics() {
    }

    /**
     * 由 {@link ModItems#register()} 调用，触发本类静态内容初始化（也就是把遗物登记进表里）。
     */
    static void register() {
        // 无缺吊坠（原名"无缺吊坠"）：血量满时跑得更快、打得更重；一受伤立刻失效
        register(ModItems.FLAWLESS_PENDANT, Condition.FULL_HEALTH,
                new Boost(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.10D,
                        EntityAttributeModifier.Operation.MULTIPLY_TOTAL),
                new Boost(EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.0D,
                        EntityAttributeModifier.Operation.ADDITION));

        // 雷电纹章：雷雨天跑得更快（移速 +15%）。
        // ★ 它的**攻击侧不在本表**：那一边给的是"近战额外 6 点魔法伤害"
        // （见 capability/carried/ThunderEmblemEffect）—— 那是"给挨打的一方补一下"，
        // 不是"给自己挂属性"，路子不同，因此分成两处。两处看的是同一个条件：打雷。
        register(ModItems.THUNDER_EMBLEM, Condition.THUNDERING,
                new Boost(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.15D,
                        EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    /**
     * 登记一件条件遗物。
     *
     * <p>登记填漏时<b>直接抛错</b>，让它在启动时就暴露 —— 这张表只有开发者会写，
     * 静默跳过只会变成"进了游戏才发现某件遗物什么都不给"。</p>
     *
     * @param relic     遗物本身
     * @param condition 它看哪件事
     * @param boosts    条件成立时给的加成，至少写一条
     */
    public static void register(Item relic, Condition condition, Boost... boosts) {
        if (boosts.length == 0) {
            throw new IllegalArgumentException(
                    "条件遗物「" + Registries.ITEM.getId(relic) + "」没有写明给哪几条加成");
        }

        BY_ITEM.put(relic, new Entry(condition, List.of(boosts)));
    }

    /**
     * @return 全部已登记的条件遗物，按登记顺序
     */
    public static Map<Item, Entry> all() {
        return Collections.unmodifiableMap(BY_ITEM);
    }
}
