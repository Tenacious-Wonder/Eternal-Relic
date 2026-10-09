package org.eternalrelic.capability.consumed;

import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 「远行的祝福」能力 —— 远行绑腿缠上之后，<b>直接给玩家的移速加一条会自己过期的加成</b>。
 *
 * <p><b>为什么不用状态效果</b>（制作者 2026-10-06 指定的做法）：状态效果会在 HUD 上占一个图标，
 * 背包界面里也会列出来 —— 而这一条祝福要的是「悄悄生效、玩家不必知道」。
 * 因此改走属性修饰符：它同样会被写进玩家的存档、退出重进都还在，
 * 但游戏里没有任何地方会把它画出来。</p>
 *
 * <p><b>「五天」记在哪</b>：记在修饰符自己的<b>名字</b>里（{@code eternal_relic:wayfarer:<到期刻数>}）。
 * 属性修饰符本来就会跟着玩家存档，名字也一并保存，因此不必另立一份玩家数据表；
 * 每 20 刻扫一遍在线玩家，到期的就摘掉。时间取<b>主世界的累计刻数</b>
 * （{@code getOverworld().getTime()}）—— 它随存档一路向前，重启、跨维度都不会算错。</p>
 *
 * <p><b>为什么用「持久」而不是「临时」修饰符</b>：临时修饰符（{@code addTemporaryModifier}）
 * 不写存档，玩家下线就没了。这一条要跨登录，所以必须用
 * {@code addPersistentModifier}，然后由我们自己负责把它按时摘掉。</p>
 */
public final class WayfarerBlessing {

    /** 加速的比例。制作者 2026-10-06 从 8% 提到 12%。 */
    private static final double SPEED_BONUS = 0.12D;

    /** 祝福持续多久：<b>五天</b>。游戏里一天是 24000 刻，因此 5 × 24000。 */
    private static final int DURATION_TICKS = 24_000 * 5;

    /**
     * 修饰符名字的前缀，后面接「到期那一刻的世界刻数」。
     *
     * <p>用名字而不是另存一份表：修饰符本身就会随玩家存档，名字也一并保存，
     * 省掉一张要跟着玩家生老病死的表（也不可能出现「表和属性对不上」的错位）。</p>
     */
    private static final String NAME_PREFIX = "eternal_relic:wayfarer:";

    /** 每 20 刻（1 秒）核对一次到期。 */
    private static final int CHECK_INTERVAL_TICKS = 20;

    private WayfarerBlessing() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，挂上「到期就摘掉」这一件事。
     */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % CHECK_INTERVAL_TICKS != 0) {
                return;
            }

            long now = server.getOverworld().getTime();

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                expire(player, now);
            }
        });
    }

    /**
     * 这位玩家身上的祝福还在不在。
     *
     * <p>物品用它挡住「重复缠一副」——一次性道具最忌按一下就白白少一个。</p>
     *
     * @param player 目标玩家
     * @return 身上还挂着祝福时返回 {@code true}
     */
    public static boolean isActive(ServerPlayerEntity player) {
        return findBlessing(player) != null;
    }

    /**
     * 缠上布条：挂一条五天后到期的加速。
     *
     * @param player 目标玩家
     */
    public static void apply(ServerPlayerEntity player) {
        EntityAttributeInstance instance = movementSpeedOf(player);

        if (instance == null) {
            return;
        }

        long expiresAt = player.server.getOverworld().getTime() + DURATION_TICKS;

        instance.addPersistentModifier(new EntityAttributeModifier(NAME_PREFIX + expiresAt, SPEED_BONUS,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    /**
     * 把已经到期的祝福摘掉。
     *
     * @param player 目标玩家
     * @param now    当前的世界刻数
     */
    private static void expire(ServerPlayerEntity player, long now) {
        EntityAttributeInstance instance = movementSpeedOf(player);

        if (instance == null) {
            return;
        }

        // 先复制一份再改：直接遍历 getModifiers() 的集合并从中摘东西会抛并发修改异常
        for (EntityAttributeModifier modifier : List.copyOf(instance.getModifiers())) {
            long expiresAt = expiresAtOf(modifier.getName());

            if (expiresAt > 0L && now >= expiresAt) {
                instance.removeModifier(modifier);
            }
        }
    }

    /**
     * 找出玩家身上的祝福修饰符。
     *
     * @param player 目标玩家
     * @return 那条修饰符；没有时返回 {@code null}
     */
    private static EntityAttributeModifier findBlessing(ServerPlayerEntity player) {
        EntityAttributeInstance instance = movementSpeedOf(player);

        if (instance == null) {
            return null;
        }

        for (EntityAttributeModifier modifier : instance.getModifiers()) {
            if (modifier.getName().startsWith(NAME_PREFIX)) {
                return modifier;
            }
        }

        return null;
    }

    /**
     * @param player 目标玩家
     * @return 他的移速属性；取不到时返回 {@code null}
     */
    private static EntityAttributeInstance movementSpeedOf(ServerPlayerEntity player) {
        return player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
    }

    /**
     * 从修饰符名字里读回「到期那一刻的世界刻数」。
     *
     * @param name 修饰符的名字
     * @return 到期的刻数；不是本能力挂上去的、或者名字被改坏了，返回 {@code -1}
     */
    private static long expiresAtOf(String name) {
        if (!name.startsWith(NAME_PREFIX)) {
            return -1L;
        }

        try {
            return Long.parseLong(name.substring(NAME_PREFIX.length()));
        } catch (NumberFormatException notOurs) {
            return -1L;
        }
    }
}
