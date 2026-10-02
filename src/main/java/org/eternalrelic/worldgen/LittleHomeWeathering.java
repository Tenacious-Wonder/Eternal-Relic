package org.eternalrelic.worldgen;

import java.util.Optional;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.biome.Biome;

import org.eternalrelic.registry.ModBlocks;

/**
 * <h1>这栋房子被时间侵蚀成什么样</h1>
 *
 * <p>同一栋房子，落在沼泽里是绿油油的一座苔藓废墟，落在沙漠里是干巴巴的秃石屋——差别就由这个类算出来。
 * 它只负责「这块石砖要不要换、要不要裂、要不要塌」，具体盖房子的事在 {@link LittleHomeFeature} 里。</p>
 *
 * <h2>一块砖的四种下场</h2>
 * <ol>
 *   <li><b>长苔藓、裂开</b>：按气候算出的比例换材质，墙面变绿或者出现裂纹。</li>
 *   <li><b>掉成半砖</b>：整块换成台阶，那一格矮了一半，一律放在下半截。</li>
 *   <li><b>掉成楼梯</b>：换成带朝向的楼梯，<b>朝向四个方向随机、上下随机</b>——
 *       摆在整砖的位置上，看着就像墙塌了一半、露出参差的断面。</li>
 *   <li><b>彻底塌掉</b>：什么都不放，墙上留个洞。一半的房子会破损，破损的强度在 1%~5% 之间。</li>
 * </ol>
 *
 * <h2>越靠上越容易烂</h2>
 * <p>房子只有一圈墙，「墙外面」和「墙里面」是同一块砖，分不开；能分得开的是<b>高度</b>。
 * 所以做旧的程度按高度递增：屋顶与柱子的残留先塌、先掉块，墙脚和地基最结实。
 * 换算下来墙顶的损坏概率约是墙脚的 4 倍。</p>
 *
 * <h2>苔藓按气候长</h2>
 * <p>潮湿的地方长苔藓，干热的地方一点都不长。湿度取自当地生物群系（沼泽、丛林最湿，森林、针叶林其次，
 * 沙漠、恶地、热带草原最干），温度则直接读生物群系自己的数据——温度越高，苔藓越少。
 * 两头一夹，最终落在 0%（沙漠）到 {@value LittleHomeConfig#MOSS_MAX}（沼泽）之间。
 * 掉下来的半砖和楼梯也跟着这个比例走：沼泽里掉的是苔藓砖的，沙漠里掉的是干砖的。</p>
 */
public final class LittleHomeWeathering {

    /** 裂石砖的比例下限与上限。 */
    private static final float CRACK_MIN = 0.02F;
    private static final float CRACK_MAX = 0.15F;

    /** 掉块时，其中有多少比例是「楼梯」而不是「半砖」。楼梯带朝向，看着更像塌出来的断面。 */
    private static final float STAIR_SHARE = 0.25F;

    /** 楼梯的四个朝向，抽签用。 */
    private static final Direction[] STAIR_FACINGS = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    private final float mossChance;
    private final float crackChance;
    private final float wornSlabChance;
    private final float holeChance;

    private LittleHomeWeathering(float mossChance, float crackChance, float wornSlabChance, float holeChance) {
        this.mossChance = mossChance;
        this.crackChance = crackChance;
        this.wornSlabChance = wornSlabChance;
        this.holeChance = holeChance;
    }

    /**
     * 抽一次签，决定这栋房子的风化程度。
     *
     * @param random   世界生成的随机源
     * @param biome    房子所在地的生物群系
     * @param config   配置里的可调旋钮
     * @param burrowed 这栋房子是否半埋。埋进土里的会更旧——这两个特征是互相印证的，不是各自乱掷骰子
     * @return 算好的风化规则
     */
    public static LittleHomeWeathering roll(Random random, RegistryEntry<Biome> biome,
                                            LittleHomeConfig config, boolean burrowed) {
        float humidity = humidityOf(biome);
        float temperature = biome.value().getTemperature();
        float scale = config.weatheringScale() * (burrowed ? 1.35F : 1.0F);

        // 温度越高苔藓越少：2.0（沙漠）时只剩三成，0.0（雪原）时不受影响
        float heatFactor = MathHelper.clamp(1.15F - temperature * 0.35F, 0.15F, 1.0F);
        float moss = MathHelper.clamp(humidity * LittleHomeConfig.MOSS_MAX * heatFactor * scale, 0.0F, LittleHomeConfig.MOSS_MAX);

        float crack = MathHelper.clamp((0.05F + temperature * 0.04F - humidity * 0.03F) * scale, CRACK_MIN, CRACK_MAX);

        // 掉块：属于结构性损坏，跟气候关系不大，只跟整体风化倍率走。上限掐在 45%，再多墙就没了
        float wornSlab = MathHelper.clamp(config.wornSlabChance() * scale, 0.0F, 0.45F);

        // 破洞：先掷「这栋房子破不破」，破了再从 1%~5% 里等概率抽一档强度
        float hole = 0.0F;
        if (random.nextFloat() < config.holeChance()) {
            hole = LittleHomeConfig.HOLE_STRENGTHS[random.nextInt(LittleHomeConfig.HOLE_STRENGTHS.length)];
        }

        return new LittleHomeWeathering(moss, crack, wornSlab, hole);
    }

    /**
     * 决定一块方块最终变成什么。
     *
     * @param state       这块方块本来是什么
     * @param heightRatio 这块方块在房子里的相对高度，0 是墙脚、1 是最顶上
     * @param random      随机源
     * @return 换成之后的方块；返回 {@code null} 表示这块塌掉了、什么都不放
     */
    public BlockState apply(BlockState state, float heightRatio, Random random) {
        // 越靠上越容易烂：墙顶与柱子的残留先塌，墙脚和地基最结实
        float topHeaviness = 0.4F + 1.2F * heightRatio;

        // 箱子不吃这一套：它是这栋房子里唯一的收获，不该被"塌掉"吃掉
        if (!state.isOf(Blocks.CHEST)
                && holeChance > 0.0F
                && random.nextFloat() < holeChance * topHeaviness) {
            return null;
        }
        if (state.isOf(Blocks.STONE_BRICKS)) {
            float roll = random.nextFloat();
            if (roll < mossChance) {
                return Blocks.MOSSY_STONE_BRICKS.getDefaultState();
            }
            if (roll < mossChance + crackChance) {
                return Blocks.CRACKED_STONE_BRICKS.getDefaultState();
            }
            // 掉一小块：同样越靠上越容易掉
            if (random.nextFloat() < wornSlabChance * topHeaviness) {
                boolean mossy = random.nextFloat() < mossChance;
                if (random.nextFloat() < STAIR_SHARE) {
                    return randomStairs(mossy, random);
                }
                // 半砖一律放下半截，看起来就是墙掉了一截
                return mossy
                        ? Blocks.MOSSY_STONE_BRICK_SLAB.getDefaultState()
                        : Blocks.STONE_BRICK_SLAB.getDefaultState();
            }
        }
        // 石砖的台阶和楼梯也跟着裂，而且用的概率和整砖一模一样——
        // 这样一面墙上的成色才是统一的，不会出现「砖裂了、台阶却完好」的割裂感
        if (state.isOf(Blocks.STONE_BRICK_SLAB) && random.nextFloat() < crackChance) {
            return withSameShape(state, ModBlocks.CRACKED_STONE_BRICK_SLAB.getDefaultState());
        }
        if (state.isOf(Blocks.STONE_BRICK_STAIRS) && random.nextFloat() < crackChance) {
            return withSameShape(state, ModBlocks.CRACKED_STONE_BRICK_STAIRS.getDefaultState());
        }
        return state;
    }

    /**
     * 把一块方块的形状属性整套搬到另一种方块上。
     *
     * <p>楼梯和台阶身上带着朝向、上下半、内外角一堆属性。换材质时若把这些丢掉，
     * 好端端的楼梯会突然变成缺角的样子，所以这里逐个照搬过去。</p>
     *
     * @param from 原来的方块（属性从它这取）
     * @param to   要换成的方块
     * @return 换好材质、形状保持原样的方块
     */
    private static BlockState withSameShape(BlockState from, BlockState to) {
        BlockState result = to;
        for (Property<?> property : from.getProperties()) {
            if (result.contains(property)) {
                result = copyProperty(from, result, property);
            }
        }
        return result;
    }

    private static <T extends Comparable<T>> BlockState copyProperty(BlockState from, BlockState to, Property<T> property) {
        return to.with(property, from.get(property));
    }

    /**
     * 随机拼一节楼梯：朝向四个方向随便挑，上下也随便挑。
     *
     * <p>楼梯摆在原来整砖的位置上、朝向又是乱的，看起来就像墙塌了一半、露出一截参差的断面。</p>
     *
     * @param mossy  这块砖按当地气候算是否该长苔
     * @param random 随机源
     * @return 拼好的楼梯方块
     */
    private static BlockState randomStairs(boolean mossy, Random random) {
        BlockState stairs = (mossy ? Blocks.MOSSY_STONE_BRICK_STAIRS : Blocks.STONE_BRICK_STAIRS).getDefaultState();
        stairs = stairs.with(StairsBlock.FACING, STAIR_FACINGS[random.nextInt(STAIR_FACINGS.length)]);
        stairs = stairs.with(StairsBlock.HALF, random.nextBoolean() ? BlockHalf.TOP : BlockHalf.BOTTOM);
        return stairs;
    }

    /**
     * 估算当地有多潮湿。
     *
     * <p>游戏没有对外公开「降雨量」这项数据，所以这里改用生物群系的分类来判断：原版的丛林、森林、
     * 针叶林标签一眼就能认；沼泽、沙漠这类没有标签的，就按名字里有没有相应字样来认——这样别的模组
     * 加的生物群系只要名字取得规矩，同样能被认出来。</p>
     */
    private static float humidityOf(RegistryEntry<Biome> biome) {
        if (biome.isIn(BiomeTags.IS_JUNGLE)) {
            return 1.0F;
        }
        if (biome.isIn(BiomeTags.IS_FOREST) || biome.isIn(BiomeTags.IS_TAIGA)) {
            return 0.75F;
        }
        Optional<RegistryKey<Biome>> key = biome.getKey();
        if (key.isPresent()) {
            String path = key.get().getValue().getPath();
            if (path.contains("swamp") || path.contains("mangrove") || path.contains("bog") || path.contains("marsh")) {
                return 1.0F;
            }
            if (path.contains("desert") || path.contains("badlands") || path.contains("savanna") || path.contains("mesa")) {
                return 0.0F;
            }
            if (path.contains("ocean") || path.contains("river") || path.contains("beach") || path.contains("shore")) {
                return 0.6F;
            }
        }
        return 0.4F;
    }
}
