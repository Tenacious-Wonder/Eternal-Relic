package org.eternalrelic.worldgen;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.gen.feature.FeatureConfig;

/**
 * <h1>「遗落小屋聚落」的旋钮</h1>
 *
 * <p>这一套参数管的是「房子怎么扎堆」，不管房子本身长什么样——每栋房子自己的参数
 * （变体、战利品、做旧程度）原封不动塞在 {@link Home#home()} 里，用的还是
 * {@link LittleHomeConfig} 那一套。</p>
 *
 * <h2>位置是怎么定的</h2>
 * <p>全世界按 {@code grid} 格划成方格，每格安排一处聚落。规模由 {@code classes} 决定：
 * 格子坐标能被某一档的 {@code cell_step} 整除，这一格就是那一档——
 * 例如 {@code cell_step 4 → 10~13 栋}、{@code 2 → 6~9 栋}、{@code 1 → 3~5 栋}。
 * 判断按 {@code classes} 的顺序来，谁先命中算谁，所以要把步长小的排在后面当兜底。</p>
 *
 * <p>这套「整除」的排法有个好处：<b>不管玩家朝哪个方向走，每过一格坐标就换一个数</b>，
 * 所以「走多远必定遇到某一档」是可以算准的，不靠运气。</p>
 *
 * @param homes          参与抽取的房子种类。每种占多少权重，决定它在聚落里出现的比例
 * @param grid           方格边长（格）。越小聚落越密
 * @param classes        各档的规模与「每几格出现一次」，例如 {@code 步长4 → 10~13 栋}
 * @param minDistance    两栋房子之间的最小中心距（格）。这是「不重叠」的直接保证：
 *                       房子是 17×17 的，斜对角摆放时要 24 格才碰不到，所以这里给的是「下限」
 * @param minDistanceMax 最小中心距的上限。每次生成聚落时会在这个区间里现抽一个值，
 *                       免得每个聚落的疏密感一模一样
 */
public record LittleHomeClusterConfig(
        List<Home> homes,
        int grid,
        List<SizeClass> classes,
        int minDistance,
        int minDistanceMax
) implements FeatureConfig {

    /**
     * 一档聚落。
     *
     * @param code     这一档的代号（只用于日志和存档可读性）
     * @param cellStep 每几个格子出现一次这一档。越小越密；最小那一档要给 1
     * @param maxHomes 这一档最多几栋
     */
    public record SizeClass(String code, int cellStep, int minHomes, int maxHomes) {
        public static final Codec<SizeClass> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("code").forGetter(SizeClass::code),
                Codec.INT.optionalFieldOf("cell_step", 1).forGetter(SizeClass::cellStep),
                Codec.INT.fieldOf("min_homes").forGetter(SizeClass::minHomes),
                Codec.INT.fieldOf("max_homes").forGetter(SizeClass::maxHomes)
        ).apply(instance, SizeClass::new));
    }

    /**
     * 一种房子。
     *
     * @param weight         抽签权重，决定这种房子在聚落里占多大比例
     * @param minClusterSize 只有本次聚落不少于这么多栋时，这种房子才会参与抽取。
     *                       拿来把某些「好东西」锁在大聚落里——孤独建筑和小聚落碰不到
     * @param home           这种房子自己的全套参数（变体、战利品、做旧）
     */
    public record Home(int weight, int minClusterSize, LittleHomeConfig home) {
        public static final Codec<Home> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("weight", 1).forGetter(Home::weight),
                Codec.INT.optionalFieldOf("min_cluster_size", 1).forGetter(Home::minClusterSize),
                LittleHomeConfig.CODEC.fieldOf("home").forGetter(Home::home)
        ).apply(instance, Home::new));
    }

    public static final Codec<LittleHomeClusterConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Home.CODEC.listOf().fieldOf("homes").forGetter(LittleHomeClusterConfig::homes),
            Codec.INT.optionalFieldOf("grid", 500).forGetter(LittleHomeClusterConfig::grid),
            SizeClass.CODEC.listOf().fieldOf("classes").forGetter(LittleHomeClusterConfig::classes),
            Codec.INT.optionalFieldOf("min_distance", 23).forGetter(LittleHomeClusterConfig::minDistance),
            Codec.INT.optionalFieldOf("min_distance_max", 27).forGetter(LittleHomeClusterConfig::minDistanceMax)
    ).apply(instance, LittleHomeClusterConfig::new));

    /**
     * 查一个格子属于哪一档。
     *
     * <p>规则一句话：<b>格子坐标能被这一档的 {@code cellStep} 整除，就是这一档。</b>
     * 横竖两个方向各判一次，谁先命中算谁（{@code classes} 要从步长小的排到大的）。</p>
     *
     * <p>为什么用「整除」而不是查一串图样：整除规则下，<b>不管玩家朝哪个方向走，
     * 脚下格子的坐标每过一格就换一个数</b>，「每几格必定遇到一次」才算得准。
     * 而查图样在斜着走时序号可能一直不变，会出现「某一档永远碰不到」的死角——
     * 实测某个斜方向能连续十五万格不见一栋。</p>
     *
     * @param cellX 格子 X（可以为负）
     * @param cellZ 格子 Z
     * @return 该档的参数；都不命中（例如最小那档的步长不是 1）时返回 {@code null}
     */
    public SizeClass classAt(int cellX, int cellZ) {
        for (SizeClass sizeClass : classes) {
            int step = Math.max(1, sizeClass.cellStep());
            if (Math.floorMod(cellX, step) == 0 || Math.floorMod(cellZ, step) == 0) {
                return sizeClass;
            }
        }
        return null;
    }
}
