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

import org.eternalrelic.relic.NightwatchParticleEffect;

/**
 * 守夜之瞳装取时的一颗微小光点 —— 萤火虫大小，深蓝为主，少数是发光的亮蓝点。
 *
 * <p>两种走法：<b>收拢</b>时朝汇聚点加速收束，抵达即消失；<b>飞散</b>时带着随机初速度向外飘，
 * 一路减速直到寿命耗尽。两者都刻意做得又小又少，只在那一瞬闪一下，不喧宾夺主。</p>
 */
@Environment(EnvType.CLIENT)
public class NightwatchSparkParticle extends SpriteBillboardParticle {

    /** 深蓝主色所占的比例，其余为发光的亮蓝点。 */
    private static final float DEEP_BLUE_CHANCE = 0.72F;

    /** 收拢时每刻的牵引强度，越接近汇聚点拉得越紧。 */
    private static final double CONVERGE_PULL = 0.17D;

    /** 飞散时每刻的速度保留率，数值越小停得越快。 */
    private static final double DIVERGE_DAMPING = 0.90D;

    /** 离汇聚点多近就算抵达（距离的平方）。 */
    private static final double ARRIVE_DISTANCE_SQUARED = 0.09D;

    /** 汇聚点（装取那一刻的头部位置）。 */
    private final double centerX;
    private final double centerY;
    private final double centerZ;

    /** 是否朝汇聚点收拢。 */
    private final boolean converging;

    NightwatchSparkParticle(ClientWorld world, double x, double y, double z,
                            NightwatchParticleEffect effect, SpriteProvider spriteProvider) {
        super(world, x, y, z, 0.0D, 0.0D, 0.0D);
        this.setSprite(spriteProvider.getSprite(this.random));

        this.centerX = effect.centerX();
        this.centerY = effect.centerY();
        this.centerZ = effect.centerZ();
        this.converging = effect.converging();

        // 萤火虫大小：比枝叶的搏动粒子小一半还多，只在眼角余光里闪一下
        this.scale = 0.050F + this.random.nextFloat() * 0.045F;
        // 收拢的那批路程短、很快到；飞散的那批要飘出去，所以给长一点的寿命
        this.maxAge = this.converging ? 22 + this.random.nextInt(7) : 24 + this.random.nextInt(9);
        // 不受重力、也不被方块挡住：否则光点会往下掉或贴在墙上，就不像"光"了
        this.collidesWithWorld = false;
        this.gravityStrength = 0.0F;

        // 深蓝为主，掺少量发光的亮蓝点——全是深蓝会显得死，全靠亮蓝又会太抢眼
        if (this.random.nextFloat() < DEEP_BLUE_CHANCE) {
            this.setColor(0.16F, 0.30F, 0.78F);
        } else {
            this.setColor(0.60F, 0.86F, 1.00F);
        }

        if (!this.converging) {
            // 飞散：给一个随机方向的初速度；俯仰上略微偏上，看着像是从眼眶里"冒"出来
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double rise = (this.random.nextDouble() - 0.30D) * 0.6D;
            double speed = 0.055D + this.random.nextDouble() * 0.050D;
            this.velocityX = Math.cos(angle) * speed;
            this.velocityY = rise * speed * 1.4D;
            this.velocityZ = Math.sin(angle) * speed;
        }
    }

    /**
     * 推进粒子：收拢的往头部收，飞散的逐渐散开减速。
     *
     * <p>先让父类按<b>上一刻</b>的速度移动，再更新速度留给下一刻，因此轨迹是连续的。</p>
     */
    @Override
    public void tick() {
        super.tick();

        if (this.converging) {
            double progress = (double) this.age / (double) this.maxAge;
            double pull = CONVERGE_PULL * (0.4D + progress);
            this.velocityX += (this.centerX - this.x) * pull;
            this.velocityY += (this.centerY - this.y) * pull;
            this.velocityZ += (this.centerZ - this.z) * pull;
            this.velocityX *= 0.86D;
            this.velocityY *= 0.86D;
            this.velocityZ *= 0.86D;

            double dx = this.x - this.centerX;
            double dy = this.y - this.centerY;
            double dz = this.z - this.centerZ;
            if (dx * dx + dy * dy + dz * dz < ARRIVE_DISTANCE_SQUARED) {
                this.markDead();
            }
        } else {
            this.velocityX *= DIVERGE_DAMPING;
            this.velocityY *= DIVERGE_DAMPING;
            this.velocityZ *= DIVERGE_DAMPING;
        }
    }

    /**
     * 让光点在寿命两端收细、中段最亮，闪一下就隐去。
     *
     * @param tickDelta 当前帧在两次游戏刻之间的插值进度
     * @return 本帧的粒子尺寸
     */
    @Override
    public float getSize(float tickDelta) {
        float progress = MathHelper.clamp((this.age + tickDelta) / this.maxAge, 0.0F, 1.0F);
        float fade = 1.0F - (float) Math.pow(2.0D * progress - 1.0D, 2.0D);
        return this.scale * (0.45F + 0.55F * fade);
    }

    /**
     * @return 取"不受光照"的渲染层，因此这些光点在暗处同样是亮的
     */
    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_LIT;
    }

    /**
     * 粒子的工厂，供客户端注册表引用。
     */
    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<NightwatchParticleEffect> {

        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(NightwatchParticleEffect effect, ClientWorld world,
                                       double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ) {
            return new NightwatchSparkParticle(world, x, y, z, effect, this.spriteProvider);
        }
    }
}
