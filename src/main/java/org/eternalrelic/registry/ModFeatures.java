package org.eternalrelic.registry;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.FeatureConfig;

import org.eternalrelic.EternalRelic;
import org.eternalrelic.worldgen.LittleHomeClusterConfig;
import org.eternalrelic.worldgen.LittleHomeClusterFeature;
import org.eternalrelic.worldgen.LittleHomeConfig;
import org.eternalrelic.worldgen.LittleHomeFeature;

/**
 * 本模组的世界生成器注册入口。
 *
 * <p>这里只登记「生成器这种东西本身」，让游戏认识本模组有两种世界生成器：</p>
 * <ul>
 *   <li>{@link #LITTLE_HOME} —— <b>单栋房子</b>。既给「小家4号」这类独立刷的房子用，
 *       也是聚落内部实际动手盖房子的那个。</li>
 *   <li>{@link #LITTLE_HOME_CLUSTER} —— <b>聚落</b>。一次决定摆几栋、摆在哪，
 *       再逐栋交给上面那个去盖。小家1／2／3／5号都由它统一调度。</li>
 * </ul>
 *
 * <p>至于它们长什么样、刷在哪里、多稀有，全在数据包的 JSON 里（{@code data/eternal_relic/worldgen/}），
 * 改那些不需要动代码。</p>
 */
public final class ModFeatures {

    /** 遗落小屋（单栋）—— 用户搭好的小石屋，生成时按当地气候与概率做旧。 */
    public static final Feature<LittleHomeConfig> LITTLE_HOME =
            register("little_home", new LittleHomeFeature(LittleHomeConfig.CODEC));

    /** 遗落小屋聚落 —— 在一处摆下若干栋，让房子能扎堆。 */
    public static final Feature<LittleHomeClusterConfig> LITTLE_HOME_CLUSTER =
            register("little_home_cluster", new LittleHomeClusterFeature(LittleHomeClusterConfig.CODEC));

    private ModFeatures() {
    }

    /**
     * 由 {@link RegistryInit#init()} 调用，触发本类静态字段初始化并完成生成器注册。
     */
    public static void register() {
    }

    private static <C extends FeatureConfig> Feature<C> register(String name, Feature<C> feature) {
        return Registry.register(Registries.FEATURE, EternalRelic.id(name), feature);
    }
}
