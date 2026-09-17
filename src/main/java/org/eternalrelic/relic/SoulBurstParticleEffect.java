package org.eternalrelic.relic;

import com.mojang.brigadier.StringReader;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;

import org.eternalrelic.registry.ModParticleTypes;

/**
 * 青色星点的生成参数：球心、向外扩散的距离，以及体型倍率。
 *
 * <p>这些参数必须随粒子一起发给客户端，客户端才知道该以哪里为中心散开、散多远——
 * 普通粒子只能携带位置与速度，做不到「以某点为中心的一圈余韵」。</p>
 *
 * <p>同一套星点有两个用途：魂火被收进灯里时在胸口炸开的一圈，以及引魂燃灯携带者
 * 身上的那片光晕。后者要小一号，因此把体型做成参数而不是写死在粒子里。</p>
 *
 * @param centerX      球心 X（炸开时是玩家胸口，光晕里就是那颗粒子自己的出生点）
 * @param centerY      球心 Y
 * @param centerZ      球心 Z
 * @param spreadRadius 向外扩散的距离
 * @param scale        体型倍率；1.0 为原本大小
 */
public record SoulBurstParticleEffect(double centerX, double centerY, double centerZ,
                                      float spreadRadius, float scale) implements ParticleEffect {

    @Override
    public ParticleType<?> getType() {
        return ModParticleTypes.soulBurst();
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
        buffer.writeFloat(this.scale);
    }

    /**
     * @return 供命令与调试输出显示的简短名称
     */
    @Override
    public String asString() {
        return "soul_burst";
    }

    /**
     * 从网络数据还原粒子参数，供游戏在客户端重建这些粒子时使用。
     */
    public static class Factory implements ParticleEffect.Factory<SoulBurstParticleEffect> {

        @Override
        public SoulBurstParticleEffect read(ParticleType<SoulBurstParticleEffect> type, PacketByteBuf buffer) {
            return new SoulBurstParticleEffect(
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readFloat(),
                    buffer.readFloat());
        }

        @Override
        public SoulBurstParticleEffect read(ParticleType<SoulBurstParticleEffect> type, StringReader reader) {
            // 星点由魂火抵达或光晕自行生成，不接受来自命令的文本参数
            return new SoulBurstParticleEffect(0.0D, 0.0D, 0.0D, 1.0F, 1.0F);
        }
    }
}
