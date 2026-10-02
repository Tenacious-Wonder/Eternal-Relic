package org.eternalrelic.worldgen;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;

import org.eternalrelic.EternalRelic;
import org.eternalrelic.mixin.ChunkRegionAccessor;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * <h1>遗落小屋聚落 —— 一次在一处摆下好几栋</h1>
 *
 * <p>早先每种房子各自独立掷骰子，结果是均匀撒开、永远不会扎堆。这个类把它们收拢到一处：
 * 先定下这一处该摆几栋、各摆在哪，再逐栋交给 {@link LittleHomeFeature#placeOne} 去盖。</p>
 *
 * <h2>位置是算出来的，不是掷骰子掷出来的</h2>
 * <p>全世界按固定边长划成方格，每格一处聚落，规模由格子坐标整除规则决定
 * （详见 {@link LittleHomeClusterConfig}）。这样定下来的好处有两个：</p>
 * <ul>
 *   <li><b>密度是能保证的</b>——「每走多远必定遇到某一档」由几何决定，不靠运气；</li>
 *   <li><b>调试指令能复现</b>——{@code /relic cluster find} 不生成地形就能算出同样的结果，
 *       因为两边跑的是同一个 {@link #plan}。</li>
 * </ul>
 *
 * <h2>房子之间怎么保证不挨太近</h2>
 * <p>撒点时每放一个点都要跟已经定下的点比距离，不够 {@code min_distance} 就重掷；
 * 掷满次数还挤不下就少放几栋——宁可聚落小一点，也不让两栋房子叠在一起。
 * 另外相邻格子至少隔开 {@code 格子边长 - 2×抖动} 格，所以两处聚落本来也撞不上。</p>
 *
 * <h2>落点不平怎么办：挪，而不是硬盖</h2>
 * <p>每栋房子都跑一遍 {@link LittleHomeFeature#placeOne} 那套挑地方的检查（太陡不要、是水不要、
 * 压着别人家不要……）。原地通不过就按 {@link #SPOT_OFFSETS} 由近到远挪着试，
 * <b>只换位置，不放宽标准</b>——房子照样只坐在平地上，但不会因为一个点正好在坡上就少一栋。</p>
 *
 * <h2>为什么摆之前要先把「可写范围」调宽</h2>
 * <p>世界生成是一步一步来的，每走一步游戏都会限定「只准在离中心区块若干个区块以内写方块」，
 * 写远了<b>不报错、直接丢掉</b>。放地物这一步原版只给 1 个区块（16 格），而聚落要铺到几十格开外——
 * 不调宽的话，第二栋之后全都会凭空消失，聚落永远长不大。</p>
 *
 * <p>所以这里在动手前把限额临时调宽（见 {@link #PLACEMENT_RADIUS}），摆完立刻还原，
 * 免得连累同一区块里其它地物。详见 {@link ChunkRegionAccessor}。</p>
 */
public class LittleHomeClusterFeature extends Feature<LittleHomeClusterConfig> {

    /** 找「周围有没有已经盖好的房子」时，每隔几格采样一次。 */
    private static final int SAMPLE_STEP = 4;

    /** 采样时从地表往上、往下各看几格，用来罩住房子本体和半埋进土里的地基。 */
    private static final int SAMPLE_UP = 10;
    private static final int SAMPLE_DOWN = 4;

    /**
     * 每一栋周围多大范围内不许有别人家的房子（格）。
     *
     * <p>房子是 17×17 的，斜对角摆放要 24 格才碰不到，所以这里取 24。
     * 注意本聚落自己已经盖好的那几栋不算「别人家」，否则后一栋会被前一栋挡住。</p>
     */
    private static final int HOUSE_CLEARANCE = 24;

    /**
     * 原地盖不下去时，按这个顺序由近到远挪着试（单位：格）。
     *
     * <p>先近后远，是为了让房子尽量待在计划的位置上；最远只挪 14 格，
     * 免得把聚落的形状拽散。</p>
     */
    private static final int[][] SPOT_OFFSETS = {
            { 0, 0 },
            { 7, 0 }, { -7, 0 }, { 0, 7 }, { 0, -7 },
            { 7, 7 }, { -7, 7 }, { 7, -7 }, { -7, -7 },
            { 14, 0 }, { -14, 0 }, { 0, 14 }, { 0, -14 },
            { 14, 14 }, { -14, 14 }, { 14, -14 }, { -14, -14 }
    };

    /**
     * 挪过位置之后，两栋房子之间至少还要留这么多格（按每个轴分别算）。
     *
     * <p>房子是 17×17，斜对角摆放要 24 格才碰不到，所以这里取 20 —— 比 24 略松一点，
     * 是因为挪位之后形状本来就该有点错落，只要墙不插进别人家里就行。</p>
     */
    private static final int MIN_HOUSE_GAP = 20;

    /**
     * 摆房子时允许的随机抖动（格）。
     *
     * <p>纯粹为了让房子别排得像阅兵方阵；抖得太大会把间距压到最小距离以下，所以给得很小。</p>
     */
    private static final int JITTER = 2;

    /**
     * 候选网格铺多大：以中心为原点，两边各铺这么多格。
     *
     * <p>3 就是 7×7 个候选点（最多能摆 48 栋），比大聚落的上限 13 栋宽裕得多——
     * 留这么大余地是为了让「随机排布」真的能排出不同形状。</p>
     */
    private static final int GRID_SPAN = 3;

    /**
     * 摆聚落时，最多把「可写范围」放宽到离中心区块几个区块。
     *
     * <p>5 个区块 = 从中心往外各 80 格。最坏情况是聚落中心正好落在区块的最边上，
     * 最外一栋再往外铺 56 格、房子本身再占 9 格，加起来约 80 格，正好压线装下。
     * 再宽就是在浪费（游戏要为此多预备更大的工作区）。</p>
     */
    private static final int PLACEMENT_RADIUS = 5;

    /**
     * 一栋房子占地的半宽（格），用来判断四个角落写不写得进去。
     *
     * <p>房子是 17×17，中心到最外一格是 8；留 1 格余量兜住外面散落的半砖，取 9。</p>
     */
    private static final int HOUSE_HALF_SIZE = 9;

    /** {@link #widenPlacement} 的返回值：这次没动过范围，不需要还原。 */
    private static final int NOT_WIDENED = Integer.MIN_VALUE;

    public LittleHomeClusterFeature(Codec<LittleHomeClusterConfig> codec) {
        super(codec);
    }

    @Override
    public boolean generate(FeatureContext<LittleHomeClusterConfig> context) {
        StructureWorldAccess world = context.getWorld();
        BlockPos origin = context.getOrigin();
        LittleHomeClusterConfig config = context.getConfig();

        int chunkX = ChunkSectionPos.getSectionCoord(origin.getX());
        int chunkZ = ChunkSectionPos.getSectionCoord(origin.getZ());

        // 这一处摆几栋、摆在哪，完全由「世界种子 + 格子坐标」算出来，跟生成顺序无关；
        // 好处是调试指令能原地算出同样的结果，不必真去生成地形
        Settlement settlement = plan(world.getSeed(), config, chunkX, chunkZ);
        if (settlement.isEmpty()) {
            return false;
        }

        List<BlockPos> spots = settlement.spots();
        Random random = randomFor(world.getSeed(), settlement.cellX(), settlement.cellZ());

        int previousRadius = widenPlacement(world);
        List<BlockPos> built = new ArrayList<>();
        int lost = 0;
        try {
            for (BlockPos spot : spots) {
                LittleHomeConfig home = pickHome(random, config.homes(), spots.size());
                // 落点原地不平就挪一挪再试——不硬盖，但也不轻易少一栋
                BlockPos placed = settleNear(world, random, spot, home, built);
                if (placed == null) {
                    lost++;
                } else {
                    built.add(placed);
                }
            }

            // 房子都盖好了，再把它们用小路口连起来（盖之前连会被房子压掉）
            LittleHomePaths.connect(world, random, built);
        } finally {
            restorePlacement(world, previousRadius);
        }

        if (lost > 0) {
            EternalRelic.LOGGER.info("聚落 格子({}, {}) 计划 {} 栋 → 盖成 {} 栋（有 {} 栋附近找不到平地／被别人占了）",
                    settlement.cellX(), settlement.cellZ(), spots.size(), built.size(), lost);
        }
        return !built.isEmpty();
    }

    /**
     * 在计划落点附近找一个能盖的地方，把房子盖下去。
     *
     * <p>先试原地；原地地形不行（太陡、是水、贴着崖边、压在别的建筑上……）就按
     * {@link #SPOT_OFFSETS} 由近到远挪着试，<b>一切照旧只挑平地</b>，只是不再死守一个点。
     * 这样既保住了「房子都坐得稳稳当当」，又不会因为一个点正好在坡上就少一栋。</p>
     *
     * @param built 已经盖好的落点，用来防止挪过头跟邻居撞上
     * @return 真正盖成的位置；附近都找不到平地则返回 {@code null}
     */
    private static BlockPos settleNear(StructureWorldAccess world, Random random, BlockPos spot,
                                       LittleHomeConfig home, List<BlockPos> built) {
        for (int[] offset : SPOT_OFFSETS) {
            BlockPos candidate = spot.add(offset[0], 0, offset[1]);
            // 写不进去的位置（超出这一步被允许的范围）不用试
            if (!canPlaceHouse(world, candidate)) {
                continue;
            }
            if (!farEnough(candidate, built)) {
                continue;
            }
            // 只躲「别人家」的房子；本聚落自己已经盖好的那几栋不算障碍
            if (hasHomesNearby(world, candidate, HOUSE_CLEARANCE, built)) {
                continue;
            }
            if (LittleHomeFeature.placeOne(world, random, candidate, home)) {
                return candidate;
            }
            // 这里的地形不肯收，挪下一个位置再试
        }
        return null;
    }

    /**
     * 这个位置离本聚落已经盖好的房子够不够远。
     *
     * @return 每一栋都离得开就返回 {@code true}
     */
    private static boolean farEnough(BlockPos candidate, List<BlockPos> built) {
        for (BlockPos other : built) {
            if (Math.abs(candidate.getX() - other.getX()) < MIN_HOUSE_GAP
                    && Math.abs(candidate.getZ() - other.getZ()) < MIN_HOUSE_GAP) {
                return false;
            }
        }
        return true;
    }

    /**
     * 一处聚落的「图纸」：几栋、各摆在哪。
     *
     * <p>它不碰世界，纯粹是查规则加一串随机数算出来的结果。正因为如此，
     * {@code /relic cluster find} 才能不生成任何地形就把它算出来——
     * 世界生成与调试指令共用 {@link #plan} 这一个源头，两边不可能对不上。</p>
     *
     * @param spots 每一栋的落点（绝对坐标；{@code y} 没有意义，盖的时候会现量地面）
     * @param cellX 这一处落在哪个格子里（摆房子时要用它取同一串随机数）
     * @param cellZ 同上
     */
    public record Settlement(List<BlockPos> spots, int cellX, int cellZ) {

        /** 这一处没有聚落。 */
        public static final Settlement NONE = new Settlement(List.of(), 0, 0);

        public boolean isEmpty() {
            return spots.isEmpty();
        }

        public int size() {
            return spots.size();
        }

        /** @return 聚落中心（第一栋的落点） */
        public BlockPos center() {
            return spots.get(0);
        }
    }

    /**
     * 把「世界种子 + 格子坐标」搅成这个格子专属的随机源。
     *
     * @param worldSeed 世界种子
     * @param cellX     格子 X
     * @param cellZ     格子 Z
     * @return 只由这三个数决定的随机源
     */
    public static Random randomFor(long worldSeed, int cellX, int cellZ) {
        long seed = worldSeed
                ^ (long) cellX * 0x9E3779B97F4A7C15L
                ^ (long) cellZ * 0xC2B2AE3D27D4EB4FL;
        return Random.create(mix(seed));
    }

    /**
     * 把一个数彻底打散，免得相邻格子的种子长得像亲戚（那样聚落会排成规律的花纹）。
     *
     * @param value 原始数
     * @return 打散后的数
     */
    private static long mix(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * 算出一个区块里该不该有聚落、有几栋、分别摆在哪。
     *
     * <p>步骤：先看这个区块压在哪个格子上，再查那个格子是哪一档；
     * 只有「聚落中心正好落在这个区块里」的那个区块会算出东西，其余返回空。
     * 所以一處聚落只会被生成一次，不会重复。</p>
     *
     * @param worldSeed 世界种子
     * @param config    聚落参数
     * @param chunkX    要算的区块 X
     * @param chunkZ    要算的区块 Z
     * @return 这一处的图纸；这个区块不是聚落所在时返回 {@link Settlement#NONE}
     */
    public static Settlement plan(long worldSeed, LittleHomeClusterConfig config, int chunkX, int chunkZ) {
        if (config.homes().isEmpty() || config.classes().isEmpty()) {
            return Settlement.NONE;
        }

        int grid = Math.max(16, config.grid());
        int jitter = Math.max(1, grid / 6);

        // 一个区块可能横跨两格（格子比区块大得多，通常只压着一格）
        int minCellX = Math.floorDiv(chunkX * 16, grid);
        int maxCellX = Math.floorDiv(chunkX * 16 + 15, grid);
        int minCellZ = Math.floorDiv(chunkZ * 16, grid);
        int maxCellZ = Math.floorDiv(chunkZ * 16 + 15, grid);

        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                LittleHomeClusterConfig.SizeClass sizeClass = config.classAt(cellX, cellZ);
                if (sizeClass == null) {
                    continue;
                }

                Random random = randomFor(worldSeed, cellX, cellZ);
                // 中心撒在格子的中间一段里：既不会长出方格感，又保证站点之间永远离得够远
                int centerX = cellX * grid + grid / 2 + random.nextInt(jitter * 2 + 1) - jitter;
                int centerZ = cellZ * grid + grid / 2 + random.nextInt(jitter * 2 + 1) - jitter;
                if (ChunkSectionPos.getSectionCoord(centerX) != chunkX
                        || ChunkSectionPos.getSectionCoord(centerZ) != chunkZ) {
                    continue;
                }

                int count = pickCount(random, sizeClass);
                int minDistance = config.minDistance();
                if (config.minDistanceMax() > minDistance) {
                    minDistance += random.nextInt(config.minDistanceMax() - minDistance + 1);
                }

                return new Settlement(scatter(random, new BlockPos(centerX, 0, centerZ), count, minDistance),
                        cellX, cellZ);
            }
        }
        return Settlement.NONE;
    }

    /**
     * 动手前把「这一步能往多远写方块」的范围临时调宽。
     *
     * @param world 当前的工作区
     * @return 调宽之前的范围，交给 {@link #restorePlacement} 还原；没调过则返回 {@link #NOT_WIDENED}
     */
    private static int widenPlacement(StructureWorldAccess world) {
        if (!(world instanceof ChunkRegion region)) {
            return NOT_WIDENED;
        }

        ChunkRegionAccessor accessor = (ChunkRegionAccessor) region;
        int previous = accessor.getPlacementRadius();
        accessor.setPlacementRadius(Math.max(previous, availableRadius(region)));
        return previous;
    }

    /**
     * 问游戏「这一片实际最远能写到哪里」，再自己封顶到 {@link #PLACEMENT_RADIUS}。
     *
     * <p>不写死数值是为了稳：万一别的地方把工作区改小了，这里跟着变小，不会撞上去报错。</p>
     *
     * @param region 当前的工作区
     * @return 可以安全使用的范围（单位：区块）
     */
    private static int availableRadius(ChunkRegion region) {
        ChunkPos center = region.getCenterPos();
        int radius = 1;
        while (radius < PLACEMENT_RADIUS
                && region.isChunkLoaded(center.x + radius + 1, center.z)
                && region.isChunkLoaded(center.x - radius - 1, center.z)
                && region.isChunkLoaded(center.x, center.z + radius + 1)
                && region.isChunkLoaded(center.x, center.z - radius - 1)) {
            radius++;
        }
        return radius;
    }

    /**
     * 把范围调回原样，免得连累同一区块里排在后面生成的其它地物。
     *
     * @param world          当前的工作区
     * @param previousRadius {@link #widenPlacement} 交回来的原值
     */
    private static void restorePlacement(StructureWorldAccess world, int previousRadius) {
        if (previousRadius == NOT_WIDENED || !(world instanceof ChunkRegion region)) {
            return;
        }
        ((ChunkRegionAccessor) region).setPlacementRadius(previousRadius);
    }

    /**
     * 这一栋放得下吗——看它占地的四个角落是不是都在可写范围内。
     *
     * <p>范围是按区块算的，横竖两个方向各自独立，所以四个角都通过，整块地就都通过。</p>
     *
     * @param world  当前的工作区
     * @param center 房子中心（柱心）
     * @return 四个角落都写得进去就是 {@code true}
     */
    private static boolean canPlaceHouse(StructureWorldAccess world, BlockPos center) {
        int half = HOUSE_HALF_SIZE;
        return world.isValidForSetBlock(center.add(-half, 0, -half))
                && world.isValidForSetBlock(center.add(half, 0, -half))
                && world.isValidForSetBlock(center.add(-half, 0, half))
                && world.isValidForSetBlock(center.add(half, 0, half));
    }

    /**
     * 抽这一处要摆几栋。
     *
     * @param random    随机源
     * @param sizeClass 这一格对应的档位
     * @return 本次的栋数
     */
    private static int pickCount(Random random, LittleHomeClusterConfig.SizeClass sizeClass) {
        int min = Math.max(1, sizeClass.minHomes());
        int max = Math.max(min, sizeClass.maxHomes());
        return min + random.nextInt(max - min + 1);
    }

    /**
     * 抽这一栋用哪种房子。
     *
     * <p>带「最小聚落规模」门槛的会先被筛掉——那种房子只在够大的聚落里露面。</p>
     *
     * @param random      随机源
     * @param homes       参与抽取的房子种类
     * @param clusterSize 本次聚落一共几栋
     * @return 抽中的那套参数
     */
    private static LittleHomeConfig pickHome(Random random, List<LittleHomeClusterConfig.Home> homes, int clusterSize) {
        int total = 0;
        for (LittleHomeClusterConfig.Home home : homes) {
            if (home.minClusterSize() <= clusterSize) {
                total += home.weight();
            }
        }
        if (total <= 0) {
            // 门槛把候选全挡掉了（配置写岔了才会这样），退而求其次：从不过门槛的那些里挑
            for (LittleHomeClusterConfig.Home home : homes) {
                if (home.minClusterSize() <= 1) {
                    return home.home();
                }
            }
            return homes.get(0).home();
        }

        int roll = random.nextInt(total);
        for (LittleHomeClusterConfig.Home home : homes) {
            if (home.minClusterSize() > clusterSize) {
                continue;
            }
            roll -= home.weight();
            if (roll < 0) {
                return home.home();
            }
        }
        return homes.get(0).home();
    }

    /**
     * 把房子摆在中心周围。
     *
     * <p>先在中心定下第一栋，再把周围 {@value #GRID_SPAN}×{@value #GRID_SPAN} 个网格点全列成候选，
     * 按「离中心的距离 + 一点随机扰动」排序，取够栋数就停。</p>
     *
     * <p>这样出来的形状每次都不同：有时饱满接近圆形，有时缺一角，有时往外伸出一条——
     * 而不是每处都长成一个规规矩矩的方阵。扰动幅度控制在一格间距以内，
     * 所以「离得远的先被选中、把近的挤掉」这种事不会发生。</p>
     *
     * @param random 随机源
     * @param origin 聚落中心
     * @param count  要摆几栋
     * @param gap    房子之间的最小中心距
     * @return 每一栋的落点
     */
    private static List<BlockPos> scatter(Random random, BlockPos origin, int count, int gap) {
        List<BlockPos> spots = new ArrayList<>();
        spots.add(origin);
        if (count <= 1) {
            return spots;
        }

        // 候选网格点：以中心为原点排开，排序键里掺进随机量，让同一圈里的次序每次都不同
        int span = GRID_SPAN;
        List<Map.Entry<BlockPos, Double>> candidates = new ArrayList<>();
        for (int dx = -span; dx <= span; dx++) {
            for (int dz = -span; dz <= span; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos spot = origin.add(dx * gap, 0, dz * gap);
                double key = spot.getSquaredDistance(origin) + random.nextDouble() * gap * gap;
                candidates.add(new AbstractMap.SimpleEntry<>(spot, key));
            }
        }
        candidates.sort(Map.Entry.comparingByValue());

        for (Map.Entry<BlockPos, Double> candidate : candidates) {
            if (spots.size() >= count) {
                break;
            }
            BlockPos spot = candidate.getKey();
            spots.add(spot.add(jitter(random), 0, jitter(random)));
        }
        return spots;
    }

    private static int jitter(Random random) {
        return random.nextInt(JITTER * 2 + 1) - JITTER;
    }

    /**
     * 看看这一带是不是已经有房子了。
     *
     * <p>隔几格采一次样，从地表往上往下各看一小段，认石砖系方块。
     * 采样是稀疏的，理论上可能漏掉一栋，但代价是可控的——
     * 真漏了也只是两处靠得近些，不会叠在一起（叠不叠由撒点时的间距把关）。</p>
     */
    public static boolean hasHomesNearby(StructureWorldAccess world, BlockPos origin, int radius) {
        return hasHomesNearby(world, origin, radius, List.of());
    }

    /**
     * 同上，但可以把「自己家的房子」排除在外。
     *
     * <p>聚落里一栋一栋接着盖，后一栋不该被前一栋挡住——所以要把本聚落
     * {@code ours} 里已经盖好的那几个位置上的砖石当成自己人，认到也不算数。</p>
     *
     * @param ours 本聚落已经盖好的落点
     * @return 这一带有没有「别人家」的房子
     */
    private static boolean hasHomesNearby(StructureWorldAccess world, BlockPos origin, int radius,
                                          List<BlockPos> ours) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx += SAMPLE_STEP) {
            for (int dz = -radius; dz <= radius; dz += SAMPLE_STEP) {
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                if (insideOurs(x, z, ours)) {
                    continue;
                }
                int top = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z);
                for (int y = top - SAMPLE_DOWN; y <= top + SAMPLE_UP; y++) {
                    cursor.set(x, y, z);
                    if (isHomeMaterial(world.getBlockState(cursor))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 这个坐标是不是落在我们自己已经盖好的房子占地里。
     *
     * @param ours 本聚落已经盖好的落点
     * @return 落在其中任意一栋的占地里就返回 {@code true}
     */
    private static boolean insideOurs(int x, int z, List<BlockPos> ours) {
        for (BlockPos house : ours) {
            if (Math.abs(x - house.getX()) <= HOUSE_HALF_SIZE
                    && Math.abs(z - house.getZ()) <= HOUSE_HALF_SIZE) {
                return true;
            }
        }
        return false;
    }

    /**
     * 这块方块是不是「房子留下的痕迹」。
     *
     * <p>只认石砖系：石头、圆石那些天然地形里到处都是，认了就等于永远不放聚落；
     * 而石砖只有人工建筑才有，别的模组的砖石结构也会被认出来——正好一起避开。</p>
     */
    private static boolean isHomeMaterial(BlockState state) {
        return state.isOf(Blocks.STONE_BRICKS)
                || state.isOf(Blocks.MOSSY_STONE_BRICKS)
                || state.isOf(Blocks.CRACKED_STONE_BRICKS)
                || state.isOf(Blocks.CHISELED_STONE_BRICKS);
    }
}
