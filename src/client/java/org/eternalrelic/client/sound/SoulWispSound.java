package org.eternalrelic.client.sound;

import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;

import org.eternalrelic.registry.ModSounds;

/**
 * 飞行中那缕魂火的呜咽声 —— 由魂火自己发出，并且跟着它一路移动。
 *
 * <p><b>为什么用「会移动的音效」而不是直接播放</b>：直接播一段声音是把声源钉在一个固定点上，
 * 魂火飞走了声音还留在原地。这个类型允许每刻改坐标，声音才会跟着魂火走；
 * 而且它天生按距离衰减，离得远就听不见，正合「单向音效」这件事的用法。</p>
 *
 * <p>声音设为循环，因为这是一路持续的呜咽，而不是一声响。魂火消失时由粒子叫停；
 * 万一粒子没走成正常的消散路径、来不及叫停，则由下面那个最长寿命兜底（见 {@link #MAX_LIFETIME_TICKS}）。</p>
 */
public class SoulWispSound extends MovingSoundInstance {

    /**
     * 兜底的最长寿命（刻）。30 秒。
     *
     * <p><b>这不是设计时长，只是"外部忘了叫停"时的保险。</b>正常一路只有几十刻：魂火从飞出到
     * 抵达玩家、或被叫停，声音都由粒子在那边停掉。之所以非留这个保险不可——粒子一旦被
     * <b>非 tick 路径</b>清掉，就再没有谁去叫停它了。最典型的是 F3+T 重载资源包：
     * 那一刻 {@code ParticleManager.clearParticles()} 会把粒子整体抹掉，粒子来不及收声，
     * 而这段循环播放的呜咽会一直响到退出世界。</p>
     *
     * <p>取值明显长于正常使用时长，也长于魂火粒子自己的寿命上限（240 刻），
     * 所以正常情况永远不会被它提前掐断。</p>
     */
    private static final int MAX_LIFETIME_TICKS = 600;

    /** 这段声音已经响了多少刻，用来数上面那个兜底寿命。 */
    private int ageTicks;

    /**
     * @param x 起始 X（魂火出生处）
     * @param y 起始 Y
     * @param z 起始 Z
     */
    public SoulWispSound(double x, double y, double z) {
        super(ModSounds.SOUL_WISP, SoundCategory.PLAYERS, SoundInstance.createRandom());

        this.x = x;
        this.y = y;
        this.z = z;
        this.volume = 1.0F;
        this.pitch = 1.0F;

        // 一路循环，直到魂火消失
        this.repeat = true;
        this.repeatDelay = 0;

        // 按距离线性衰减：凑近才听得清，隔远了自然消失
        this.attenuationType = SoundInstance.AttenuationType.LINEAR;
    }

    public void moveTo(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * 叫停这一段声音。魂火抵达玩家或超时消散时调用。
     */
    public void stop() {
        this.setDone();
    }

    /**
     * 每刻推进兜底的寿命计数。
     *
     * <p>位置与结束时机本来都由魂火粒子推过来，这里只负责"粒子没了也要收声"这一件事：
     * 到点自己 {@code setDone()}，不依赖任何外部调用。</p>
     */
    @Override
    public void tick() {
        this.ageTicks++;

        if (this.ageTicks >= MAX_LIFETIME_TICKS) {
            this.setDone();
        }
    }
}
