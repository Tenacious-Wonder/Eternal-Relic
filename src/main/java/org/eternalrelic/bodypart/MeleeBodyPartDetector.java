package org.eternalrelic.bodypart;

import java.util.List;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import org.eternalrelic.relic.AttackDamage;

/**
 * <h1>近战受击部位判定</h1>
 *
 * <p>玩家被近战打中时，算出这一刀落在哪个部位并广播（{@link BodyPartHits}）。与弹射物那一套的区别
 * 只有一点：箭有明确的飞行轨迹，命中点可以算（{@link BodyPartResolver}）；近战是贴脸砍，
 * 游戏根本不给出刀落在哪儿，所以改为<b>按双方站位推算</b>——攻击者的出手点够得着哪几块、
 * 其中哪一块离他最近，哪一块就更容易中签。几何算法单独放在 {@link MeleeBodyPartGeometry}，
 * 这里只负责「什么时候算」与「算完怎么广播」。</p>
 *
 * <p><b>为什么不用 mixin</b>：项目里已有 {@code ServerLivingEntityEvents.ALLOW_DAMAGE} 这条现成的路
 * （守护与附魔兔脚都挂在它上面），「挨打了没」不需要再动游戏内部代码。</p>
 *
 * <p><b>怎么把近战与远程分开</b>：看伤害的<b>直接来源</b>是不是一个生物。近战攻击的直接来源就是下手的
 * 那一方；箭、火球那类远程的直接来源是飞行中的弹射物（射手只算「主使」，不算直接来源），
 * 因此不会与 {@link org.eternalrelic.mixin.ProjectileEntityMixin} 那一套重复触发。</p>
 *
 * <p><b>为什么拿得到攻击者的位置</b>：近战攻击的直接来源就是攻击者本人，他的位置、身高、
 * 朝向在服务端都是现成的，站在哪儿就决定了刀落在哪儿——这正是几何算法需要的那点信息。</p>
 *
 * <p><b>运行位置</b>：事件本身只在服务端触发，联机时各人看到的部位一致。</p>
 *
 * @see MeleeBodyPartGeometry 这一刀打在哪儿是怎么算出来的
 */
public final class MeleeBodyPartDetector {

    private MeleeBodyPartDetector() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，挂上受击判定。
     */
    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(MeleeBodyPartDetector::allowDamage);
    }

    /**
     * 在伤害即将落下时算出手点、抽一个部位。恒返回 {@code true} —— 本判定不参与伤害结算，
     * 只是搭一次顺风车。
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

        // 刚挨过打的短时间里，游戏本来还会挡掉后续伤害（无敌帧）。这段时间不算，
        // 一下挨打只报一次，免得连击时刷出一串部位。
        if (player.hurtTime > 0) {
            return true;
        }

        // 只有「直接来源是生物」才算近战；箭、火球那类留给弹射物那一套
        if (!(source.getSource() instanceof LivingEntity attacker) || attacker == player) {
            return true;
        }

        Vec3d reference = MeleeBodyPartGeometry.referencePointOf(attacker);
        BodyPart part = draw(MeleeBodyPartGeometry.weightsFor(player, reference), player.getRandom());
        BodyPartHits.fire(new MeleeBodyPartHit(player, attacker, part));
        return true;
    }

    /**
     * 按各部位的权重抽一次签。
     *
     * <p>权重来自站位（近的、朝着攻击者的那一侧更重），所以这一抽既是随机的、也带着方向——
     * 抽出来的结果不会跑到攻击者够不着的那一边去。</p>
     *
     * @param weights 各部位的相对权重，由 {@link MeleeBodyPartGeometry#weightsFor} 给出，至少一项
     * @param random  抽签用的随机源（取受击者自己的那一个，服务端持有）
     * @return 抽中的部位
     */
    private static BodyPart draw(List<MeleeBodyPartGeometry.PartWeight> weights, Random random) {
        double total = 0.0;
        for (MeleeBodyPartGeometry.PartWeight weight : weights) {
            total += weight.weight();
        }

        double roll = random.nextDouble() * total;
        for (MeleeBodyPartGeometry.PartWeight weight : weights) {
            roll -= weight.weight();
            if (roll < 0.0) {
                return weight.part();
            }
        }

        // 正常走不到这里：上面的减法必然在总和耗尽之前命中某一项。
        // 只有浮点零头把 roll 恰好留在边界上时才会落到这儿，兜给最后一项即可。
        return weights.get(weights.size() - 1).part();
    }
}
