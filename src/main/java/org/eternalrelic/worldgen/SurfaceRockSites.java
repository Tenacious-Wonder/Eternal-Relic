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
 * <h1>地表岩石散在哪些地方</h1>
 *
 * <p>按「现实里哪儿石头多」来分两档：</p>
 * <ul>
 *   <li>{@link #DENSE 密} —— 山地、风袭丘陵、针叶林。本来就是裸岩地带，石头随处可见。</li>
 *   <li>{@link #SPARSE 疏} —— 其余内陆群系（平原、森林、草原……）。零零散散偶尔露一块。</li>
 * </ul>
 *
 * <p>两档用的是同一份图纸配置：具体长「石块堆」还是「地表碎石」由 {@link SurfaceRockFeature}
 * 看落点的生物群系现决定。两档的区别只在密度，写在 {@code placed_feature} 的 JSON 里。</p>
 *
 * <p>海洋、深海、河流、海滩一律不挂——水里不放岩石；沙漠、恶地也不挂，因为图纸自带一层草皮，
 * 落在沙子会突兀（想给沙漠配砂岩岩石的话，得另做一套图纸，跟我说一声）。</p>
 *
 * <p>生成步骤选的是<b>「植被」</b>而不是「地表结构」：这一步排在树之后跑，
 * 岩石才能看见同一区块里已经长出来的树并让开。</p>
 */
public final class SurfaceRockSites {

    /** 岩石 · 密：多裸岩的地带。 */
    public static final RegistryKey<PlacedFeature> DENSE =
            RegistryKey.of(RegistryKeys.PLACED_FEATURE, EternalRelic.id("surface_rock_dense"));

    /** 岩石 · 疏：其余内陆群系。 */
    public static final RegistryKey<PlacedFeature> SPARSE =
            RegistryKey.of(RegistryKeys.PLACED_FEATURE, EternalRelic.id("surface_rock_sparse"));

    private SurfaceRockSites() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，把两档岩石挂进生物群系。
     */
    public static void register() {
        // 「多裸岩」：整片山、风袭丘陵、针叶林
        Predicate<BiomeSelectionContext> rugged = BiomeSelectors.foundInOverworld()
                .and(context -> context.hasTag(BiomeTags.IS_MOUNTAIN)
                        || context.hasTag(BiomeTags.IS_HILL)
                        || context.hasTag(BiomeTags.IS_TAIGA));

        // 「其余内陆」：主世界陆地，但不算上面那类，也不算沙漠类（自带草皮会突兀）
        Predicate<BiomeSelectionContext> temperate = BiomeSelectors.foundInOverworld()
                .and(context -> !context.hasTag(BiomeTags.IS_OCEAN))
                .and(context -> !context.hasTag(BiomeTags.IS_DEEP_OCEAN))
                .and(context -> !context.hasTag(BiomeTags.IS_RIVER))
                .and(context -> !context.hasTag(BiomeTags.IS_BEACH))
                .and(context -> !context.hasTag(BiomeTags.IS_BADLANDS))
                .and(context -> !rugged.test(context));

        BiomeModifications.addFeature(rugged,
                GenerationStep.Feature.VEGETAL_DECORATION, DENSE);
        BiomeModifications.addFeature(temperate,
                GenerationStep.Feature.VEGETAL_DECORATION, SPARSE);
    }
}
