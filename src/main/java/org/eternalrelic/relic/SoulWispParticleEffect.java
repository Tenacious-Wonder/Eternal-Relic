package org.eternalrelic.relic;

import com.mojang.brigadier.StringReader;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;

import org.eternalrelic.registry.ModParticleTypes;

/**
 * 引魂燃灯收集灵魂时，那颗飞向玩家的魂火的生成参数。
 *
 * <p>只需要带一个「目标是谁」——粒子要靠这个编号在客户端每刻查到玩家此刻的位置，
 * 所以魂火才能一路追着人跑，而不是飞向生成瞬间的旧坐标。位置随粒子自身携带，
 * 不必额外传。</p>
 *
 * @param targetEntityId 目标玩家的实体编号
 */
public record SoulWispParticleEffect(int targetEntityId) implements ParticleEffect {

    @Override
    public ParticleType<?> getType() {
        return ModParticleTypes.soulWisp();
    }

    /**
     * 把这些参数写进网络包，随粒子一起发往客户端。
     *
     * @param buffer 目标网络缓冲
     */
    @Override
    public void write(PacketByteBuf buffer) {
        buffer.writeInt(this.targetEntityId);
    }

    /**
     * @return 供命令与调试输出显示的简短名称
     */
    @Override
    public String asString() {
        return "soul_wisp";
    }

    /**
     * 从网络数据还原粒子参数，供游戏在客户端重建这些粒子时使用。
     */
    public static class Factory implements ParticleEffect.Factory<SoulWispParticleEffect> {

        @Override
        public SoulWispParticleEffect read(ParticleType<SoulWispParticleEffect> type, PacketByteBuf buffer) {
            return new SoulWispParticleEffect(buffer.readInt());
        }

        @Override
        public SoulWispParticleEffect read(ParticleType<SoulWispParticleEffect> type, StringReader reader) {
            // 魂火由遗物触发时生成，不接受来自命令的文本参数
            return new SoulWispParticleEffect(-1);
        }
    }
}
