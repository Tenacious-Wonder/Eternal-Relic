package org.eternalrelic.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;

import org.eternalrelic.client.sound.SoulWispSound;
import org.eternalrelic.registry.ModSounds;
import org.eternalrelic.relic.SoulBurstParticleEffect;
import org.eternalrelic.relic.SoulWispParticleEffect;

/**
 * 一缕飞向玩家的魂火 —— 生物倒下时从尸体上升起，像火苗一样摇曳着飘向灯的主人，挨到人便化开。
 *
 * <p><b>摇曳是怎么来的</b>：原版火焰靠「一堆寿命、速度各不相同的火苗」堆出摇曳感，
 * 而这里只有一颗会追人的火，所以把那几样特征直接做进它自己身上——
 * 上下正弦窜动、大小随节奏脉动、越飞越亮（呼应原版火焰越烧越亮）、并留下一星半点的火星。</p>
 *
 * <p><b>为什么不是直线冲过来</b>：它每刻朝玩家迈一步，同时叠加一份<b>带惯性的</b>横向漂移——
 * 漂移方向每刻只轻轻拐一点，而不是整颗重新乱跳。早期版本用的是每刻完全独立的随机位移，
 * 幅度又接近步长，结果看着一卡一卡的。</p>
 */
@Environment(EnvType.CLIENT)
public class SoulWispParticle extends SpriteBillboardParticle {

    /** 每刻朝玩家前进的距离（格）。0.22 格/刻 = 每秒约 4.4 格。 */
    private static final double STEP = 0.22D;

    /** 漂移每刻的转向强度：数值越大，曲线拐得越勤。 */
    private static final double SWAY_TURN = 0.016D;

    /** 漂移的速度保留率。比 1 略小，避免漂移越积越大把人甩飞。 */
    private static final double SWAY_DAMPING = 0.90D;

    /** 火焰上下窜动的幅度（格）与角速度。 */
    private static final double FLICKER_AMPLITUDE = 0.05D;
    private static final double FLICKER_SPEED = 0.45D;

    /** 尺寸脉动的幅度（相对大小）。火苗一胀一缩，才不像一颗匀速的光球。 */
    private static final float SIZE_PULSE = 0.25F;

    /** 每隔多少刻掉一星火星。数值越小拖尾越密。 */
    private static final int TRAIL_INTERVAL_TICKS = 2;

    /** 离玩家多近就算碰到（格）。玩家宽约 0.6，留一点余量。 */
    private static final double ARRIVE_DISTANCE = 0.6D;

    /** 最长追多久（刻）。玩家跑远或消失时靠它收场，免得魂火永远飘着。 */
    private static final int MAX_LIFETIME = 240;

    /** 到达时炸开的碎屑颗数与扩散半径。半径 1.5 即直径约三格。 */
    private static final int BURST_COUNT = 14;
    private static final float BURST_RADIUS = 1.5F;

    /** 目标玩家的实体编号；-1 表示没有目标。 */
    private final int targetEntityId;

    /** 供每刻重新定位目标，也用于撒出火星。 */
    private final ClientWorld clientWorld;

    /** 当前的漂移速度。它自己带惯性，因此路径是平滑曲线而不是抖动。 */
    private double swayX;
    private double swayY;
    private double swayZ;

    /** 这颗粒子的摇曳相位。每颗不同，免得同时生成的几颗像节拍器一样整齐。 */
    private final double flickerPhase;

    /**
     * 这颗粒子自己的呜咽声。声源挂在魂火上并随它移动，因此听起来是「那个东西在响」
     * 而不是凭空来的声音；至于听不听得见，由音效引擎按距离衰减决定。
     */
    private SoulWispSound sound;

    /**
     * @param world          粒子所在的客户端世界
     * @param x              出生点 X
     * @param y              出生点 Y
     * @param z              出生点 Z
     * @param effect         服务端传来的生成参数，其中指明了这缕魂火要追哪位玩家
     * @param spriteProvider 供它从中挑一帧贴图
     */
    SoulWispParticle(ClientWorld world, double x, double y, double z,
                     SoulWispParticleEffect effect, SpriteProvider spriteProvider) {
        super(world, x, y, z, 0.0D, 0.0D, 0.0D);
        this.setSprite(spriteProvider.getSprite(this.random));

        this.clientWorld = world;
        this.targetEntityId = effect.targetEntityId();
        this.flickerPhase = this.random.nextDouble() * Math.PI * 2.0D;

        // 灵魂火的量级：约比守夜之瞳的光点大三四倍，好让人看清它在飞
        this.scale = 0.20F + this.random.nextFloat() * 0.10F;
        this.maxAge = MAX_LIFETIME;
        // 不受重力、也不被方块挡住：它是飘过去的，不该掉在地上或卡在墙角
        this.collidesWithWorld = false;
        this.gravityStrength = 0.0F;

        // 出生点随机偏一点，与同批出现的其它魂火错开
        this.setPos(
                this.x + (this.random.nextFloat() - this.random.nextFloat()) * 0.05F,
                this.y + (this.random.nextFloat() - this.random.nextFloat()) * 0.05F,
                this.z + (this.random.nextFloat() - this.random.nextFloat()) * 0.05F);

        // 灵魂火本体的贴图自带上色，这里只做一点点提亮
        this.setColor(0.85F, 1.0F, 1.0F);
    }

    /**
     * 每刻朝玩家迈一步，叠上带惯性的漂移与上下窜动；挨到人就化开。
     */
    @Override
    public void tick() {
        super.tick();

        // 寿命走到尽头时收声再散场，否则那段循环播放的声音会一直留在这个世界上
        if (this.dead) {
            stopSound();
            return;
        }

        Entity target = this.clientWorld.getEntityById(this.targetEntityId);
        if (target == null) {
            // 目标不在了（离线、换维度、已死亡）：让它自然飘散，不再乱追
            this.velocityY += 0.002D;
            return;
        }

        double targetX = target.getX();
        double targetY = target.getEyeY() - 0.35D;
        double targetZ = target.getZ();

        double dx = targetX - this.x;
        double dy = targetY - this.y;
        double dz = targetZ - this.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (distance <= ARRIVE_DISTANCE) {
            burstAtChest(targetX, targetY, targetZ);
            stopSound();
            this.markDead();
            return;
        }

        // 漂移方向每刻只轻轻拐一点，靠惯性延续，于是路径平滑
        this.swayX = this.swayX * SWAY_DAMPING + (this.random.nextDouble() - 0.5D) * SWAY_TURN;
        this.swayY = this.swayY * SWAY_DAMPING + (this.random.nextDouble() - 0.5D) * SWAY_TURN;
        this.swayZ = this.swayZ * SWAY_DAMPING + (this.random.nextDouble() - 0.5D) * SWAY_TURN;

        // 火苗的上下窜动：正弦起伏，像被风吹得一跳一跳
        double flicker = Math.sin((this.age + this.flickerPhase) * FLICKER_SPEED) * FLICKER_AMPLITUDE;

        this.setPos(
                this.x + dx / distance * STEP + this.swayX,
                this.y + dy / distance * STEP + this.swayY + flicker,
                this.z + dz / distance * STEP + this.swayZ);

        updateSound();
        dropTrail();
    }

    /**
     * 让呜咽声跟着魂火走。
     *
     * <p>第一次调用时把声音放出去，此后每刻只更新坐标——位置每刻都在变，声音才不会留在出生点。
     * 声音实例自带按距离衰减，凑近才听得清。</p>
     */
    private void updateSound() {
        if (this.sound == null) {
            this.sound = new SoulWispSound(this.x, this.y, this.z);
            MinecraftClient.getInstance().getSoundManager().play(this.sound);
            return;
        }

        this.sound.moveTo(this.x, this.y, this.z);
    }

    /**
     * 叫停这一段呜咽，并清掉引用，避免重复停止。
     */
    private void stopSound() {
        if (this.sound != null) {
            this.sound.stop();
            this.sound = null;
        }
    }

    /**
     * 隔几刻掉下一星火星，拖出一条淡淡的尾迹。
     *
     * <p>只用原版灵魂火粒子、且速度近乎为零：它只是留在原地慢慢熄灭的余烬，
     * 不会自己飞走，于是尾迹看起来像是被这颗火苗"熏"出来的。</p>
     */
    private void dropTrail() {
        if (this.age % TRAIL_INTERVAL_TICKS != 0) {
            return;
        }

        this.clientWorld.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                this.x, this.y, this.z,
                0.0D, 0.01D, 0.0D);
    }

    /**
     * 抵达时在玩家胸口炸开一小圈碎屑。
     *
     * <p>用的是本模组自己的碎屑粒子（柔光圆点），而不是原版灵魂火——两颗粒子各司其职：
     * 飞来的那颗是灵魂火把的火苗，炸开的这圈则是"被灯收进去了"的余韵。</p>
     *
     * @param chestX 玩家胸口 X
     * @param chestY 玩家胸口 Y
     * @param chestZ 玩家胸口 Z
     */
    private void burstAtChest(double chestX, double chestY, double chestZ) {
        // 消散声：直接交给声音引擎播一次。不用世界那套「按坐标播放」——
        // 那套适合由服务端广播给周围人，而这里本来就是客户端自己的收尾，直接播更干脆
        MinecraftClient.getInstance().getSoundManager().play(
                PositionedSoundInstance.master(ModSounds.SOUL_BURST, 1.0F));

        // 一次生成多颗：每颗的出生点方向不同，客户端才知道各自该往哪散
        for (int i = 0; i < BURST_COUNT; i++) {
            double theta = this.random.nextDouble() * Math.PI * 2.0D;
            double cosPhi = this.random.nextDouble() * 2.0D - 1.0D;
            double sinPhi = Math.sqrt(Math.max(0.0D, 1.0D - cosPhi * cosPhi));

            this.clientWorld.addParticle(
                    new SoulBurstParticleEffect(chestX, chestY, chestZ, BURST_RADIUS),
                    chestX + sinPhi * Math.cos(theta) * 0.05D,
                    chestY + cosPhi * 0.05D,
                    chestZ + sinPhi * Math.sin(theta) * 0.05D,
                    0.0D, 0.0D, 0.0D);
        }
    }

    /**
     * 尺寸一边随寿命收细，一边按火焰的节奏一胀一缩。
     *
     * <p>原版火焰只做单调的缩小，靠许多颗长短不齐的火苗凑出跳动感；
     * 这里只有一颗，便把那份跳动直接做成它自己的脉动。</p>
     *
     * @param tickDelta 当前帧在两次游戏刻之间的插值进度
     * @return 本帧的粒子尺寸
     */
    @Override
    public float getSize(float tickDelta) {
        float progress = MathHelper.clamp((this.age + tickDelta) / this.maxAge, 0.0F, 1.0F);
        float shrink = 1.0F - progress * 0.4F;
        float pulse = (float) Math.sin((this.age + tickDelta + this.flickerPhase) * FLICKER_SPEED) * SIZE_PULSE;

        return this.scale * shrink * (1.0F + pulse);
    }

    /**
     * 越接近寿命终点越亮，与{@code FlameParticle}同一套做法——火焰本就该越烧越旺。
     *
     * <p>只抬升方块光那一半，不动天光，因此白天夜里都亮得一致。</p>
     *
     * @param tint 当前帧的插值进度
     * @return 打包后的光照值
     */
    @Override
    public int getBrightness(float tint) {
        float progress = MathHelper.clamp((this.age + tint) / this.maxAge, 0.0F, 1.0F);

        int packed = super.getBrightness(tint);
        int blockLight = packed & 0xFF;
        int skyLight = packed >> 16 & 0xFF;

        blockLight = Math.min(240, blockLight + (int) (progress * 15.0F * 16.0F));

        return blockLight | skyLight << 16;
    }

    /**
     * @return 粒子的渲染层。取不透明层，与原版火焰一致——火焰贴图自带透明区域，
     *         用不透明层做镂空比半透明混叠更干脆
     */
    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_OPAQUE;
    }

    /**
     * 粒子的工厂，供客户端注册表引用。
     */
    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<SoulWispParticleEffect> {

        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(SoulWispParticleEffect effect, ClientWorld world,
                                       double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ) {
            return new SoulWispParticle(world, x, y, z, effect, this.spriteProvider);
        }
    }
}
