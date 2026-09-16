package org.eternalrelic.relic;

import com.mojang.brigadier.StringReader;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;

import org.eternalrelic.registry.ModParticleTypes;

/**
 * 守夜之瞳装取时的微光点参数。
 *
 * <p>光点只有两种走法，用 {@code converging} 区分：</p>
 * <ul>
 *   <li><b>收拢</b>（装入义眼）：光点从头部四周飞向头部；</li>
 *   <li><b>飞散</b>（取下义眼）：光点从头部向四周散开。</li>
 * </ul>
 *
 * <p>汇聚点是装取那一刻的头部位置。它随粒子一起发给客户端——普通粒子只能携带位置与速度，
 * 无法表达"朝某个点收拢"这种走法。</p>
 *
 * @param centerX    汇聚点 X（头部）
 * @param centerY    汇聚点 Y
 * @param centerZ    汇聚点 Z
 * @param converging 是否朝汇聚点收拢；{@code false} 表示向四周飞散
 */
public record NightwatchParticleEffect(double centerX, double centerY, double centerZ,
                                       boolean converging) implements ParticleEffect {

    @Override
    public ParticleType<?> getType() {
        return ModParticleTypes.nightwatchSpark();
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
        buffer.writeBoolean(this.converging);
    }

    /**
     * @return 供命令与调试输出显示的简短名称
     */
    @Override
    public String asString() {
        return "nightwatch_spark";
    }

    /**
     * 从网络数据还原粒子参数。
     */
    public static class Factory implements ParticleEffect.Factory<NightwatchParticleEffect> {

        @Override
        public NightwatchParticleEffect read(ParticleType<NightwatchParticleEffect> type, PacketByteBuf buffer) {
            return new NightwatchParticleEffect(
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readBoolean());
        }

        @Override
        public NightwatchParticleEffect read(ParticleType<NightwatchParticleEffect> type, StringReader reader) {
            // 光点由遗物触发时生成，不接受来自命令的文本参数
            return new NightwatchParticleEffect(0.0D, 0.0D, 0.0D, true);
        }
    }
}
