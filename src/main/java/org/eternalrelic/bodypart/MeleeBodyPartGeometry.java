package org.eternalrelic.bodypart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * <h1>近战这一刀落在哪儿 —— 按双方站位算出来的概率</h1>
 *
 * <p>箭有飞行轨迹，命中点可以算（见 {@link BodyPartResolver}）；近战是贴脸砍，游戏从不告诉你刀
 * 落在哪儿。这里接手这件事，问的是一句人话：<b>「他站在你哪一边，那一边的哪儿最够得着？」</b></p>
 *
 * <h2>两条规矩</h2>
 * <ol>
 *   <li><b>够不够得着</b>——攻击者的攻击部位（{@link #referencePointOf 参考点}）落在玩家身体
 *       的那一侧之外时，<b>另一侧的部位直接出局</b>。左侧来的敌人摸不到你的右肩，因为要绕过
 *       你的身体；背后捅过来的刀够不到正胸，因为胸口那一面朝着另一边。出局是<b>硬性</b>的，
 *       不管他站多远都一样。</li>
 *   <li><b>哪一块更容易</b>——够得着的部位之间，按「离参考点多近」分配权重：<b>近的更容易中</b>，
 *       权重取距离平方的倒数（权重 ∝ 1 ÷ 距离²）。这一条负责手感，也是「矮怪打腿、高个砸头」
 *       的来源，因为参考点的高度是按攻击者身高算出来的。</li>
 * </ol>
 *
 * <h2>参考点为什么取身高的 55%</h2>
 * <p>{@link #REFERENCE_HEIGHT_RATIO} 是<b>唯一一个纯手感数值</b>，制作者可随时调：</p>
 * <ul>
 *   <li>取 <b>50%</b>（也就是碰撞箱正中心）时，参考点落在腰上，腿和腹部的权重最大。</li>
 *   <li>取得<b>越高</b>，头与胸的权重越大、腿越小。抬到 76% 左右时头与胸会打成平手。</li>
 *   <li>当前取 <b>55%</b>：比正中心略高一点，让上半身比「纯按中心算」稍占优，
 *       但腿脚仍然是一个真正常见的落点。</li>
 * </ul>
 * <p>参考点的<b>横向</b>直接取攻击者的碰撞箱中心。不能用实体的「位置」当中心——那个位置给的是
 * <b>脚底</b>，照着算就永远只能打到腿。</p>
 *
 * <h2>为什么不再抽签</h2>
 * <p>早先的版本按一张固定权重表抽签（正胸 25%、头 10% 那一套）。那张表的毛病是<b>不知道双方站在
 * 哪儿</b>：从背后捅你背部的一刀，照样有 25% 的概率报成「正胸」。改成按站位算之后，
 * 同样的这一刀会老老实实落在后背或肩上。</p>
 *
 * <p>本类是纯计算，不读也不改任何游戏状态；随机只在调用方抽签时用一次。</p>
 *
 * @see BodyPartResolver 身体各部位的范围（两边共用同一批盒子）
 * @see MeleeBodyPartDetector 什么时候调用这里、算完怎么广播出去
 */
public final class MeleeBodyPartGeometry {

    /**
     * 参考点取在攻击者身高的百分之多少处 —— <b>要调近战手感就改这一个数</b>。
     *
     * <p>它是本类唯一的手感旋钮：调高则头胸更容易中，调低则腿脚更容易中（详见类文档）。</p>
     */
    public static final double REFERENCE_HEIGHT_RATIO = 0.55;

    /**
     * 横向容差：参考点横着偏出玩家身体这么多以外，<b>远侧的肩膀</b>就够不着了。
     *
     * <p>取 0.3 正是玩家碰撞箱的半宽。也就是说，敌人站在你身体正前方那条带子里时两侧肩膀都能打；
     * 一旦偏到身体侧面之外，就只能打到靠近他的那一边。</p>
     */
    private static final double SIDE_REACH = 0.3;

    /**
     * 前后容差：参考点偏到玩家身体前后这么深以外，<b>另一面</b>的躯干就够不着了。
     *
     * <p>取 0.15 正是躯干盒子的半深。正面来的够得着正胸与腹部，背后来的够得着后背；
     * 正侧面来的（前后偏移在这一线之内）两面都够得着，谁中签按距离分。</p>
     */
    private static final double DEPTH_REACH = 0.15;

    /**
     * 距离平方上摊的一点零头，免得参考点<b>正好</b>落在某块的正中心时权重除出无穷大。
     *
     * <p>0.01 相当于「最近也算 0.1 格」；贴脸时它只起平滑作用，不会让某一块吃掉全部概率。</p>
     */
    private static final double DISTANCE_EPSILON = 0.01;

    /**
     * 每个部位「从哪一边才够得着」。
     *
     * <p>没登记在这里的部位<b>从哪一边都够得着</b>：后脑勺也是头，从背后砍腿一样砍得着，
     * 头与腿脚本来就没有背面可言。</p>
     */
    private static final Map<BodyPart, Facing> FACINGS = Map.of(
            BodyPart.LEFT_SHOULDER, new Facing(-1.0, 0.0, SIDE_REACH),
            BodyPart.RIGHT_SHOULDER, new Facing(1.0, 0.0, SIDE_REACH),
            BodyPart.ABDOMEN, new Facing(0.0, 1.0, DEPTH_REACH),
            BodyPart.CHEST, new Facing(0.0, 1.0, DEPTH_REACH),
            BodyPart.BACK, new Facing(0.0, -1.0, DEPTH_REACH));

    private MeleeBodyPartGeometry() {
    }

    /**
     * 算出攻击者这一击的「出手点」，也就是概率的圆心。
     *
     * <p>横向与前后取他的碰撞箱中心，高度取身高乘上 {@link #REFERENCE_HEIGHT_RATIO}。
     * {@code getPos()} 给的是脚底中心，所以横向前后本来就是中心，只有高度需要自己加上去。</p>
     *
     * <p>用攻击者<b>当前</b>身高而不是一个固定数，是为了让体型自己说话：蜘蛛出手点在小腿高度，
     * 于是它主要咬你的腿；铁傀儡与末影人出手点在胸以上，于是它们砸你的上半身与头顶。</p>
     *
     * @param attacker 下手的那一方
     * @return 世界坐标下的参考点
     */
    public static Vec3d referencePointOf(LivingEntity attacker) {
        return attacker.getPos().add(0.0, REFERENCE_HEIGHT_RATIO * attacker.getHeight(), 0.0);
    }

    /**
     * 算出这一击各部位的相对权重。
     *
     * <p>够不着的部位不会出现在结果里；权重之和不必是 1，调用方按总和归一即可。
     * 返回的顺序跟着 {@link BodyPartResolver#PART_BOXES} 走，因此同一场景下的结果稳定可复现。</p>
     *
     * @param player         挨打的那个玩家
     * @param referencePoint 攻击者的出手点（见 {@link #referencePointOf}）
     * @return 各个够得着的部位与它的权重，至少一项
     */
    public static List<PartWeight> weightsFor(PlayerEntity player, Vec3d referencePoint) {
        Vec3d local = BodyPartResolver.toBodyLocal(player, referencePoint);

        List<PartWeight> reachable = new ArrayList<>();
        for (BodyPartResolver.PartBox candidate : BodyPartResolver.PART_BOXES) {
            if (isReachable(candidate.part(), local)) {
                reachable.add(new PartWeight(candidate.part(), weightOf(candidate.box(), local)));
            }
        }

        // 规矩彼此打架时（离谱的站位、将来加了新规矩都可能有），至少要把全部部位放回来，
        // 免得出现「这一击明明打中了人，却算不出打在哪儿」这种空洞。
        if (reachable.isEmpty()) {
            for (BodyPartResolver.PartBox candidate : BodyPartResolver.PART_BOXES) {
                reachable.add(new PartWeight(candidate.part(), weightOf(candidate.box(), local)));
            }
        }

        return reachable;
    }

    /**
     * 这个部位从攻击者所在的那一侧够不够得着。
     *
     * @param part  部位
     * @param local 参考点在玩家身体坐标系里的位置
     * @return 够得着为 {@code true}；没有登记朝向规矩的部位一律为 {@code true}
     */
    private static boolean isReachable(BodyPart part, Vec3d local) {
        Facing facing = FACINGS.get(part);
        return facing == null || facing.reachable(local.x, local.z);
    }

    /**
     * 一个部位的权重：越近越重，按距离平方反比。
     *
     * @param box   部位盒子
     * @param local 参考点在玩家身体坐标系里的位置
     * @return 权重（恒为正）
     */
    private static double weightOf(Box box, Vec3d local) {
        double distance = box.getCenter().distanceTo(local);
        return 1.0 / (distance * distance + DISTANCE_EPSILON);
    }

    /**
     * 一条「从哪一边才够得着」的规矩。
     *
     * @param sideFacing  这个部位朝着横向的哪一边（负 = 玩家左手边、正 = 右手边、0 = 不看横向）
     * @param depthFacing 这个部位朝着前后的哪一边（正 = 身体正面、负 = 背面、0 = 不看前后）
     * @param tolerance   允许参考点越过身体中线多少（单位：格）
     */
    private record Facing(double sideFacing, double depthFacing, double tolerance) {

        /**
         * @param side  参考点的横向坐标（正为玩家右手边）
         * @param depth 参考点的前后坐标（正为玩家面朝的方向）
         * @return 参考点是否落在本部位够得着的那一侧
         */
        boolean reachable(double side, double depth) {
            return this.sideFacing * side + this.depthFacing * depth >= -this.tolerance;
        }
    }

    /**
     * 一个部位与它在这一击里的相对权重。
     *
     * @param part   部位
     * @param weight 相对权重（与其他部位比大小即可，不必凑成 1）
     */
    public record PartWeight(BodyPart part, double weight) {
    }
}
