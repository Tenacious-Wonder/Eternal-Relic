package org.eternalrelic.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import org.eternalrelic.relic.EchoRingShardParticleEffect;

/**
 * 回响之环碎裂时迸出的一颗白色碎屑 —— 先向外扩散，原地停一下，再坠入地下。
 *
 * <p>三段走法正好对应它在游戏里要讲的那件事：遗物替你挡下攻击的瞬间向外炸开，
 * 愣住一瞬，然后像碎屑一样坠下去、沉进地面看不见。</p>
 *
 * <p>纵向不做碰撞，碎屑才能穿过地面沉下去；否则它会停在方块表面，变成"贴着地"而不是
 * "落进地里"。</p>
 */
@Environment(EnvType.CLIENT)
public class EchoRingShardParticle extends SpriteBillboardParticle {

    /** 向外扩散用多少刻。 */
    private static final double SPREAD_TICKS = 6.0D;

    /** 扩散距离的随机范围（相对参数给出的距离），让同一场碎裂里的碎屑远近不一。 */
    private static final double SPREAD_DISTANCE_MIN_RATIO = 0.8D;
    private static final double SPREAD_DISTANCE_MAX_RATIO = 1.2D;

    /** 方向被推歪的基准强度：飞行途中越偏越多，路径于是弯成弧线而不是笔直的射线。 */
    private static final double BEND_RATE = 0.9D;

    /** 扩散到位之后原地静止多少刻（0.3 秒）。 */
    private static final double HOLD_TICKS = 6.0D;

    /** 往下坠落用多少刻。 */
    private static final double FALL_TICKS = 24.0D;

    /** 坠落速度由慢到快（格/刻），做出"掉下去"的加速感。 */
    private static final double FALL_START_SPEED = 0.08D;
    private static final double FALL_END_SPEED = 0.30D;

    /** 碎屑的基础大小。 */
    private static final float BASE_SCALE = 0.07F;

    /** 球心。 */
    private final double centerX;
    private final double centerY;
    private final double centerZ;

    /** 这颗粒子向外扩散的距离。 */
    private final double spreadDistance;

    /** 这颗粒子起飞的方向（单位向量）。 */
    private final double dirX;
    private final double dirY;
    private final double dirZ;

    /** 把这颗粒子推歪的方向（与起飞方向垂直的单位向量），每颗都不同。 */
    private final double swirlX;
    private final double swirlY;
    private final double swirlZ;

    /** 这颗粒子被推歪的强度，同样每颗不同——有的绕得厉害，有的几乎笔直。 */
    private final double bendRate;

    /**
     * @param world          粒子所在的客户端世界
     * @param x              出生点 X
     * @param y              出生点 Y
     * @param z              出生点 Z
     * @param dirX           向外扩散的方向 X；方向是工厂算好的单位向量，本类不再自行归一化
     * @param dirY           向外扩散的方向 Y
     * @param dirZ           向外扩散的方向 Z
     * @param effect         服务端传来的生成参数：球心与扩散距离
     * @param spriteProvider 供它从中挑一帧贴图
     */
    EchoRingShardParticle(ClientWorld world, double x, double y, double z,
                          double dirX, double dirY, double dirZ,
                          EchoRingShardParticleEffect effect,
                          SpriteProvider spriteProvider) {
        super(world, x, y, z, 0.0D, 0.0D, 0.0D);
        this.setSprite(spriteProvider.getSprite(this.random));

        this.dirX = dirX;
        this.dirY = dirY;
        this.dirZ = dirZ;
        this.centerX = effect.centerX();
        this.centerY = effect.centerY();
        this.centerZ = effect.centerZ();

        // 远近不一：同一场碎裂里，有的碎屑飞得远、有的刚出去就落
        this.spreadDistance = effect.spreadRadius()
                * (SPREAD_DISTANCE_MIN_RATIO
                + this.random.nextDouble() * (SPREAD_DISTANCE_MAX_RATIO - SPREAD_DISTANCE_MIN_RATIO));
        this.bendRate = BEND_RATE * (0.5D + this.random.nextDouble());

        // 取一条与起飞方向垂直的随机方向作为推力方向：碎屑被推着往侧面绕，
        // 而不是被推着往回走
        double rawX = this.random.nextDouble() * 2.0D - 1.0D;
        double rawY = this.random.nextDouble() * 2.0D - 1.0D;
        double rawZ = this.random.nextDouble() * 2.0D - 1.0D;
        double along = rawX * dirX + rawY * dirY + rawZ * dirZ;
        double perpX = rawX - along * dirX;
        double perpY = rawY - along * dirY;
        double perpZ = rawZ - along * dirZ;
        double perpLength = Math.sqrt(perpX * perpX + perpY * perpY + perpZ * perpZ);

        if (perpLength < 1.0E-4D) {
            // 随机方向恰好与起飞方向平行时，换一条垂直方向兜底
            perpX = -dirY;
            perpY = dirX;
            perpZ = 0.0D;
            perpLength = Math.sqrt(perpX * perpX + perpY * perpY + perpZ * perpZ);
        }

        if (perpLength < 1.0E-4D) {
            // 起飞方向恰好落在 Z 轴上（正南/正北）时，任取一个方向兜底
            perpX = 1.0D;
            perpY = 0.0D;
            perpZ = 0.0D;
            perpLength = 1.0D;
        }

        this.swirlX = perpX / perpLength;
        this.swirlY = perpY / perpLength;
        this.swirlZ = perpZ / perpLength;

        this.scale = BASE_SCALE * (0.6F + this.random.nextFloat() * 0.8F);
        // 寿命必须覆盖整条时间轴，否则走不到最后那段落地下沉
        this.maxAge = (int) Math.ceil(SPREAD_TICKS + HOLD_TICKS + FALL_TICKS);
        // 不受重力、也不被方块挡住：碎屑要能一路沉进地面
        this.collidesWithWorld = false;
        this.gravityStrength = 0.0F;

        // 以白色为主，深浅拉开一点层次，免得整片死白
        float brightness = 0.82F + this.random.nextFloat() * 0.18F;
        this.setColor(brightness, brightness, brightness);
    }

    /**
     * 推进碎屑的三段动作：扩散 → 静止 → 坠落。
     */
    @Override
    public void tick() {
        super.tick();

        double age = this.age;
        double holdEnd = SPREAD_TICKS + HOLD_TICKS;

        if (age <= SPREAD_TICKS) {
            moveAlongRay(ParticleMath.ratio(age, 0.0D, SPREAD_TICKS));
        } else if (age <= holdEnd) {
            holdPosition();
        } else {
            fall(ParticleMath.ratio(age, holdEnd, holdEnd + FALL_TICKS));
        }
    }

    /**
     * 按进度把粒子摆到「球心 + 当前方向 × 已飞距离」的位置。
     *
     * <p>飞行途中方向会被逐渐推歪，因此每颗碎屑走的都是自己那条弧线，
     * 而不是所有粒子一起沿笔直的射线向外辐射。推歪量只取决于进度，
     * 所以飞到终点时的距离仍是这颗碎屑自己抽到的那个值。</p>
     *
     * @param progress 0 表示贴在球心，1 表示飞到最外圈
     */
    private void moveAlongRay(double progress) {
        double clamped = MathHelper.clamp(progress, 0.0D, 1.0D);

        double bend = this.bendRate * clamped;
        double nx = this.dirX + this.swirlX * bend;
        double ny = this.dirY + this.swirlY * bend;
        double nz = this.dirZ + this.swirlZ * bend;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0E-4D) {
            length = 1.0D;
        }

        double distance = this.spreadDistance * clamped;
        this.setPos(
                this.centerX + nx / length * distance,
                this.centerY + ny / length * distance,
                this.centerZ + nz / length * distance);
    }

    /**
     * 停在原地：清掉速度，位置由逐刻的时间轴接管，不会自己漂走。
     */
    private void holdPosition() {
        this.velocityX = 0.0D;
        this.velocityY = 0.0D;
        this.velocityZ = 0.0D;
    }

    /**
     * 让碎屑坠下去，速度由慢到快。
     *
     * <p>坠落的总距离特意留得比扩散半径更长：碎屑是向四面八方散开的，
     * 往上飞的那些要落回地面、再沉进土里，路程比平地起落的那些远得多。</p>
     *
     * @param progress 0 表示刚开始坠落，1 表示接近寿命终点
     */
    private void fall(double progress) {
        double clamped = MathHelper.clamp(progress, 0.0D, 1.0D);
        double speed = FALL_START_SPEED + (FALL_END_SPEED - FALL_START_SPEED) * clamped;
        this.setPos(this.x, this.y - speed, this.z);
    }

    /**
     * 坠落的后半段逐渐收细。
     *
     * <p>玩家站在地面上时碎屑早已沉进地里、被方块挡住；这一手是为了玩家悬空时
     * 也在寿命尽头收干净，不至于在空中"啪"地整颗消失。</p>
     *
     * @param tickDelta 当前帧在两次游戏刻之间的插值进度，用于让缩小平滑
     * @return 本帧的粒子尺寸
     */
    @Override
    public float getSize(float tickDelta) {
        double age = this.age + tickDelta;
        double holdEnd = SPREAD_TICKS + HOLD_TICKS;

        if (age <= holdEnd) {
            return this.scale;
        }

        float fade = (float) MathHelper.clamp((age - holdEnd) / FALL_TICKS, 0.0D, 1.0D);
        return this.scale * (1.0F - fade * fade);
    }

    /**
     * @return 粒子的渲染层，取不受世界光照的一层——碎屑要保持白色，落进暗处也不该发黑
     */
    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_LIT;
    }

    /**
     * 粒子的工厂，供客户端注册表引用。
     */
    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<EchoRingShardParticleEffect> {

        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(EchoRingShardParticleEffect effect, ClientWorld world,
                                       double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ) {
            // 出生点相对球心的指向，就是这颗粒子向外扩散的方向
            Vec3d direction = ParticleMath.unitDirection(
                    x - effect.centerX(), y - effect.centerY(), z - effect.centerZ());

            return new EchoRingShardParticle(world, x, y, z,
                    direction.x, direction.y, direction.z,
                    effect, this.spriteProvider);
        }
    }
}
