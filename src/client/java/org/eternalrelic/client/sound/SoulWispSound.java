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
 * <p>声音设为循环，因为这是一路持续的呜咽，而不是一声响。魂火消失时由粒子叫停。</p>
 */
public class SoulWispSound extends MovingSoundInstance {

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
     * 位置与结束时机都由魂火粒子推过来，这里没有自己要算的东西。
     */
    @Override
    public void tick() {
    }
}
