package org.eternalrelic.relic;

import com.mojang.brigadier.StringReader;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;

import org.eternalrelic.registry.ModParticleTypes;

/**
 * 遗物入手粒子的生成参数：球心、扩散半径与涨落速度。
 *
 * <p>这些参数必须随粒子一起发给客户端，客户端才知道要把粒子摆到哪个球面上——
 * 普通粒子只能携带位置与速度，做不到「以胸口为中心的球形涨落」。</p>
 *
 * @param centerX      球心 X（玩家胸口）
 * @param centerY      球心 Y
 * @param centerZ      球心 Z
 * @param sphereRadius 向外扩散的最大半径
 * @param cycleSpeed   涨落速度倍率，越大往返越快
 */
public record RelicArrivalParticleEffect(double centerX, double centerY, double centerZ, float sphereRadius,
        float cycleSpeed) implements ParticleEffect {

    @Override
    public ParticleType<?> getType() {
        return ModParticleTypes.relicArrival();
    }

    /**
     * 把这些参数写进网络包，随粒子一起发往客户端。
     *
     * @param buffer 目标网络缓冲
     */
    @Override
    public void write(PacketByteBuf buffer) {
        buffer.writeDouble(this.centerX);
        buffer.writeDouble(this.centerY);
        buffer.writeDouble(this.centerZ);
        buffer.writeFloat(this.sphereRadius);
        buffer.writeFloat(this.cycleSpeed);
    }

    /**
     * @return 供命令与调试输出显示的简短名称
     */
    @Override
    public String asString() {
        return "relic_arrival";
    }

    /**
     * 从网络数据还原粒子参数，供游戏在客户端重建这些粒子时使用。
     */
    public static class Factory implements ParticleEffect.Factory<RelicArrivalParticleEffect> {

        @Override
        public RelicArrivalParticleEffect read(ParticleType<RelicArrivalParticleEffect> type, PacketByteBuf buffer) {
            return new RelicArrivalParticleEffect(
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readFloat(),
                    buffer.readFloat());
        }

        @Override
        public RelicArrivalParticleEffect read(ParticleType<RelicArrivalParticleEffect> type, StringReader reader) {
            // 搏动粒子由遗物触发时生成，不接受来自命令的文本参数
            return new RelicArrivalParticleEffect(0.0D, 0.0D, 0.0D, 1.0F, 1.0F);
        }
    }
}
