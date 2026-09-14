package org.eternalrelic.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

import org.eternalrelic.relic.AotaPulseParticleEffect;

/**
 * 奥塔的搏动粒子 —— 随搏动声两轮「涌出 → 停驻 → 飞回胸口」的绿色光点。
 *
 * <p>两轮的时间线与音频的两次心跳峰值对齐：每轮先沿各自随机方向向外扩散成不规则球面，
 * 到达峰值后短暂停驻，再加速飞向玩家<b>此刻</b>的胸口位置——目标实时取自玩家当前位置，
 * 因此玩家走动时粒子会朝人飞，而不是回到拾取时的旧位置。抵达后立即消失，不再回弹。</p>
 */
@Environment(EnvType.CLIENT)
public class AotaPulseParticle extends SpriteBillboardParticle {

    /** 粒子的基础大小。 */
    private static final float BASE_SCALE = 0.2F;

    /** 停驻阶段的速度保留率，制造"定格一下"的观感。 */
    private static final double HOLD_DAMPING = 0.35D;

    /** 飞回胸口时的加速度系数。 */
    private static final double FLIGHT_PULL = 0.42D;

    /** 粒子到胸口多近就算抵达。 */
    private static final double ARRIVE_DISTANCE = 0.35D;

    /** 球心（触发时玩家的胸口）。 */
    private double centerX;
    private double centerY;
    private double centerZ;

    /** 这颗粒子向外扩散的最大半径。 */
    private final double sphereRadius;

    /** 时间轴快慢倍率，越大两轮走得越快。 */
    private final double cycleSpeed;

    /** 这颗粒子所属的固定方向（单位向量）。 */
    private final double dirX;
    private final double dirY;
    private final double dirZ;

    /** 供飞回阶段定位玩家。 */
    private final ClientWorld clientWorld;

    AotaPulseParticle(ClientWorld world,
                      double x, double y, double z,
                      double dirX, double dirY, double dirZ,
                      AotaPulseParticleEffect effect,
                      SpriteProvider spriteProvider) {
        super(world, x, y, z, 0.0D, 0.0D, 0.0D);
        this.setSprite(spriteProvider.getSprite(this.random));

        this.dirX = dirX;
        this.dirY = dirY;
        this.dirZ = dirZ;
        this.centerX = effect.centerX();
        this.centerY = effect.centerY();
        this.centerZ = effect.centerZ();
        this.sphereRadius = effect.sphereRadius();
        this.cycleSpeed = effect.cycleSpeed();
        this.clientWorld = world;

        this.scale = BASE_SCALE * (0.55F + this.random.nextFloat() * 1.35F);
        // 寿命必须覆盖完整时间轴（约 62 刻），否则撑不到第二轮的扩散与飞回
        this.maxAge = 70 + this.random.nextInt(12);
        this.collidesWithWorld = false;
        this.gravityStrength = 0.0F;

        applyLeafGreen(this.random);
    }

    /**
     * 把粒子染成绿色系，并在黄绿与青绿之间拉开色相。
     *
     * <p>三档色调由红蓝两端的配比决定：红多蓝少偏黄绿，蓝多红少偏青绿，
     * 中间档保持中性绿；绿色通道始终最高，所以整体仍是绿色系，只是色相更丰富。</p>
     *
     * @param random 随机源，用于挑选色调与深浅
     */
    private void applyLeafGreen(Random random) {
        int tint = random.nextInt(3);
        float depth = random.nextFloat() * 0.35F;

        float red;
        float blue;
        switch (tint) {
            case 0 -> {
                // 黄绿：绿里透一点暖，但仍留在绿色系内，不取纯黄
                red = 0.58F + depth;
                blue = 0.34F + depth * 0.45F;
            }
            case 1 -> {
                // 青绿：绿里透一点冷，不取纯蓝
                red = 0.36F + depth * 0.45F;
                blue = 0.62F + depth * 0.5F;
            }
            default -> {
                // 中性绿
                red = 0.46F + depth * 0.6F;
                blue = 0.46F + depth * 0.6F;
            }
        }

        float green = 0.86F + random.nextFloat() * 0.14F;
        this.setColor(Math.min(1.0F, red), green, Math.min(1.0F, blue));
    }

    /**
     * 推进粒子的动画时间轴。
     *
     * <p>按存活刻数落在哪个区间，决定这颗粒子此刻是向外扩散、原地停驻，还是飞回胸口。
     * 两轮的时间边界与搏动声的两次心跳对齐。</p>
     */
    @Override
    public void tick() {
        super.tick();

        // 时间轴以「刻」为单位：两轮各约 31 刻，对齐音频的两次心跳
        final double t0 = 15.0D;   // 第一轮：扩散结束、到达峰值
        final double t1 = 19.0D;   // 停驻结束
        final double t2 = 31.0D;   // 第一轮：飞回胸口结束
        final double t3 = 46.0D;   // 第二轮：扩散结束、到达峰值
        final double t4 = 50.0D;   // 停驻结束
        final double t5 = 62.0D;   // 第二轮：飞回胸口结束

        double age = this.age / Math.max(0.2D, this.cycleSpeed);

        if (age <= t0) {
            moveAlongRay(this.ratio(age, 0.0D, t0));
        } else if (age <= t1) {
            holdPosition();
        } else if (age <= t2) {
            // 第一轮飞回：只减速停住，不消失，好继续第二轮扩散
            flyToPlayer(this.ratio(age, t1, t2), false);
        } else if (age <= t3) {
            // 第二轮以玩家此刻的胸口为球心，避免两轮重叠在同一处
            updateCenter();
            moveAlongRay(this.ratio(age, t2, t3));
        } else if (age <= t4) {
            holdPosition();
        } else {
            // 第二轮飞回：抵达胸口后消失
            flyToPlayer(this.ratio(age, t4, t5), true);
        }
    }

    /**
     * 按进度把粒子摆到「球心 + 方向 × 半径」的位置。
     *
     * @param progress 0 表示贴在球心，1 表示到达球面
     */
    private void moveAlongRay(double progress) {
        double radius = this.sphereRadius * MathHelper.clamp(progress, 0.0D, 1.0D);
        this.setPos(
                this.centerX + this.dirX * radius,
                this.centerY + this.dirY * radius,
                this.centerZ + this.dirZ * radius);
    }

    private void holdPosition() {
        this.velocityX *= HOLD_DAMPING;
        this.velocityY *= HOLD_DAMPING;
        this.velocityZ *= HOLD_DAMPING;
    }

    /**
     * 让粒子加速飞向玩家<b>此刻</b>的胸口位置。
     *
     * <p>目标点每刻重新取一次玩家当前位置，所以玩家走动时粒子会跟着追人；
     * 接近到一定距离内即算抵达。只有第二轮飞回会在抵达后消失——
     * 第一轮若也消失，粒子就撑不到第二次扩散了。</p>
     *
     * @param progress  0 表示刚开始飞，1 表示接近终点
     * @param disappear 抵达后是否消失
     */
    private void flyToPlayer(double progress, boolean disappear) {
        PlayerEntity target = nearestPlayer();
        if (target == null) {
            holdPosition();
            return;
        }

        double targetX = target.getX();
        double targetY = target.getEyeY() - 0.35D;
        double targetZ = target.getZ();

        // 越接近终点拉得越紧，形成"快速飞回"的加速感
        double pull = FLIGHT_PULL * (0.35D + MathHelper.clamp(progress, 0.0D, 1.0D));
        this.velocityX += (targetX - this.x) * pull;
        this.velocityY += (targetY - this.y) * pull;
        this.velocityZ += (targetZ - this.z) * pull;

        this.velocityX *= 0.78D;
        this.velocityY *= 0.78D;
        this.velocityZ *= 0.78D;

        double dx = this.x - targetX;
        double dy = this.y - targetY;
        double dz = this.z - targetZ;
        boolean arrived = dx * dx + dy * dy + dz * dz <= ARRIVE_DISTANCE * ARRIVE_DISTANCE;

        if (arrived) {
            if (disappear) {
                this.markDead();
            } else {
                holdPosition();
            }
        }
    }

    /**
     * @return 离球心最近的玩家（也就是触发遗物的那位）
     */
    private PlayerEntity nearestPlayer() {
        PlayerEntity closest = null;
        double best = Double.MAX_VALUE;

        for (PlayerEntity candidate : this.clientWorld.getPlayers()) {
            double dx = candidate.getX() - this.centerX;
            double dy = candidate.getY() - this.centerY;
            double dz = candidate.getZ() - this.centerZ;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < best) {
                best = distance;
                closest = candidate;
            }
        }

        return closest;
    }

    /**
     * 把球心挪到玩家此刻的胸口。
     *
     * <p>第二轮扩散前调用，使第二次张开以玩家当前所在位置为中心，
     * 而不是回到第一轮的那个旧位置。</p>
     */
    private void updateCenter() {
        PlayerEntity target = nearestPlayer();
        if (target == null) {
            return;
        }

        this.centerX = target.getX();
        this.centerY = target.getEyeY() - 0.35D;
        this.centerZ = target.getZ();
    }

    /**
     * @param value 当前刻
     * @param from  区间起点
     * @param to    区间终点
     * @return 归一化进度
     */
    private double ratio(double value, double from, double to) {
        if (to <= from) {
            return 1.0D;
        }
        return MathHelper.clamp((value - from) / (to - from), 0.0D, 1.0D);
    }

    /**
     * 让粒子随时间逐渐缩小，配合淡出形成消散感。
     *
     * @param tickDelta 当前帧在两次游戏刻之间的插值进度，用于让缩小平滑
     * @return 本帧的粒子尺寸
     */
    @Override
    public float getSize(float tickDelta) {
        float progress = MathHelper.clamp((this.age + tickDelta) / this.maxAge, 0.0F, 1.0F);
        return this.scale * (0.65F + 0.35F * (1.0F - progress));
    }

    /**
     * @return 粒子的渲染层，取半透明层以支持柔和的绿色光点
     */
    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    /**
     * 粒子的工厂，供客户端注册表引用。
     */
    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<AotaPulseParticleEffect> {

        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(AotaPulseParticleEffect effect, ClientWorld world,
                                       double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ) {
            // 生成点相对球心的指向就是这颗粒子的固定方向
            double dirX = x - effect.centerX();
            double dirY = y - effect.centerY();
            double dirZ = z - effect.centerZ();
            double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);

            if (length < 1.0E-4D) {
                dirX = 0.0D;
                dirY = 1.0D;
                dirZ = 0.0D;
                length = 1.0D;
            }

            return new AotaPulseParticle(world, x, y, z,
                    dirX / length, dirY / length, dirZ / length,
                    effect, this.spriteProvider);
        }
    }
}
