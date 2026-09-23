package org.eternalrelic.capability.carried;

import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;

import org.eternalrelic.registry.ModItems;

/**
 * 「野狼慑服」能力：带着可怕狼牙吊坠时，身边的野狼会被狼王的气息镇住，当场坐下。
 *
 * <p><b>为什么不是「让它坐下」那么简单</b>：原版负责让狼坐下、并让它原地不动的那段逻辑
 * （{@code SitGoal}）开工前先问一句「这只动物被驯服了吗」，没被驯服就直接放弃。
 * 因此未驯服的狼即使被设成坐下，也只是一个没人理会的数字——既没有坐姿，也不会停下，
 * 照样在附近乱走。所以这里做的是两件事：把「野狼也允许坐下」告诉那段逻辑
 * （见 {@link org.eternalrelic.mixin.SitGoalMixin}），再照常把坐下标志设上。
 * 坐姿、停止移动、以及解除时的恢复，全部由原版负责，一行也没有复制。</p>
 *
 * <p><b>走出范围就会站起来</b>：这是一圈「气场」而不是一道「诅咒」——玩家带着吊坠在附近时
 * 狼才坐着；玩家走开、或把吊坠收起来，狼随即恢复原样。做法是让<b>每只野狼自己定期回头看一眼</b>
 * 附近有没有带着吊坠的玩家，因此模组里<b>不需要记「哪些狼被我按下了」这本账</b>，
 * 也就不会出现「账本和实际状态对不上、狼永远坐着」这类问题。</p>
 *
 * <p><b>只认野狼</b>：已经驯服的狼（包括玩家自己的狗）一概不碰——它们是伙伴，不是猎物。
 * 这一点同时也是上面那个「不必记账」能成立的前提：未驯服的狼在原版里没有任何办法
 * 把坐下标志设成 true，所以「一只野狼正坐着」这件事本身就只可能是本能力做的。</p>
 */
public final class WolfAweEffect {

    /** 慑服范围。十格。 */
    private static final double AWE_RADIUS = 10.0D;

    /** 每隔多少刻回头看一眼。10 刻 = 半秒，玩家察觉不到延迟，也不必每刻都算一遍距离。 */
    private static final int CHECK_INTERVAL_TICKS = 10;

    private WolfAweEffect() {
    }

    /**
     * 按「附近有没有带着狼牙吊坠的玩家」，决定这只狼该坐着还是该恢复原样。
     *
     * <p>由 {@link org.eternalrelic.mixin.WolfAweMixin} 在狼每刻走完自己的逻辑之后调用。
     * 只有服务端需要处理：客户端那份状态会被服务端同步覆盖，处理了也是白做。</p>
     *
     * @param wolf 待检查的狼
     */
    public static void update(WolfEntity wolf) {
        if (wolf.getWorld().isClient || wolf.isTamed() || wolf.age % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        boolean awed = isPendantBearerNearby(wolf);

        if (awed == wolf.isSitting()) {
            return;
        }

        wolf.setSitting(awed);
    }

    /**
     * 这只动物能不能「还没被驯服就坐下」。
     *
     * <p>三个条件缺一不可：是狼、还没被驯服、<b>而且此刻已经处于坐姿</b>。</p>
     *
     * <p><b>最后那一条是这里的关键，少不得。</b>原版那段逻辑在读不到主人时会直接放行，
     * 而未驯服的狼恰好没有主人——所以只判断前两条的话，<b>每一只站在地上的野狼都会当场
     * 被按住坐下、并且再也走不动</b>，与玩家带没带吊坠毫无关系。要求「它自己已经坐着」，
     * 就等于要求「这次坐下确实是本能力请它坐的」：未驯服的狼在原版里没有任何办法把坐下标志
     * 设成 true，那个标志只可能由 {@link #update} 设置。</p>
     *
     * @param tameable 待判断的动物
     * @return 是否放行
     */
    public static boolean maySitWhileUntamed(TameableEntity tameable) {
        return tameable instanceof WolfEntity wolf && !wolf.isTamed() && wolf.isSitting();
    }

    /**
     * 这只狼身边有没有带着狼牙吊坠的玩家。
     *
     * <p>先用外扩十格的方盒把附近的玩家挑出来，再用真实距离筛一遍——方块盒子在四个角上
     * 能伸到十四格远，只按盒子算的话，站得远一点也会被算进「十格以内」。</p>
     *
     * @param wolf 待检查的狼
     * @return 十格以内是否有携带该遗物的玩家
     */
    private static boolean isPendantBearerNearby(WolfEntity wolf) {
        Box area = wolf.getBoundingBox().expand(AWE_RADIUS);

        for (Entity entity : wolf.getWorld().getOtherEntities(null, area, WolfAweEffect::isServerPlayer)) {
            if (wolf.squaredDistanceTo(entity) > AWE_RADIUS * AWE_RADIUS) {
                continue;
            }

            if (CarriedStacks.inEffect((ServerPlayerEntity) entity, ModItems.DREADFUL_WOLF_FANG_PENDANT)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @param entity 待判断的实体
     * @return 是不是服务端玩家
     */
    private static boolean isServerPlayer(Entity entity) {
        return entity instanceof ServerPlayerEntity;
    }
}
