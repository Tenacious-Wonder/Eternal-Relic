package org.eternalrelic.bodypart;

import java.util.List;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.random.Random;

import org.eternalrelic.relic.AttackDamage;

/**
 * <h1>近战受击部位判定</h1>
 *
 * <p>玩家被近战打中时，抽一个部位出来并广播（{@link BodyPartHits}）。与弹射物那一套的区别只有一点：
 * 箭有明确的飞行轨迹，命中点可以算（{@link BodyPartResolver}）；近战是贴脸砍，游戏根本不给出刀落在哪儿，
 * 所以这里<b>按概率抽签</b>，抽签表见 {@link #CHANCES}。</p>
 *
 * <p><b>为什么不用 mixin</b>：项目里已有 {@code ServerLivingEntityEvents.ALLOW_DAMAGE} 这条现成的路
 * （守护与附魔兔脚都挂在它上面），「挨打了没」不需要再动游戏内部代码。</p>
 *
 * <p><b>怎么把近战与远程分开</b>：看伤害的<b>直接来源</b>是不是一个生物。近战攻击的直接来源就是下手的
 * 那一方；箭、火球那类远程的直接来源是飞行中的弹射物（射手只算「主使」，不算直接来源），
 * 因此不会与 {@link org.eternalrelic.mixin.ProjectileEntityMixin} 那一套重复触发。</p>
 *
 * <p><b>运行位置</b>：事件本身只在服务端触发，联机时各人看到的部位一致。</p>
 */
public final class MeleeBodyPartDetector {

    /**
     * 各部位的中签权重 —— <b>要调手感就改这一张表</b>。
     *
     * <p>权重之和不必凑成 100，抽签时按总和归一。当前取法贴近「正面对砍」的常识：
     * 躯干最容易挨刀（正胸 + 腹部 = 45），两条肩膀一样多，头最小（10）。</p>
     */
    private static final List<Chance> CHANCES = List.of(
            new Chance(BodyPart.HEAD, 10),
            new Chance(BodyPart.CHEST, 25),
            new Chance(BodyPart.ABDOMEN, 20),
            new Chance(BodyPart.LEFT_SHOULDER, 15),
            new Chance(BodyPart.RIGHT_SHOULDER, 15),
            new Chance(BodyPart.LEGS, 15));

    private MeleeBodyPartDetector() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，挂上受击判定。
     */
    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(MeleeBodyPartDetector::allowDamage);
    }

    /**
     * 在伤害即将落下时抽签。恒返回 {@code true} —— 本判定不参与伤害结算，只是搭一次顺风车。
     *
     * @param entity 受伤的实体
     * @param source 伤害来源
     * @param amount 这一击的伤害数值（本判定不使用，但必须与事件签名一致）
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

        // 刚挨过打的短时间里，游戏本来还会挡掉后续伤害（无敌帧）。这段时间不抽签，
        // 一下挨打只报一次，免得连击时刷出一串部位。
        if (player.hurtTime > 0) {
            return true;
        }

        // 只有「直接来源是生物」才算近战；箭、火球那类留给弹射物那一套
        if (!(source.getSource() instanceof LivingEntity attacker) || attacker == player) {
            return true;
        }

        BodyPartHits.fire(new MeleeBodyPartHit(player, attacker, draw(player.getRandom())));
        return true;
    }

    /**
     * 按 {@link #CHANCES} 的权重抽一个部位。
     *
     * @param random 抽签用的随机源（取受击者自己的那一个，服务端持有）
     * @return 抽中的部位
     */
    private static BodyPart draw(Random random) {
        int total = 0;
        for (Chance chance : CHANCES) {
            total += chance.weight();
        }

        int roll = random.nextInt(total);
        for (Chance chance : CHANCES) {
            roll -= chance.weight();
            if (roll < 0) {
                return chance.part();
            }
        }

        // 走不到这里：上面的减法必然在总和耗尽之前命中某一项
        return BodyPart.CHEST;
    }

    /**
     * 抽签表的一行：某个部位占多少权重。
     *
     * @param part   部位
     * @param weight 权重（相对值，不必凑成 100）
     */
    private record Chance(BodyPart part, int weight) {
    }
}
