package org.eternalrelic.mixin;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

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
import org.eternalrelic.capability.attached.ShoulderGuardEffect;
import org.eternalrelic.debug.BodyPartHitReport;
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
     * 已经被肩甲弹开过的弹射物 → 它是在哪位玩家身上被弹开的。
     *
     * <p>被崩回去的箭速度只剩十分之一，还会在玩家身上停留几刻，于是同一个游戏刻到随后几刻里
     * 会反复报出同一次碰撞。原版举盾挡箭时靠「盾牌每次碰撞都拦下来」解决这件事，
     * 我们这里照做：进过这张表的箭，在它离开这位玩家之前，每一次碰撞都继续拦着，
     * 否则它下一刻意就会扎进身体、照常造成伤害，"弹开"就白弹了。</p>
     *
     * <p>键是弹射物实体本身，用弱引用表是为了让它被移除（扎进地里、被回收）之后自动清掉，
     * 不必另外写一处清理。</p>
     */
    @Unique
    private static final Map<ProjectileEntity, UUID> DEFLECTED = new WeakHashMap<>();

    /**
     * 在弹射物处理碰撞之前，抢先记下这次命中打在玩家哪个部位。
     *
     * @param hitResult     本次碰撞的结果，只有其中的「撞到实体」才是这里关心的
     * @param callbackInfo  原方法（无返回值）的回调；这里不取消，原版流程照常继续
     */
    @Inject(method = "onCollision", at = @At("HEAD"), cancellable = true)
    private void eternal_relic$locateBodyPart(HitResult hitResult, CallbackInfo callbackInfo) {
        if (!(hitResult instanceof EntityHitResult entityHit)) {
            return;
        }
        if (!(entityHit.getEntity() instanceof ServerPlayerEntity player)) {
            return;
        }

        ProjectileEntity projectile = (ProjectileEntity) (Object) this;

        // 已经被肩甲弹开过的这一箭还在玩家身上打转，继续拦着，别让它造成伤害
        if (player.getUuid().equals(DEFLECTED.get(projectile))) {
            callbackInfo.cancel();
            return;
        }

        Vec3d hitPos = locateHitPoint(projectile, player);

        // 求不出交点，说明这一刻弹射物并没有真的穿过身体。
        // 射中之后弹射物还会在身上停留一段时间，每个游戏刻都会重复报同一次碰撞，
        // 而那些时刻它已经不在身体里了——那种重复必须丢掉，否则真正的第一次会被它盖过去。
        if (hitPos == null) {
            return;
        }

        BodyPart part = BodyPartResolver.resolve(player, hitPos);
        ProjectileBodyPartHit hit = new ProjectileBodyPartHit(player, projectile, part, hitPos);
        BodyPartHits.fire(hit);

        // 龟壳肩甲：打中护着那一侧的箭有几率被整个弹开。这里拦下原版的命中处理
        // （于是这一箭既不造成伤害、也不会扎进身体），再照原版的动作把它崩回去
        if (ShoulderGuardEffect.deflects(player, part)) {
            DEFLECTED.put(projectile, player.getUuid());
            deflect(projectile);
            BodyPartHitReport.reportDeflected(hit);
            callbackInfo.cancel();
        }
    }

    /**
     * 把这一箭掉头崩回去 —— <b>动作照抄原版</b>。
     *
     * <p>原版在「这一击没打动对方」时就是这么处理的（例如对方举着盾，见
     * {@code PersistentProjectileEntity#onEntityHit} 的 else 分支）：速度反向并衰减到十分之一、
     * 朝向加 180 度。照抄的好处是箭随后的表现与原版完全一致——晃晃悠悠反向飘出去、
     * 落到地上，而不是凭空消失或卡在身体里。</p>
     *
     * @param projectile 被弹开的弹射物
     */
    @Unique
    private static void deflect(ProjectileEntity projectile) {
        projectile.setVelocity(projectile.getVelocity().multiply(-0.1));
        projectile.setYaw(projectile.getYaw() + 180.0F);
        projectile.prevYaw += 180.0F;
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
