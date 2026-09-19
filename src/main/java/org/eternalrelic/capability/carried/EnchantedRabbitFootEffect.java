package org.eternalrelic.capability.carried;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;

import org.eternalrelic.registry.ModItems;
import org.eternalrelic.relic.AttackDamage;

/**
 * 「受击加速」能力：带着附魔兔脚挨打时，立刻换来一段速度。
 *
 * <p>与另外两类能力的区别：{@code CarriedRelicEffect} 管的是一直升效的数值，
 * {@code DamageWardEffect} 管的是把这一击挡下来，而这里<b>既不挡伤害也不改伤害</b>——
 * 挨打照常挨，只是在挨的这一下换来一段跑得快的功夫。因此它挂上判定之后一律放行，
 * 游戏原本的伤害结算一步不动。</p>
 *
 * <p>兔脚本身不必装上、也不必拿在手上，放主背包或副手即可。</p>
 */
public final class EnchantedRabbitFootEffect {

    /** 身上没有速度时给的持续时间。100 刻 = 5 秒。 */
    private static final int DURATION_TICKS = 100;

    /** 身上没有速度时给到的等级。游戏中 0 级写作「速度 I」，因此 1 级就是「速度 II」。 */
    private static final int BASE_AMPLIFIER = 1;

    /** 身上已有速度时，在原有等级之上再抬几级。 */
    private static final int UPGRADE_STEPS = 2;

    /** 出手之后的冷却。2400 刻 = 2 分钟。 */
    private static final int COOLDOWN_TICKS = 2400;

    /** 每隔多少刻清一次已经过期的冷却记录。1200 刻 = 1 分钟。 */
    private static final int CLEANUP_INTERVAL_TICKS = 1200;

    /** 记录每位玩家何时可以再次触发：玩家编号 → 冷却结束时的世界刻数。 */
    private static final Map<UUID, Long> READY_AT = new HashMap<>();

    private EnchantedRabbitFootEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.EternalRelic#onInitialize()} 调用，挂上受击判定与冷却清理的回调。
     *
     * <p>用的是「伤害即将落下」这个判定点：游戏在 1.20.1 没有提供「伤害已经落下之后」的事件，
     * 而这里只是给玩家加一段速度、对伤害本身毫无意见，因此在落下之前动手与之后动手没有区别，
     * 玩家感受到的都是「挨了这一下，同时跑快了」。</p>
     */
    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(EnchantedRabbitFootEffect::allowDamage);

        // 冷却记录**不随下线清除**：以前在这里把玩家那条记录删掉，结果是退出重进立刻又能触发，
        // 两分钟的冷却等于白给。同一批能力里，守护（记在物品数据上）与引魂灯（同样记在物品数据上）
        // 都能跨重登保持，只有这里漏了，三条规矩不一致。
        //
        // 为什么不干脆写进存档：写玩家存档要 mixin 玩家 NBT，代价大；写进物品 NBT 更糟——
        // 身上带两个兔脚就会变成两条各自独立的冷却，反而是新漏洞。
        // 因此保持「按玩家记在内存里」的语义，只是不再下线清空。已知代价：**服务器重启会重置冷却**，
        // 重启窗口内的这一次冷却会被放过去，接受这一点。
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % CLEANUP_INTERVAL_TICKS != 0) {
                return;
            }

            // 顺手清掉已经到点的记录。判定那行用的是世界总刻数（now < READY_AT.getOrDefault(uuid, 0L)），
            // 因此过期的记录与「没有这条记录」完全等价，清掉它不会改动任何行为，
            // 只是别让这张表随着玩家来来去去一直涨下去。取主世界的总刻数：这口钟一路向前，
            // 也不受 /time set 影响，正是全服通用的计时。
            long now = server.getOverworld().getTime();
            READY_AT.entrySet().removeIf(entry -> entry.getValue() <= now);
        });
    }

    /**
     * 在伤害即将落下时决定要不要给速度。
     *
     * <p>无论是否出手都返回 {@code true}：本能力从不让伤害落空，只是搭一次顺风车。</p>
     *
     * @param entity 受伤的实体
     * @param source 伤害来源
     * @param amount 这一击本身的伤害数值（本能力不使用，但必须与事件签名一致）
     * @return 恒为 {@code true}，让伤害照常结算
     */
    private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayerEntity player)) {
            return true;
        }

        if (player.isDead() || player.isInvulnerableTo(source)) {
            return true;
        }

        if (!AttackDamage.isAttack(source)) {
            return true;
        }

        // 刚挨过打的短时间里，游戏本来就还会挡掉后续伤害（无敌帧）。
        // 这段时间内不出手，免得把一次两分钟的冷却耗在一下根本没落下来的攻击上；
        // 代价是紧接着的连击只认第一下，而第一下已经触发过了，实际并不吃亏。
        if (player.hurtTime > 0) {
            return true;
        }

        if (!CarriedStacks.inEffect(player, ModItems.ENCHANTED_RABBIT_FOOT)) {
            return true;
        }

        long now = player.getServerWorld().getTime();
        if (now < READY_AT.getOrDefault(player.getUuid(), 0L)) {
            return true;
        }

        READY_AT.put(player.getUuid(), now + COOLDOWN_TICKS);
        applySpeed(player);
        return true;
    }

    /**
     * 给玩家换上一段速度。
     *
     * <p>身上本来没有速度时给「速度 II」；已经有速度时改为在原有等级之上再抬两级，
     * 因此「速度 I」会变成「速度 III」、「速度 II」会变成「速度 IV」——身上越亮，
     * 这一下赚得越多。</p>
     *
     * <p>持续时间取「5 秒」与「原有剩余时间」中较长的那一个：升级不该把玩家原本更长的
     * 速度（例如信标给的那份）截短，只把它拔高。</p>
     *
     * @param player 触发了兔脚的玩家
     */
    private static void applySpeed(ServerPlayerEntity player) {
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.SPEED);
        int amplifier = current == null ? BASE_AMPLIFIER : current.getAmplifier() + UPGRADE_STEPS;
        int duration = current == null ? DURATION_TICKS : Math.max(DURATION_TICKS, current.getDuration());

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, duration, amplifier));
    }
}
