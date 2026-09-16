package org.eternalrelic.client.sound;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;

import org.eternalrelic.registry.ModSounds;

/**
 * 地面上那片魂火发出的声响 —— 一层持续的低语，外加在火里各处随机冒出的呻吟。
 *
 * <p><b>两层声音的分工</b>：低语是"这片火在烧"的底噪，固定在魂火中心、循环播放；
 * 呻吟是"火里有东西"的动静，每隔 1.5~4.5 秒（30~90 刻）在魂火范围内的随机位置响一声。
 * 只有前者的话，听起来像一台持续运转的机器；加上后者，才像有东西在火里。</p>
 *
 * <p><b>呻吟为什么要随机摆位</b>：如果在固定点播，哪怕声音本身有变化，耳朵也能听出
 * "还是那个喇叭"。撒在不同位置、配上随机的音高与音量之后，每一声都像从火里的另一个
 * 角落传出来，才会让人忍不住往那个方向看。</p>
 *
 * <p><b>淡出是怎么做到的</b>：音效引擎每刻都会重新读一次这个实例的音量，所以只要在最后一段时间里
 * 逐刻把音量按剩余比例调小，声音就会平滑地沉下去；等剩余时间归零再真正收声。</p>
 *
 * <p>它固定在魂火中心播放（不是跟着谁跑），并且按距离衰减——离得远就听不见。
 * 呻吟同样按距离衰减：站在火边听得清，站远了只剩一点模糊的动静。</p>
 */
public class SoulFlameSound extends MovingSoundInstance {

    /** 用多少刻把声音收干净。约一秒。 */
    private static final int FADE_OUT_TICKS = 20;

    /** 正常播放时的音量，淡出时按比例在此基础上下调。 */
    private static final float BASE_VOLUME = 1.0F;

    /** 两声呻吟之间隔多少刻：1.5~4.5 秒（30~90 刻）。 */
    private static final int MOAN_INTERVAL_MIN = 30;
    private static final int MOAN_INTERVAL_MAX = 90;

    /** 魂火刚落下时先安静一小会儿再开口（0.25~1.25 秒），免得和释放声撞在一起。 */
    private static final int FIRST_MOAN_DELAY_MIN = 5;
    private static final int FIRST_MOAN_DELAY_MAX = 25;

    /** 呻吟的音量区间：压低一些，让它像是从火里透出来的，而不是在耳边喊。 */
    private static final float MOAN_VOLUME_MIN = 0.45F;
    private static final float MOAN_VOLUME_MAX = 0.85F;

    /** 呻吟的音高区间：同一段素材换个音高，听着就不是同一句。 */
    private static final float MOAN_PITCH_MIN = 0.70F;
    private static final float MOAN_PITCH_MAX = 1.15F;

    /** 摆位时把半径收缩一点，免得正好落在边界上、听起来忽远忽近。 */
    private static final double MOAN_RADIUS_SCALE = 0.85D;

    /** 呻吟摆位时在高度上最多抬多少格，免得全都贴着地面。 */
    private static final double MOAN_HEIGHT_SPREAD = 1.2D;

    /**
     * 魂火中心。
     *
     * <p>单独存一份，因为 {@code x / y / z} 是「声源此刻在哪」，含义不同：
     * 万一将来要挪动这段低语的声源，呻吟的撒点也不该跟着漂走。</p>
     */
    private final double centerX;
    private final double centerY;
    private final double centerZ;

    /** 魂火半径（格）。呻吟撒在这个圆里。 */
    private final double radius;

    /** 这片魂火还剩多少刻。 */
    private int remainingTicks;

    /** 距离下一声呻吟还有多少刻。 */
    private int moanCountdown;

    /**
     * @param x             魂火中心 X
     * @param y             魂火中心 Y
     * @param z             魂火中心 Z
     * @param durationTicks 这片魂火还会存在多少刻
     * @param radius        魂火半径（格）
     */
    public SoulFlameSound(double x, double y, double z, int durationTicks, double radius) {
        super(ModSounds.SOUL_FLAME, SoundCategory.PLAYERS, SoundInstance.createRandom());

        this.x = x;
        this.y = y;
        this.z = z;
        this.volume = BASE_VOLUME;
        this.pitch = 1.0F;

        // 低语循环着烧，直到魂火熄灭
        this.repeat = true;
        this.repeatDelay = 0;

        // 按距离线性衰减：凑近才听得清
        this.attenuationType = SoundInstance.AttenuationType.LINEAR;

        this.centerX = x;
        this.centerY = y;
        this.centerZ = z;
        this.radius = radius;

        this.remainingTicks = durationTicks;
        this.moanCountdown = randomBetween(FIRST_MOAN_DELAY_MIN, FIRST_MOAN_DELAY_MAX);
    }

    /**
     * 每刻推进：倒计时、按随机间隔冒出呻吟、尾声淡出。
     */
    @Override
    public void tick() {
        this.remainingTicks--;

        // 先判结束：魂火已经熄了就不该再冒出新的一声
        if (this.remainingTicks <= 0) {
            this.setDone();
            return;
        }

        this.moanCountdown--;
        if (this.moanCountdown <= 0) {
            moan();
            this.moanCountdown = randomBetween(MOAN_INTERVAL_MIN, MOAN_INTERVAL_MAX);
        }

        if (this.remainingTicks <= FADE_OUT_TICKS) {
            this.volume = BASE_VOLUME * (this.remainingTicks / (float) FADE_OUT_TICKS);
        }
    }

    /**
     * 在魂火范围内的随机位置放一声呻吟。
     *
     * <p>取点用的是极坐标：角度均匀随机，半径取平方根——这样落点在圆面上才是均匀的。
     * 直接对半径均匀取值的话，点会往圆心堆，听起来就总在正中响。</p>
     *
     * <p>音高与音量也各随机一次：素材本身已有三段，再叠上音高变化，重复感就被打散了。</p>
     */
    private void moan() {
        double angle = this.random.nextDouble() * Math.PI * 2.0D;
        double distance = Math.sqrt(this.random.nextDouble()) * this.radius * MOAN_RADIUS_SCALE;

        double moanX = this.centerX + Math.cos(angle) * distance;
        double moanZ = this.centerZ + Math.sin(angle) * distance;
        double moanY = this.centerY + this.random.nextDouble() * MOAN_HEIGHT_SPREAD;

        float moanVolume = MOAN_VOLUME_MIN + this.random.nextFloat() * (MOAN_VOLUME_MAX - MOAN_VOLUME_MIN);
        float moanPitch = MOAN_PITCH_MIN + this.random.nextFloat() * (MOAN_PITCH_MAX - MOAN_PITCH_MIN);

        // 每一声都是一次独立的定点播放：它有自己的位置、音量、音高，放完即止
        MinecraftClient.getInstance().getSoundManager().play(
                new PositionedSoundInstance(ModSounds.SOUL_MOAN, SoundCategory.PLAYERS,
                        moanVolume, moanPitch, SoundInstance.createRandom(),
                        moanX, moanY, moanZ));
    }

    /**
     * @param min 下界（含）
     * @param max 上界（含）
     * @return 区间内的一个随机整数
     */
    private int randomBetween(int min, int max) {
        return min + this.random.nextInt(max - min + 1);
    }
}
