package org.eternalrelic.capability.attached;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;

import org.eternalrelic.capability.carried.CarriedStacks;
import org.eternalrelic.registry.ModItems;
import org.eternalrelic.relic.AttackDamage;

/**
 * 「繁花绽放」能力：缝在胸甲上的繁花纹章，在穿着它的人挨了<b>近战</b>打之后绽开一次——
 * 当场随机得到一条 1 级增益，持续 10 秒。
 *
 * <p>它与「定时馈赠」（勇气纹章）和「受击加速」（附魔兔脚）同类，都是<b>挨打换 buff、既不挡伤害
 * 也不改伤害</b>。差别在这一条给的是<b>随机一条</b>，而不是事先说好的那一条——因此每一场混战
 * 拿到的东西都不一样，这正是这件遗物的趣味所在。</p>
 *
 * <p><b>为什么只认近战</b>：远程与近战的分法沿用本项目既有的口径——看伤害的<b>直接来源</b>
 * 是不是一个生物（近战攻击的直接来源就是下手的那一方，箭与火球的直接来源是飞行中的弹射物），
 * 与近战部位判定（{@code MeleeBodyPartDetector}）完全一致。若这里另立一套标准，
 * 同一刀会出现「部位判定算近战、纹章却不算」这种自相矛盾的结果。</p>
 *
 * <p><b>放在背包里就生效</b>，缝在胸甲上也算——两条来源一并认，与太阳 / 月亮纹章同一条口径。
 * 想缝到盔甲上时只能走遗物装卸台、而且只能缝胸甲，但那是「怎么附上去」的规矩，
 * 与「怎样才能生效」是两件事。</p>
 *
 * <p><b>「不与原有 buff 叠加」是怎么落实的</b>：抽签只在<b>玩家身上还没有的那些效果</b>里进行。
 * 因此这一下永远不会把玩家自己更长的药水截短，也不会把等级抬到 II 级——身上已经有的那条，
 * 纹章连碰都不碰。十一种恰好全在身上的极端情况（几乎不可能）下这一次什么也不给，
 * 而且<b>不占用冷却</b>。</p>
 */
public final class BloomEmblemEffect {

    /** 绽放出来那一条增益的持续时间。200 刻 = 10 秒。 */
    private static final int DURATION_TICKS = 200;

    /**
     * 给到的等级。游戏中 0 级写作「速度 I」，因此 1 级 = 0。
     *
     * <p>制作者定的是「1 级增益」，也就是药水最普通的那一档；不随挨打次数变强。</p>
     */
    private static final int AMPLIFIER = 0;

    /** 两次绽放之间的冷却。300 刻 = 15 秒。 */
    private static final int COOLDOWN_TICKS = 300;

    /** 每隔多少刻清一次已经过期的冷却记录。1200 刻 = 1 分钟。 */
    private static final int CLEANUP_INTERVAL_TICKS = 1200;

    /**
     * 可以绽放出来的十一条增益 —— <b>制作者选定的名单</b>。
     *
     * <p>挑的都是「一眼看得出效果」的那一类：跑得快、挖得快、打得疼、跳得高、扛得住、
     * 回血、金心、抗火、水下呼吸、摔不伤、运气好。</p>
     *
     * <p>刻意<b>没有</b>收进来的那几条，各有各的理由：<b>隐身</b>与<b>夜视</b>会突然改变画面观感，
     * 抽到的人只会觉得屏幕出了毛病；<b>饱和</b>、<b>生命提升</b>、<b>海豚的恩惠</b>、
     * <b>潮涌能量</b>在陆地上几乎看不出变化；<b>村庄英雄</b>与战斗无关。</p>
     */
    private static final List<StatusEffect> BLOOMS = List.of(
            StatusEffects.SPEED,
            StatusEffects.HASTE,
            StatusEffects.STRENGTH,
            StatusEffects.JUMP_BOOST,
            StatusEffects.RESISTANCE,
            StatusEffects.REGENERATION,
            StatusEffects.ABSORPTION,
            StatusEffects.FIRE_RESISTANCE,
            StatusEffects.WATER_BREATHING,
            StatusEffects.SLOW_FALLING,
            StatusEffects.LUCK);

    /**
     * 记录每位玩家何时可以再次绽放：玩家编号 → 冷却结束时的世界刻数。
     *
     * <p>与附魔兔脚、余烬吊坠同一套规矩：记在内存里、不随下线清除。已知代价是服务器重启
     * 会把这 15 秒放过去，可以接受。</p>
     */
    private static final Map<UUID, Long> READY_AT = new HashMap<>();

    private BloomEmblemEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，挂上挨打判定与冷却清理的回调。
     */
    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(BloomEmblemEffect::allowDamage);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % CLEANUP_INTERVAL_TICKS != 0) {
                return;
            }

            long now = server.getOverworld().getTime();
            READY_AT.entrySet().removeIf(entry -> entry.getValue() <= now);
        });
    }

    /**
     * 在伤害即将落下时决定要不要绽放。
     *
     * <p>无论是否出手都返回 {@code true}：这件纹章从不让伤害落空，也不改动伤害数值。</p>
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

        // 只认近战：直接来源得是一个生物（与近战部位判定同一口径）
        if (!(source.getSource() instanceof LivingEntity attacker) || attacker == player) {
            return true;
        }

        // 无敌帧里不出手，免得把 15 秒冷却耗在一下根本没落下来的攻击上
        if (player.hurtTime > 0) {
            return true;
        }

        // 放在背包里就生效，缝在胸甲上也算 —— inEffect 把这两条来源一起问了。
        // 这里不必挑部位：缝上去那条路本来就只允许附到胸甲上
        if (!CarriedStacks.inEffect(player, ModItems.BLOOM_EMBLEM)) {
            return true;
        }

        long now = player.getServerWorld().getTime();
        if (now < READY_AT.getOrDefault(player.getUuid(), 0L)) {
            return true;
        }

        StatusEffect bloom = drawBloom(player);
        if (bloom == null) {
            // 十一条全在身上：这一次什么也不给，也不占用冷却
            return true;
        }

        READY_AT.put(player.getUuid(), now + COOLDOWN_TICKS);
        player.addStatusEffect(new StatusEffectInstance(bloom, DURATION_TICKS, AMPLIFIER));
        return true;
    }

    /**
     * 从「玩家身上还没有的那些增益」里随机抽一条。
     *
     * <p><b>先筛掉已有的再抽</b>，而不是先抽再看有没有——两种做法都能做到「不叠加」，
     * 但先筛的做法保证每一次绽放都真的给到点什么；先抽后看则会有一半概率白挨一下。</p>
     *
     * @param player 挨打的玩家
     * @return 抽中的那条增益；十一条全在身上时返回 {@code null}
     */
    private static StatusEffect drawBloom(ServerPlayerEntity player) {
        List<StatusEffect> available = new ArrayList<>(BLOOMS.size());

        for (StatusEffect effect : BLOOMS) {
            if (player.getStatusEffect(effect) == null) {
                available.add(effect);
            }
        }

        if (available.isEmpty()) {
            return null;
        }

        return available.get(player.getRandom().nextInt(available.size()));
    }
}
