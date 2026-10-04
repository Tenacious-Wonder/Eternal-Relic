package org.eternalrelic.worldgen;

import java.util.function.Predicate;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

import org.eternalrelic.EternalRelic;

/**
 * <h1>各号小家刷在哪些地方</h1>
 *
 * <p>地表与地下分两套规矩：</p>
 *
 * <h2>地表：聚落说了算</h2>
 * <p>小家1／2／3／5号的<b>地表</b>出现全部交给 {@link #CLUSTER 聚落}统一调度。
 * 之所以不让它们各自刷，是因为"各自独立掷骰子"在数学上必然导致房子均匀撒开、永远不扎堆。
 * 收归一处之后，才有可能出现"孤零零一栋""三五栋一簇""十几栋一片"的区别。</p>
 *
 * <p>{@link #SURFACE}、{@link #SURFACE_2}、{@link #SURFACE_3}、{@link #SURFACE_5} 这几份
 * <b>仍然保留</b>，只是暂时没挂上去——哪天想改回"各刷各的"，把上面那行聚落换回它们即可。</p>
 *
 * <h2>地下：各刷各的</h2>
 * <p>地下那几版依旧独立、依旧极稀有，不受聚落影响——地底下本来也谈不上"聚落"。</p>
 *
 * <h2>小家4号：独立</h2>
 * <p>4 号地表与地下都还按老规矩独立刷。</p>
 *
 * <p>地表版一律避开海洋、深海、河流、海滩；地下版不限生物群系，但要求落点周围有现成的空间
 * （见 {@code configured_feature} 里的 {@code require_open_space}），免得房子整栋闷在石头里。</p>
 *
 * <p>这里只写「挂到哪些生物群系、在哪一步生成」，具体密度在
 * {@code data/eternal_relic/worldgen/placed_feature/} 的 JSON 里。</p>
 */
public final class LittleHomeSites {

    /** 小家1号 · 地表：主世界内陆全群系，露天刷。 */
    public static final RegistryKey<PlacedFeature> SURFACE =
            RegistryKey.of(RegistryKeys.PLACED_FEATURE, EternalRelic.id("little_home_surface"));

    /** 小家1号 · 地下：主世界全群系，偶发刷，且要求有现成的空间。 */
    public static final RegistryKey<PlacedFeature> UNDERGROUND =
            RegistryKey.of(RegistryKeys.PLACED_FEATURE, EternalRelic.id("little_home_underground"));

    /** 小家2号 · 地表：比小家1号更稀有。 */
    public static final RegistryKey<PlacedFeature> SURFACE_2 =
            RegistryKey.of(RegistryKeys.PLACED_FEATURE, EternalRelic.id("little_home_2_surface"));

    /** 小家2号 · 地下：同样更稀有。 */
    public static final RegistryKey<PlacedFeature> UNDERGROUND_2 =
            RegistryKey.of(RegistryKeys.PLACED_FEATURE, EternalRelic.id("little_home_2_underground"));

    /** 小家3号 · 地表：密度与小家1号相当，但保留用户铺的地板。 */
    public static final RegistryKey<PlacedFeature> SURFACE_3 =
            RegistryKey.of(RegistryKeys.PLACED_FEATURE, EternalRelic.id("little_home_3_surface"));

    /** 小家3号 · 地下。 */
    public static final RegistryKey<PlacedFeature> UNDERGROUND_3 =
            RegistryKey.of(RegistryKeys.PLACED_FEATURE, EternalRelic.id("little_home_3_underground"));

    /** 小家4号 · 地表：八成开小家1号那份收获，两成开要塞走廊那份。 */
    public static final RegistryKey<PlacedFeature> SURFACE_4 =
            RegistryKey.of(RegistryKeys.PLACED_FEATURE, EternalRelic.id("little_home_4_surface"));

    /** 小家4号 · 地下。 */
    public static final RegistryKey<PlacedFeature> UNDERGROUND_4 =
            RegistryKey.of(RegistryKeys.PLACED_FEATURE, EternalRelic.id("little_home_4_underground"));

    /** 小家5号 · 地表：收获和小家1号一样，密度也相当。 */
    public static final RegistryKey<PlacedFeature> SURFACE_5 =
            RegistryKey.of(RegistryKeys.PLACED_FEATURE, EternalRelic.id("little_home_5_surface"));

    /** 小家5号 · 地下。 */
    public static final RegistryKey<PlacedFeature> UNDERGROUND_5 =
            RegistryKey.of(RegistryKeys.PLACED_FEATURE, EternalRelic.id("little_home_5_underground"));

    /** 聚落 · 地表：小家1／2／3／5号在地表全部由它统一调度，好让房子能扎堆。 */
    public static final RegistryKey<PlacedFeature> CLUSTER =
            RegistryKey.of(RegistryKeys.PLACED_FEATURE, EternalRelic.id("little_home_cluster"));

    private LittleHomeSites() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，把十份配置挂进生物群系。
     */
    public static void register() {
        Predicate<BiomeSelectionContext> dryLand = BiomeSelectors.foundInOverworld()
                .and(context -> !context.hasTag(BiomeTags.IS_OCEAN))
                .and(context -> !context.hasTag(BiomeTags.IS_DEEP_OCEAN))
                .and(context -> !context.hasTag(BiomeTags.IS_RIVER))
                .and(context -> !context.hasTag(BiomeTags.IS_BEACH));

        // 小家1号的**地表**已经移到下面由聚落统一调度了，这里只留地下那版
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
                GenerationStep.Feature.UNDERGROUND_STRUCTURES, UNDERGROUND);

        // 小家2号：同上，地表走聚落
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
                GenerationStep.Feature.UNDERGROUND_STRUCTURES, UNDERGROUND_2);

        // 小家3号：同上，地表走聚落
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
                GenerationStep.Feature.UNDERGROUND_STRUCTURES, UNDERGROUND_3);

        // 小家4号：仍然独立刷，不受聚落调度
        BiomeModifications.addFeature(dryLand,
                GenerationStep.Feature.SURFACE_STRUCTURES, SURFACE_4);
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
                GenerationStep.Feature.UNDERGROUND_STRUCTURES, UNDERGROUND_4);

        // 小家5号：同上，地表走聚落
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
                GenerationStep.Feature.UNDERGROUND_STRUCTURES, UNDERGROUND_5);

        // 聚落：小家1／2／3／5号在地表的出现全由它一处说了算
        BiomeModifications.addFeature(dryLand,
                GenerationStep.Feature.SURFACE_STRUCTURES, CLUSTER);
    }
}
