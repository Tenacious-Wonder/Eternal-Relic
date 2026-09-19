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
 * 从地面升起的灵魂 —— 魂火范围内，不时从地里冒出来、缓缓向上飘的一缕鬼魂。
 *
 * <p><b>它要像什么</b>：下界灵魂沙谷里那种从地里渗出来、慢慢往上飘的小脸。
 * 关键在于"往上"和"慢"：横着飘或者匀速直上都会立刻失去那种感觉。</p>
 *
 * <p><b>怎么做出"渗出来"的感觉</b>：出生时几乎静止，随后越升越快，到上限为止，
 * 像被地底的东西挤出来；同时在水平方向做极轻微的随机摆动。再配上头尾的缩放
 * 淡入淡出，看起来就是慢慢显形、又慢慢散掉，而不是凭空出现、到点消失。</p>
 *
 * <p>贴图直接引用原版那套 11 帧灵魂图（{@code soul_rise.json} 里指定），
 * 帧随时间推进，因此它的"表情"在飘的过程中一直在变。</p>
 */
@Environment(EnvType.CLIENT)
public class SoulRiseParticle extends SpriteBillboardParticle {

    /** 出生后用寿命的多少比例把体型放大到满。 */
    private static final float APPEAR_RATIO = 0.22F;

    /** 消失前用寿命的多少比例把体型收到零。 */
    private static final float DISAPPEAR_RATIO = 0.32F;

    /** 每刻增加的上升速度（格/刻）。 */
    private static final double RISE_ACCELERATION = 0.0016D;

    /** 上升速度的下限与随机增量（格/刻）。合起来约每秒 0.4~0.84 格，且初期还在加速。 */
    private static final double RISE_SPEED_MIN = 0.020D;
    private static final double RISE_SPEED_SPREAD = 0.022D;

    /** 水平摆动的每刻随机加速度与速度上限（格/刻）。 */
    private static final double SWAY_ACCELERATION = 0.0012D;
    private static final double SWAY_SPEED = 0.010D;

    /** 寿命的下限与随机增量（刻）。约 2.2~3.7 秒。 */
    private static final int AGE_MIN = 45;
    private static final int AGE_SPREAD = 30;

    /** 体型的下限与随机增量。 */
    private static final float SCALE_MIN = 0.16F;
    private static final float SCALE_SPREAD = 0.10F;

    /** 这颗粒子贴图随寿命翻到第几帧 —— 由原版那套 11 帧灵魂图提供。 */
    private final SpriteProvider spriteProvider;

    /** 这颗粒子最终会升到的速度。每颗略有不同，免得整片一起匀速上升。 */
    private final double riseSpeed;

    /** 满体型。缩放淡入淡出时在它和零之间过渡。 */
    private final float fullScale;

    /**
     * @param world          粒子所在的客户端世界
     * @param x              出生点 X
     * @param y              出生点 Y
     * @param z              出生点 Z
     * @param spriteProvider 供它按年龄翻动那套 11 帧灵魂图
     */
    SoulRiseParticle(ClientWorld world, double x, double y, double z, SpriteProvider spriteProvider) {
        super(world, x, y, z, 0.0D, 0.0D, 0.0D);

        this.spriteProvider = spriteProvider;

        // 寿命必须先定下来：挑动画帧是按「已活了多少 / 总共活多久」算的，
        // 此时寿命还是初始的 0 的话，那一步会除以零
        this.maxAge = AGE_MIN + this.random.nextInt(AGE_SPREAD);
        this.setSpriteForAge(spriteProvider);

        this.riseSpeed = RISE_SPEED_MIN + this.random.nextDouble() * RISE_SPEED_SPREAD;
        this.fullScale = SCALE_MIN + this.random.nextFloat() * SCALE_SPREAD;
        this.scale = this.fullScale;

        // 出生时静止，由 tick 里的加速把它"挤"上去
        this.velocityX = 0.0D;
        this.velocityY = 0.0D;
        this.velocityZ = 0.0D;

        // 自己管上升，所以不要重力；也不和方块较劲 —— 魂本来就能穿墙
        this.gravityStrength = 0.0F;
        this.collidesWithWorld = false;

        // 灵魂火的青白色。每颗深浅略作区别，一片里才不会像复制粘贴
        float depth = this.random.nextFloat() * 0.25F;
        this.setColor(0.62F + depth * 0.25F, 0.88F + depth * 0.12F, 1.0F);
    }

    /**
     * 越升越快、左右轻摆，并推进贴图帧。
     */
    @Override
    public void tick() {
        // 速度要在 super.tick() 之前定好：那一步才会按它把粒子挪出去
        this.velocityY = Math.min(this.velocityY + RISE_ACCELERATION, this.riseSpeed);

        this.velocityX = MathHelper.clamp(
                this.velocityX + (this.random.nextDouble() - 0.5D) * SWAY_ACCELERATION,
                -SWAY_SPEED, SWAY_SPEED);
        this.velocityZ = MathHelper.clamp(
                this.velocityZ + (this.random.nextDouble() - 0.5D) * SWAY_ACCELERATION,
                -SWAY_SPEED, SWAY_SPEED);

        super.tick();

        this.setSpriteForAge(this.spriteProvider);
    }

    /**
     * 头尾各来一段缩放过渡：慢慢显形、慢慢散掉。
     *
     * @param tickDelta 当前帧在两次游戏刻之间的插值进度
     * @return 本帧的粒子尺寸
     */
    @Override
    public float getSize(float tickDelta) {
        float progress = MathHelper.clamp((this.age + tickDelta) / this.maxAge, 0.0F, 1.0F);

        float appear = MathHelper.clamp(progress / APPEAR_RATIO, 0.0F, 1.0F);
        float disappear = MathHelper.clamp((1.0F - progress) / DISAPPEAR_RATIO, 0.0F, 1.0F);

        return this.fullScale * Math.min(appear, disappear);
    }

    /**
     * 越老越亮：刚渗出来时几乎看不见，升到半空才亮起来，像慢慢"醒"过来。
     *
     * @param tint 当前帧的插值进度
     * @return 本帧的光照值
     */
    @Override
    public int getBrightness(float tint) {
        float progress = MathHelper.clamp((this.age + tint) / this.maxAge, 0.0F, 1.0F);

        return ParticleMath.brightenBlockLight(progress, super.getBrightness(tint));
    }

    /**
     * @return 粒子的渲染层。取半透明层，和原版灵魂粒子一致，边缘柔和
     */
    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
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
            return new SoulRiseParticle(world, x, y, z, this.spriteProvider);
        }
    }
}
