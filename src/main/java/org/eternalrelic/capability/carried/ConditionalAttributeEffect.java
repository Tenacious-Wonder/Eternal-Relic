package org.eternalrelic.capability.carried;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

import org.eternalrelic.registry.ConditionalRelics;

/**
 * <b>条件遗物的统一执行者</b> —— 按 {@link ConditionalRelics 条件遗物表} 里的登记，
 * 每 5 刻核对一次"带着没有 + 条件成立没有"，据此把属性加成挂上或摘掉。
 *
 * <p><b>为什么要单独核对、而不写进遗物表</b>：遗物表里的加成是"带着就一直有"，
 * 游戏只在玩家物品变动时算一次；而这一类的开关是<b>血量与天气</b>，它们随时会变，
 * 因此必须有人隔一会儿看一眼。这与巡夜斗篷（看天色）是同一个道理，
 * 区别只在于那一件自己写了一个类，而这一类共用一个。</p>
 *
 * <p><b>用的是「临时」属性加成。</b>满血与雷雨一天要满足好几回，加成挂上摘下许多次，
 * 写进存档毫无意义；临时加成在实体被写进存档时会跳过，所以玩家退出重进身上是干净的，
 * 不必再写一处清理。旅人吊坠、巡夜斗篷用的都是这个手法。</p>
 *
 * <p><b>每条加成的标识由"遗物 + 第几条"算出来</b>，因此同一件遗物的多条加成互不干扰，
 * 也不会与别的遗物撞车（不用手写一串 UUID 常量）。</p>
 */
public final class ConditionalAttributeEffect {

    /** 每隔多少刻核对一次。5 刻约为 0.25 秒，与其它携带型能力同一个节奏。 */
    private static final int CHECK_INTERVAL_TICKS = 5;

    /** 属性面板里显示的来源名字。 */
    private static final String MODIFIER_NAME = "eternal_relic:conditional";

    private ConditionalAttributeEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，挂上逐刻核对的回调。
     */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % CHECK_INTERVAL_TICKS != 0) {
                return;
            }

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                applyFor(player);
            }
        });
    }

    /**
     * 把一名玩家身上所有条件遗物的加成，调整到与此刻的情况一致。
     *
     * @param player 目标玩家
     */
    private static void applyFor(ServerPlayerEntity player) {
        for (Map.Entry<Item, ConditionalRelics.Entry> registered : ConditionalRelics.all().entrySet()) {
            Item relic = registered.getKey();
            ConditionalRelics.Entry entry = registered.getValue();

            boolean active = CarriedStacks.carries(player, relic) && entry.condition().matches(player);

            int index = 0;
            for (ConditionalRelics.Boost boost : entry.boosts()) {
                setModifier(player, boost, idFor(relic, index), active);
                index++;
            }
        }
    }

    /**
     * 算出某件遗物第几条加成的固定标识。
     *
     * @param relic 遗物
     * @param index 第几条（从 0 起）
     * @return 这条加成的标识
     */
    private static UUID idFor(Item relic, int index) {
        String path = "eternal_relic:conditional/" + Registries.ITEM.getId(relic).getPath() + "/" + index;

        return UUID.nameUUIDFromBytes(path.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 把一条加成挂上、更新或摘掉。
     *
     * <p>值没变时什么都不做 —— 反复挂同一条会在属性面板上堆出一长串一模一样的记录。</p>
     *
     * @param player 目标玩家
     * @param boost  这条加成
     * @param id     这条加成的固定标识
     * @param active 此刻该不该有这条加成
     */
    private static void setModifier(ServerPlayerEntity player, ConditionalRelics.Boost boost, UUID id,
            boolean active) {
        EntityAttributeInstance instance = player.getAttributeInstance(boost.attribute());

        if (instance == null) {
            return;
        }

        EntityAttributeModifier existing = instance.getModifier(id);

        if (!active) {
            if (existing != null) {
                instance.removeModifier(id);
            }

            return;
        }

        if (existing != null && existing.getValue() == boost.value()
                && existing.getOperation() == boost.operation()) {
            return;
        }

        instance.removeModifier(id);
        instance.addTemporaryModifier(new EntityAttributeModifier(id, MODIFIER_NAME,
                boost.value(), boost.operation()));
    }
}
