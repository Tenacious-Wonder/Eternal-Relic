package org.eternalrelic.worldgen;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.gen.feature.FeatureConfig;

/**
 * <h1>「地表岩石」的旋钮</h1>
 *
 * <p>两类岩石的做法完全不同：</p>
 * <ul>
 *   <li><b>大石头</b>（{@code outcrop_variants}）—— 用你搭好的图纸，只在平缓的地方生成，
 *       底下一定会垫实。图纸名单越小越重复，越大越多样；</li>
 *   <li><b>小石头</b>—— <b>不用图纸</b>，由程序在落点附近直接铺几块石台阶，跟地面齐平，
 *       所以怎么都不会悬空。铺几块由 {@code slab_count} 控制。</li>
 * </ul>
 *
 * <p>两类的比例随地方变：多裸岩的地方（山地、风袭丘陵、针叶林……）偏大石头，
 * 其余地方（森林、平原、丛林……）偏小石头。<b>两边都会出现</b>，不会「永远见不到某一种」。</p>
 *
 * <p>两个比例之和不必等于 1：它们只是「抽到大石头」的概率，剩下的自然就是小石头。</p>
 *
 * @param outcropVariants     大石头的图纸名单（文件名，不含 {@code .nbt}）
 * @param replaceChance       每一块石头有多大概率被换成当地特色的石材。
 *                            默认 0.05，也就是约二十分之一——掺一点就够，换多了整块岩石会变味
 * @param outcropChance       普通地方抽到大石头的比例。默认 0.4
 * @param outcropChanceRocky  多裸岩的地方抽到大石头的比例。默认 0.85
 * @param slabCount           小石头一次最多铺几块台阶（实际是 1 到这么多块随机）。
 *                            填 0 表示不要小石头
 */
public record SurfaceRockConfig(
        List<String> outcropVariants,
        float replaceChance,
        float outcropChance,
        float outcropChanceRocky,
        int slabCount
) implements FeatureConfig {

    public static final Codec<SurfaceRockConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().fieldOf("outcrop_variants").forGetter(SurfaceRockConfig::outcropVariants),
            Codec.FLOAT.optionalFieldOf("replace_chance", 0.05F).forGetter(SurfaceRockConfig::replaceChance),
            Codec.FLOAT.optionalFieldOf("outcrop_chance", 0.4F).forGetter(SurfaceRockConfig::outcropChance),
            Codec.FLOAT.optionalFieldOf("outcrop_chance_rocky", 0.85F)
                    .forGetter(SurfaceRockConfig::outcropChanceRocky),
            Codec.INT.optionalFieldOf("slab_count", 4).forGetter(SurfaceRockConfig::slabCount)
    ).apply(instance, SurfaceRockConfig::new));
}
