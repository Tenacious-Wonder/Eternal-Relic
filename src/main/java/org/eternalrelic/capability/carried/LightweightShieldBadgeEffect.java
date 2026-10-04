package org.eternalrelic.capability.carried;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;

import org.eternalrelic.registry.ModItems;

/**
 * 「举盾轻身」能力：带着轻巧盾徽时，举着盾牌的那段时间里走得更快。
 *
 * <p><b>为什么这条加成要临时挂、临时摘</b>：它是<b>有条件</b>的——只在举盾时才算数，盾一放下
 * 就该没。而玩家身上那一堆属性加成是按「带没带」来算的（见 {@link CarriedRelicEffect}），
 * 表达不了「带着，但此刻不算」。因此这一条不进遗物表，由这里每 {@value #CHECK_INTERVAL_TICKS}
 * 刻对着「他此刻举着盾没有」重新核对一次：举着就挂上，放下就摘掉。</p>
 *
 * <p><b>用的是「临时」属性加成</b>，与旅人吊坠给坐骑加速同一个理由：临时加成不写进存档。
 * 玩家举着盾退出游戏、或服务器被强杀，存档里都不会留下一条洗不掉的加速——重新进来时
 * 他两手空空，这里自然会重新判断要不要给。</p>
 *
 * <p><b>带多枚不叠加</b>：只问「带没带」，不问带了几枚。</p>
 */
public final class LightweightShieldBadgeEffect {

    /** 每隔多少刻核对一次举盾状态。5 刻约为 0.25 秒，放下盾后几乎立刻恢复常态。 */
    private static final int CHECK_INTERVAL_TICKS = 5;

    /**
     * 举盾时的移速提升比例。{@code 2.0} 表示「在举盾那档速度上快两倍」，也就是原来的三倍。
     *
     * <p>这个数是制作者逐轮试出来的：初版一成半（{@code 0.15}）几乎感觉不到，翻一倍
     * （{@code 1.0}）之后仍然偏肉，最后定在 {@code 2.0}——举盾时约合正常走速的六成。</p>
     *
     * <p><b>要知道它补的是什么</b>：原版对「正在使用物品」（举盾、拉弓、吃东西）另有一层狠减速，
     * 在客户端把移动输入直接乘 {@code 0.2}；那一层乘的是<b>输入</b>而不是属性，这里的加成补不动它。
     * 因此举盾时永远比空手慢，只是不至于寸步难行。想要「举盾＝正常走速」，
     * 这个数得给到 {@code 4.0}。</p>
     */
    private static final double SPEED_BONUS = 2.0D;

    /**
     * 这条加速在玩家身上的固定标识。
     *
     * <p>标识必须固定：核对是反复进行的，靠它才能认出「这条是不是我们挂的那一条」，
     * 从而做到挂一次就够、不会每 5 刻叠一层。</p>
     */
    private static final UUID MODIFIER_ID = UUID.nameUUIDFromBytes(
            "eternal_relic:lightweight_shield_badge/blocking_speed".getBytes(StandardCharsets.UTF_8));

    /** 这条加速在玩家属性面板里显示的名字。 */
    private static final String MODIFIER_NAME = "eternal_relic:lightweight_shield_badge";

    private LightweightShieldBadgeEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，挂上核对举盾状态的回调。
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
     * 核对一名玩家此刻该不该有这份加速，并让属性与之保持一致。
     *
     * <p>两个条件缺一不可：<b>手上正举着盾</b>，且<b>身上带着盾徽</b>（背包里放着，或者钉在
     * 正拿着的那面盾牌上，两者都算——见 {@link CarriedStacks#inEffect}）。</p>
     *
     * @param player 目标玩家
     */
    private static void applyFor(ServerPlayerEntity player) {
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }

        if (player.isBlocking() && CarriedStacks.inEffect(player, ModItems.LIGHTWEIGHT_SHIELD_BADGE)) {
            if (speed.getModifier(MODIFIER_ID) == null) {
                speed.addTemporaryModifier(new EntityAttributeModifier(
                        MODIFIER_ID,
                        MODIFIER_NAME,
                        SPEED_BONUS,
                        EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
            }
            return;
        }

        // 没举盾、或盾徽已经不在身上：把这份加速摘掉。没挂过时这一句什么也不会做
        speed.removeModifier(MODIFIER_ID);
    }
}
