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

import org.eternalrelic.relic.SoulBurstParticleEffect;

/**
 * 魂火被收进灯里时，从玩家胸口散开的一颗青色碎屑。
 *
 * <p>它是「灵魂已经收下」的收尾：从胸口朝四面八方散成直径约三格的一圈，随即淡去。
 * 每颗的出生点方向不同，因此散出来的是一圈不规则的圆形，而不是一朵整齐的球。</p>
 *
 * <p>用的是本模组自己的柔光圆点贴图，而不是原版灵魂火——飞来的那颗魂火负责"像灵魂火"，
 * 这一圈只负责像"被吸进去的余韵"。</p>
 */
@Environment(EnvType.CLIENT)
public class SoulBurstParticle extends SpriteBillboardParticle {

    /** 向外散开用多少刻。短促一瞬即可。 */
    private static final double SPREAD_TICKS = 5.0D;

    /** 散开之后原地停住、逐渐收细，直到寿命结束。 */
    private static final double HOLD_TICKS = 6.0D;

    /** 球心（玩家胸口）。 */
    private final double centerX;
    private final double centerY;
    private final double centerZ;

    /** 这颗粒子向外扩散的距离。 */
    private final double spreadDistance;

    /** 这颗粒子所属的固定方向（单位向量）。 */
    private final double dirX;
    private final double dirY;
    private final double dirZ;

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
    SoulBurstParticle(ClientWorld world, double x, double y, double z,
                      double dirX, double dirY, double dirZ,
                      SoulBurstParticleEffect effect,
                      SpriteProvider spriteProvider) {
        super(world, x, y, z, 0.0D, 0.0D, 0.0D);
        this.setSprite(spriteProvider.getSprite(this.random));

        this.dirX = dirX;
        this.dirY = dirY;
        this.dirZ = dirZ;
        this.centerX = effect.centerX();
        this.centerY = effect.centerY();
        this.centerZ = effect.centerZ();
        this.spreadDistance = effect.spreadRadius();

        // 极小：比飞来的魂火小一圈，只当余韵
        this.scale = 0.06F + this.random.nextFloat() * 0.05F;
        this.maxAge = (int) Math.ceil(SPREAD_TICKS + HOLD_TICKS);
        // 不受重力、也不被方块挡住：它是贴着人炸开的光点
        this.collidesWithWorld = false;
        this.gravityStrength = 0.0F;

        // 灵魂火的青蓝色，深浅略作区别，免得一圈死板
        float depth = this.random.nextFloat() * 0.25F;
        this.setColor(0.20F + depth * 0.3F, 0.80F + depth * 0.2F, 1.0F);
    }

    /**
     * 分成散开与原地两段，是因为「向外散」由本类按时间轴算位置，而超级类每刻还会按速度
     * 挪一次粒子：既然位置已经自己摆好，速度就必须留在零上，粒子才会停在散到的位置收细。
     */
    @Override
    public void tick() {
        super.tick();

        if (this.age <= SPREAD_TICKS) {
            moveAlongRay(ratio(this.age, 0.0D, SPREAD_TICKS));
        } else {
            this.velocityX = 0.0D;
            this.velocityY = 0.0D;
            this.velocityZ = 0.0D;
        }
    }

    /**
     * 按进度把粒子摆到「球心 + 方向 × 距离」的位置。
     *
     * @param progress 0 表示贴在球心，1 表示散到最外圈
     */
    private void moveAlongRay(double progress) {
        double distance = this.spreadDistance * MathHelper.clamp(progress, 0.0D, 1.0D);
        this.setPos(
                this.centerX + this.dirX * distance,
                this.centerY + this.dirY * distance,
                this.centerZ + this.dirZ * distance);
    }

    /**
     * 散开后逐渐收细，消失时不显得突兀。
     *
     * @param tickDelta 当前帧在两次游戏刻之间的插值进度
     * @return 本帧的粒子尺寸
     */
    @Override
    public float getSize(float tickDelta) {
        float progress = MathHelper.clamp((this.age + tickDelta) / this.maxAge, 0.0F, 1.0F);
        return this.scale * (1.0F - progress * progress);
    }

    /**
     * @return 粒子的渲染层，取不受世界光照的一层——暗处也该是亮的
     */
    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_LIT;
    }

    /**
     * @param value 当前刻
     * @param from  区间起点
     * @param to    区间终点
     * @return 归一化进度
     */
    private static double ratio(double value, double from, double to) {
        if (to <= from) {
            return 1.0D;
        }
        return MathHelper.clamp((value - from) / (to - from), 0.0D, 1.0D);
    }

    /**
     * 粒子的工厂，供客户端注册表引用。
     */
    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<SoulBurstParticleEffect> {

        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(SoulBurstParticleEffect effect, ClientWorld world,
                                       double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ) {
            // 出生点相对球心的指向，就是这颗粒子向外扩散的方向
            double dirX = x - effect.centerX();
            double dirY = y - effect.centerY();
            double dirZ = z - effect.centerZ();
            double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);

            if (length < 1.0E-4D) {
                // 正好生在球心上时给个朝上的方向，免得除以零
                dirX = 0.0D;
                dirY = 1.0D;
                dirZ = 0.0D;
                length = 1.0D;
            }

            return new SoulBurstParticle(world, x, y, z,
                    dirX / length, dirY / length, dirZ / length,
                    effect, this.spriteProvider);
        }
    }
}
