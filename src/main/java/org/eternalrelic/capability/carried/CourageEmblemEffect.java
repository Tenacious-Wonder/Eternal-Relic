package org.eternalrelic.capability.carried;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

import org.eternalrelic.registry.ModItems;

/**
 * 「定时馈赠」能力：勇气纹章放在背包里时，每隔一分钟替玩家攒下两颗金心。
 *
 * <p>与属性加成的区别在于它按时间出手，而不是一直挂着一个数值：每满一分钟给一次，
 * 一次给两颗，攒到四颗就不再往上加。金心就是吸收，在伤害结算的最后一步替玩家挡掉一部分，
 * 因此多出来的那一排金心本身就是「纹章刚才出过手」的提示。</p>
 *
 * <p><b>为什么攒到四颗就停</b>：吸收不会自己消失，被打掉才算没。若不加封顶，
 * 带够五分钟就能白拿满十颗金心，等于凭时间换一条命；封顶之后它是一件「维持着一点余裕」的遗物，
 * 而不是一台护盾生成机。</p>
 *
 * <p><b>不回收已经给出去的金心</b>：金心一旦给出就与纹章无关了，把纹章收起来也不会让它消失。
 * 因此这里只需在「玩家携带时」往上加，不必记录哪一部分是自己给的——也就没有需要维护的账。</p>
 *
 * <p><b>与其它吸收来源的关系</b>：只在自己还没攒到封顶时补，且补的时候不会超过封顶，
 * 也绝不会把玩家已有的吸收往下压。因此金苹果之类的外来吸收不会被它削减，只是让它提前停手。</p>
 */
public final class CourageEmblemEffect {

    /** 每隔多少刻核对一次。20 刻为一秒，因此计数单位就是「秒」。 */
    private static final int CHECK_INTERVAL_TICKS = 20;

    /** 携带满多少秒给一次。 */
    private static final int SECONDS_PER_GIFT = 60;

    /** 每次给多少点吸收。游戏中一颗金心抵 2 点，因此 4 点就是两颗金心。 */
    private static final float ABSORPTION_PER_GIFT = 4.0F;

    /** 这份纹章最多替玩家维持多少点吸收。8 点就是四颗金心。 */
    private static final float ABSORPTION_CEILING = 8.0F;

    /** 记录每位玩家已经安静携带了多少秒：玩家编号 → 秒数。 */
    private static final Map<UUID, Integer> CARRIED_SECONDS = new HashMap<>();

    private CourageEmblemEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.EternalRelic#onInitialize()} 调用，挂上计时用的回调。
     *
     * <p>核对节奏放宽到每秒一次：这里只数一个很长的计时，不需要像属性那样每 5 刻跟一次，
     * 少扫几遍背包也更省。</p>
     */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % CHECK_INTERVAL_TICKS != 0) {
                return;
            }

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                tick(player);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                CARRIED_SECONDS.remove(handler.getPlayer().getUuid()));
    }

    /**
     * 推进一名玩家一秒：携带纹章就攒时间，攒满一分钟给一次金心。
     *
     * <p>没带纹章时秒数清零，因此中途把纹章收起来再放回去会重新从零开始数。</p>
     *
     * @param player 目标玩家
     */
    private static void tick(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();

        if (!CarriedStacks.inEffect(player, ModItems.COURAGE_EMBLEM)) {
            CARRIED_SECONDS.remove(playerId);
            return;
        }

        int seconds = CARRIED_SECONDS.merge(playerId, 1, Integer::sum);
        if (seconds < SECONDS_PER_GIFT) {
            return;
        }

        CARRIED_SECONDS.put(playerId, 0);
        grantHearts(player);
    }

    /**
     * 给玩家补上两颗金心。
     *
     * <p>已经攒到封顶就什么也不做；没到封顶时按「加两颗、但不越过封顶」补，
     * 因此给出的吸收永远不会超过四颗金心，也不会把已有的吸收压低。</p>
     *
     * @param player 目标玩家
     */
    private static void grantHearts(ServerPlayerEntity player) {
        float current = player.getAbsorptionAmount();
        if (current >= ABSORPTION_CEILING) {
            return;
        }

        player.setAbsorptionAmount(Math.min(current + ABSORPTION_PER_GIFT, ABSORPTION_CEILING));
    }
}
