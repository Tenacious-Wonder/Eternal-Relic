package org.eternalrelic.relic;

import com.mojang.brigadier.StringReader;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;

import org.eternalrelic.registry.ModParticleTypes;

/**
 * 回响之环碎屑粒子的生成参数：球心与扩散距离。
 *
 * <p>这些参数必须随粒子一起发给客户端，客户端才知道该以哪里为中心散开、散多远——
 * 普通粒子只能携带位置与速度，做不到「以玩家为中心向外炸开的一圈碎屑」。</p>
 *
 * @param centerX      球心 X（玩家胸口）
 * @param centerY      球心 Y
 * @param centerZ      球心 Z
 * @param spreadRadius 向外扩散的距离
 */
public record EchoRingShardParticleEffect(double centerX, double centerY, double centerZ,
                                          float spreadRadius) implements ParticleEffect {

    @Override
    public ParticleType<?> getType() {
        return ModParticleTypes.echoRingShard();
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
        buffer.writeFloat(this.spreadRadius);
    }

    /**
     * @return 供命令与调试输出显示的简短名称
     */
    @Override
    public String asString() {
        return "echo_ring_shard";
    }

    /**
     * 从网络数据还原粒子参数，供游戏在客户端重建这些粒子时使用。
     */
    public static class Factory implements ParticleEffect.Factory<EchoRingShardParticleEffect> {

        @Override
        public EchoRingShardParticleEffect read(ParticleType<EchoRingShardParticleEffect> type, PacketByteBuf buffer) {
            return new EchoRingShardParticleEffect(
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readFloat());
        }

        @Override
        public EchoRingShardParticleEffect read(ParticleType<EchoRingShardParticleEffect> type, StringReader reader) {
            // 碎屑粒子由遗物触发时生成，不接受来自命令的文本参数
            return new EchoRingShardParticleEffect(0.0D, 0.0D, 0.0D, 1.0F);
        }
    }
}
