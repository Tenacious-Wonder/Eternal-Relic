package org.eternalrelic.worldgen;

import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.Identifier;
import net.minecraft.world.gen.feature.FeatureConfig;

import org.eternalrelic.EternalRelic;

/**
 * <h1>「遗落小屋」生成器的可调旋钮</h1>
 *
 * <p>这些数字全部写在数据包的配置里（{@code data/eternal_relic/worldgen/configured_feature/}），
 * 改完重启游戏就生效，不需要重新编译代码。每一项都给了默认值，因此配置文件里只写想改的那几项同样成立。</p>
 *
 * <p>真正的「长什么样」在房子自己身上（结构文件），这里只管「以什么概率、什么程度出现」。</p>
 *
 * @param variants             要用哪几栋房子。填结构文件名（不含路径），例如 {@code little_home_01}；
 *                             每次生成随机抽一个，这就是「同一栋房子有多种面貌」的来源
 * @param lootTable            这批房子默认用哪张战利品表。可以直接是一张内容表，
 *                             也可以是一张「调度表」（按权重在几套内容之间挑）
 * @param exclusiveLootTables  给个别变体开小灶：键是变体名、值是它专属的战利品表。
 *                             没列进来的变体一律用 {@code lootTable}
 * @param exclusiveLootChance  抽到有专属表的变体时，改用专属表的概率（0~1）。
 *                             余下的概率仍然用通用表，所以同一栋房子的箱子有两种可能
 * @param noBurrowVariants     永远不下沉的变体名单。少数房子铺了完整地板或地基较浅，
 *                             一埋进土里就不好看了，把它们的名字列在这儿即可
 * @param stripGround          是否削掉房子最底下那层地形。用户搭房子时把脚下那片草地一起框了进去，
 *                             不削掉的话会有一块方形草皮跟着房子搬过去，落在坡地上很难看
 * @param keepFloors           是否「只削草方块」。有些房子用户特意铺了地板（泥土、石头、灰化土等），
 *                             那些是建筑的一部分，不该当成地形削掉；而草方块基本一定是框进来的野地
 * @param requireOpenSpace     是否要求落点足够空旷。地下版本打开它，免得房子整栋埋在石头里
 * @param holeChance           破洞的发生率（0~1）。0.5 表示一半的房子会有缺口，另一半完好
 * @param burrowChance         半埋的发生率（0~1）。0.1 表示只有一成的房子陷进土里
 * @param slabChance           房子周围散落台阶的发生率（0~1）
 * @param wornSlabChance       墙上的整砖「掉成半砖」的比例（0~1）。这是强化做旧感的主力：
 *                             整砖换成台阶后，那一格矮了一半，墙面就会一截一截地缺下去
 * @param weatheringScale      风化程度的总倍率。1.0 是设计值，调大就更旧、调小就更新
 */
public record LittleHomeConfig(
        List<String> variants,
        Identifier lootTable,
        Map<String, Identifier> exclusiveLootTables,
        float exclusiveLootChance,
        List<String> noBurrowVariants,
        boolean stripGround,
        boolean keepFloors,
        boolean requireOpenSpace,
        float holeChance,
        float burrowChance,
        float slabChance,
        float wornSlabChance,
        float weatheringScale
) implements FeatureConfig {

    /** 破洞强度：一半的房子会破损，破损时从这几档里等概率抽一档。 */
    public static final float[] HOLE_STRENGTHS = {0.01F, 0.02F, 0.03F, 0.04F, 0.05F};

    /** 半埋时最多往下沉几格。 */
    public static final int BURROW_MAX_DEPTH = 3;

    /** 苔藓砖的比例上限，也就是最潮湿的地方能长到多少。 */
    public static final float MOSS_MAX = 0.15F;

    public static final Codec<LittleHomeConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().fieldOf("variants").forGetter(LittleHomeConfig::variants),
            Identifier.CODEC.optionalFieldOf("loot_table", EternalRelic.id("chests/little_home"))
                    .forGetter(LittleHomeConfig::lootTable),
            Codec.unboundedMap(Codec.STRING, Identifier.CODEC)
                    .optionalFieldOf("exclusive_loot_tables", Map.of())
                    .forGetter(LittleHomeConfig::exclusiveLootTables),
            Codec.FLOAT.optionalFieldOf("exclusive_loot_chance", 0.5F)
                    .forGetter(LittleHomeConfig::exclusiveLootChance),
            Codec.STRING.listOf().optionalFieldOf("no_burrow_variants", List.of())
                    .forGetter(LittleHomeConfig::noBurrowVariants),
            Codec.BOOL.optionalFieldOf("strip_ground", true).forGetter(LittleHomeConfig::stripGround),
            Codec.BOOL.optionalFieldOf("keep_floors", false).forGetter(LittleHomeConfig::keepFloors),
            Codec.BOOL.optionalFieldOf("require_open_space", false).forGetter(LittleHomeConfig::requireOpenSpace),
            Codec.FLOAT.optionalFieldOf("hole_chance", 0.5F).forGetter(LittleHomeConfig::holeChance),
            Codec.FLOAT.optionalFieldOf("burrow_chance", 0.10F).forGetter(LittleHomeConfig::burrowChance),
            Codec.FLOAT.optionalFieldOf("slab_chance", 0.55F).forGetter(LittleHomeConfig::slabChance),
            Codec.FLOAT.optionalFieldOf("worn_slab_chance", 0.20F).forGetter(LittleHomeConfig::wornSlabChance),
            Codec.FLOAT.optionalFieldOf("weathering_scale", 1.0F).forGetter(LittleHomeConfig::weatheringScale)
    ).apply(instance, LittleHomeConfig::new));
}
