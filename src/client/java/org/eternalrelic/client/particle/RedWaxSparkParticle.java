package org.eternalrelic.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.MathHelper;

/**
 * 一块红土蜡块揉开时溅出的一颗蜡点 —— 橙色的小圆点，缓缓上浮，然后淡去。
 *
 * <p><b>为什么不直接用游戏自带的涂蜡粒子。</b>原版涂蜡粒子（{@code ParticleTypes.WAX_ON}）
 * 其实<b>就是橙色的圆点</b>：同一张 {@code minecraft:glow} 圆点贴图、同一个颜色，形状并没有错。
 * 真正的问题是它<b>太小</b>——原版把尺寸压到基础值的七成半（0.075 格），渲染到屏幕上只剩几个
 * 像素，圆边根本看不出来，看上去更像一个模糊的小方块。</p>
 *
 * <p>所以这里沿用它的颜色（<b>逐位相同</b>，见下面那三个常量），只把尺寸放大到看得清的程度，
 * 并让它在消失前慢慢变淡。视觉上仍是同一颗蜡点，只是这次看得出是圆的。</p>
 *
 * <p><b>取不受世界光照的渲染层</b>：它是「光点」，在暗处也该亮着，不该跟着洞窟一起变黑。</p>
 */
@Environment(EnvType.CLIENT)
public class RedWaxSparkParticle extends SpriteBillboardParticle {

    /** 蜡点的红分量。与游戏自带涂蜡粒子逐位相同——那正是蜡的颜色，不必另调。 */
    private static final float WAX_RED = 0.91F;

    /** 蜡点的绿分量。 */
    private static final float WAX_GREEN = 0.55F;

    /** 蜡点的蓝分量。 */
    private static final float WAX_BLUE = 0.08F;

    /** 寿命的下限（刻）。 */
    private static final int MIN_LIFETIME = 14;

    /** 寿命的浮动范围（刻）。每颗长短不一，一齐淡出才不会像商量好的。 */
    private static final int LIFETIME_SPREAD = 8;

    /** 尺寸的下限（格）。比原版涂蜡粒子大约五成，圆边才看得清。 */
    private static final float MIN_SCALE = 0.11F;

    /** 尺寸的浮动范围（格）。 */
    private static final float SCALE_SPREAD = 0.05F;

    /** 上浮的初速度下限（格/刻）。慢到几乎察觉不出，只让它看着像在飘。 */
    private static final double MIN_RISE = 0.004D;

    /** 上浮速度的浮动范围（格/刻）。 */
    private static final double RISE_SPREAD = 0.006D;

    /** 横向漂移的幅度（格/刻）。极小，只是免得几颗排成一条直线往上走。 */
    private static final double SIDE_DRIFT = 0.008D;

    /**
     * @param world          粒子所在的客户端世界
     * @param x              出生点 X
     * @param y              出生点 Y
     * @param z              出生点 Z
     * @param spriteProvider 供它从中挑一帧贴图
     */
    RedWaxSparkParticle(ClientWorld world, double x, double y, double z, SpriteProvider spriteProvider) {
        super(world, x, y, z, 0.0D, 0.0D, 0.0D);
        this.setSprite(spriteProvider.getSprite(this.random));

        this.scale = MIN_SCALE + this.random.nextFloat() * SCALE_SPREAD;
        this.maxAge = MIN_LIFETIME + this.random.nextInt(LIFETIME_SPREAD);

        // 不受重力、也不被方块挡住：它是贴着人溅开的一颗蜡点，不该掉在地上或卡在墙角
        this.collidesWithWorld = false;
        this.gravityStrength = 0.0F;
        this.velocityMultiplier = 0.86F;

        this.velocityY = MIN_RISE + this.random.nextDouble() * RISE_SPREAD;
        this.velocityX = (this.random.nextDouble() - 0.5D) * SIDE_DRIFT;
        this.velocityZ = (this.random.nextDouble() - 0.5D) * SIDE_DRIFT;

        this.setColor(WAX_RED, WAX_GREEN, WAX_BLUE);
    }

    /**
     * 每刻把不透明度按寿命往下压，消失时就不会「啪」地一下没掉。
     *
     * <p>用的是平方曲线：前半程几乎不变，后半程才明显变淡——这样蜡点先清清楚楚地亮一下，
     * 再柔和地收尾，而不是从头到尾一直在褪色。</p>
     */
    @Override
    public void tick() {
        super.tick();

        float progress = MathHelper.clamp((float) this.age / this.maxAge, 0.0F, 1.0F);
        this.alpha = 1.0F - progress * progress;
    }

    /**
     * @return 粒子的渲染层，取不受世界光照的一层——它是光点，暗处也该亮着
     */
    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_LIT;
    }

    /**
     * 粒子的工厂，供客户端注册表引用。
     */
    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<DefaultParticleType> {

        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(DefaultParticleType type, ClientWorld world,
                                       double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ) {
            return new RedWaxSparkParticle(world, x, y, z, this.spriteProvider);
        }
    }
}
