package org.eternalrelic.mixin;

import java.util.Optional;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import org.eternalrelic.bodypart.BodyPart;
import org.eternalrelic.bodypart.BodyPartHits;
import org.eternalrelic.bodypart.BodyPartResolver;
import org.eternalrelic.bodypart.ProjectileBodyPartHit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在弹射物撞到玩家的那一刻，把「打中哪个部位」算出来并广播出去。
 *
 * <p><b>为什么挂在 {@code ProjectileEntity} 上</b>：撞到东西后走的是这一个 {@code onCollision}，
 * 箭、三叉戟、雪球、火球等一切弹射物都从这里过一遍。挂在这一层就能一次覆盖全部弹射物，
 * 不必逐个类型写。</p>
 *
 * <p><b>为什么只要服务端玩家</b>：客户端也跑同一份弹射物代码，但那里的「玩家」是本地视角的替身，
 * 算出来的结果当不得准，而且客户端算完也没人需要。这里只认 {@code ServerPlayerEntity}，
 * 于是判定天然只在服务端发生，联机时不会出现各算各的。</p>
 *
 * <p>只读取命中结果，不修改原方法的行为，因此注入后不影响原版弹射物的任何表现。</p>
 */
@Mixin(ProjectileEntity.class)
public abstract class ProjectileEntityMixin {

    /**
     * 游戏在检测「弹射物是否擦到实体」时，把实体轮廓向外放宽的距离。
     *
     * <p>这个 0.3 正是原版 {@code ProjectileUtil#getEntityCollision} 的默认放宽值，所以这里
     * 「算没算中」与游戏「认没认中」用的是同一圈轮廓，不会出现游戏认为射中了、这里却算不出交点。
     * 放宽之后的交点可能落在身体外面的空气里，因此只在真实轮廓求不出交点时才退而用之。</p>
     */
    @Unique
    private static final double TARGETING_MARGIN = 0.3;

    /**
     * 在弹射物处理碰撞之前，抢先记下这次命中打在玩家哪个部位。
     *
     * @param hitResult     本次碰撞的结果，只有其中的「撞到实体」才是这里关心的
     * @param callbackInfo  原方法（无返回值）的回调；这里不取消，原版流程照常继续
     */
    @Inject(method = "onCollision", at = @At("HEAD"))
    private void eternal_relic$locateBodyPart(HitResult hitResult, CallbackInfo callbackInfo) {
        if (!(hitResult instanceof EntityHitResult entityHit)) {
            return;
        }
        if (!(entityHit.getEntity() instanceof ServerPlayerEntity player)) {
            return;
        }

        ProjectileEntity projectile = (ProjectileEntity) (Object) this;
        Vec3d hitPos = locateHitPoint(projectile, player);

        // 求不出交点，说明这一刻弹射物并没有真的穿过身体。
        // 射中之后弹射物还会在身上停留一段时间，每个游戏刻都会重复报同一次碰撞，
        // 而那些时刻它已经不在身体里了——那种重复必须丢掉，否则真正的第一次会被它盖过去。
        if (hitPos == null) {
            return;
        }

        BodyPart part = BodyPartResolver.resolve(player, hitPos);
        BodyPartHits.fire(new ProjectileBodyPartHit(player, projectile, part, hitPos));
    }

    /**
     * 求这次命中打在身体上的哪个点。
     *
     * <p><b>为什么不能直接问游戏要</b>：原版在检测命中时<b>确实算出过</b>这个点——它拿射线的
     * 飞行段去和实体轮廓求交，用交点挑出最近的那个实体——但构造「命中结果」时，它<b>把那个交点丢掉了</b>，
     * 只把被击中者自身的位置（也就是脚下的落脚点）放进结果里。所以从命中结果里读到的永远是脚底。</p>
     *
     * <p><b>飞行段的方向</b>：这里必须和原版一致。原版在<b>移动弹射物之前</b>就做碰撞检测，
     * 此时 {@code getPos()} 给的是<b>本刻起点</b>，检测用的线段是「起点 → 起点 + 本刻速度」。
     * 若反过来用「起点 − 速度 → 起点」，那条线段会落在弹射物<b>还没飞到</b>的位置上，
     * 与身体根本不相交——表现就是无论射中哪里都得不到命中点。</p>
     *
     * <p><b>先用真实轮廓，再退到放宽轮廓</b>：放宽后的轮廓比身体大一圈，交点可能是身体外面的空气，
     * 斜射时会把命中点抬到身体上方。真实轮廓的交点才是弹射物真正扎进去的位置。</p>
     *
     * @return 打在身体上的点；这一刻并没有真的穿过身体时返回 {@code null}
     */
    @Unique
    private static Vec3d locateHitPoint(ProjectileEntity projectile, PlayerEntity player) {
        Vec3d departure = projectile.getPos();
        Vec3d arrival = departure.add(projectile.getVelocity());
        Box body = player.getBoundingBox();

        Optional<Vec3d> hit = body.raycast(departure, arrival);
        if (hit.isEmpty()) {
            hit = body.expand(TARGETING_MARGIN).raycast(departure, arrival);
        }
        return hit.orElse(null);
    }
}
