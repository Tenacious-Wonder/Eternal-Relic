package org.eternalrelic.worldgen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;
import net.minecraft.world.gen.structure.Structure;

import org.eternalrelic.EternalRelic;

/**
 * <h1>「遗落小屋」—— 把用户搭好的小石屋盖进世界里</h1>
 *
 * <p>每次生成做这几件事，顺序不能颠倒：</p>
 * <ol>
 *   <li><b>抽一栋房子</b>：从配置列出的几份图纸里随机挑一份，这就是「同一个地方每次遇到都不太一样」的来源。</li>
 *   <li><b>削掉地面</b>：用户搭房子时把脚下那层草地一起框进了图纸，先把它剔掉，房子才能贴到当地地形上。</li>
 *   <li><b>挑朝向</b>：四个方向随机转一个。</li>
 *   <li><b>量地</b>：把房子要占的每一列地表高度都扫一遍。这一步是「不悬空」的关键——
 *       只量中心一个点的话，地面稍有起伏，偏高一侧的墙脚就会吊在半空。</li>
 *   <li><b>挑地方</b>：太陡（高差超过 {@value #MAX_SLOPE} 格）不要，落在水面或冰面上也不要。</li>
 *   <li><b>掷骰子</b>：要不要半埋、风化到什么程度。半埋的房子风化会自动加重，因为埋进土里的本来就该更旧。</li>
 *   <li><b>逐块决定</b>：照图纸走一遍，每块石砖都问一次「你塌了吗」「长苔藓了吗」
 *       「掉成半砖还是楼梯」。做旧程度随高度递增——墙顶先烂，墙脚最结实。</li>
 *   <li><b>补地基</b>：从墙脚往下把缝填到实地，用和房子同样的石砖。这一步保证房子稳稳坐在地上。</li>
 *   <li><b>周围散落</b>：最后在附近丢一两个台阶，像是从墙上崩下来的。</li>
 * </ol>
 */
public class LittleHomeFeature extends Feature<LittleHomeConfig> {

    /** 四个朝向，每次生成随机挑一个，免得全世界的房子都朝同一个方向。 */
    private static final BlockRotation[] ROTATIONS = {
            BlockRotation.NONE,
            BlockRotation.CLOCKWISE_90,
            BlockRotation.CLOCKWISE_180,
            BlockRotation.COUNTERCLOCKWISE_90
    };

    /** 房子周围散落台阶的最近与最远距离（格）。 */
    private static final double SLAB_MIN_DISTANCE = 2.0;
    private static final double SLAB_MAX_DISTANCE = 6.0;

    /** 地下版本要求落点至少这么空，否则换地方——免得房子整栋埋在石头里。 */
    private static final float REQUIRED_OPEN_RATIO = 0.5F;

    /**
     * 房子占地范围内允许的最大高差。
     *
     * <p>超过这个数就说明那是一块坡地或悬崖边。硬要盖的话，房子要么一大截埋进土里、
     * 要么一侧吊在半空，两种都不好看，所以宁可换地方。</p>
     */
    private static final int MAX_SLOPE = 3;

    /** 除了房子自己占的地，还要往外多看几圈，用来判断这一带是不是河岸、崖边。 */
    private static final int NEARBY_MARGIN = 8;

    /** 周围这一圈里水面占比超过这个数就不盖——免得房子半拉在河里。 */
    private static final float MAX_NEARBY_WATER = 0.05F;

    /** 周围地形的平均高度跟落点差太多也不盖，说明正站在崖边或谷底。 */
    private static final int MAX_NEARBY_DIFF = 4;

    /** 房子周围还要往外踩乱几格的地面。 */
    private static final int GROUND_MARGIN = 3;

    /** 踩乱地面时「种子」的密度。种子会互相吸引、长成一片片的斑块，而不是均匀撒开。 */
    private static final float GROUND_SEED_CHANCE = 0.04F;

    /** 旁边已经有两格被换过时，这一格跟着换的概率。这是斑块能连成片的来源。 */
    private static final float GROUND_GROW_CHANCE = 0.75F;

    /** 生长几轮。轮数越多，斑块越大、越连片。 */
    private static final int GROUND_GROW_PASSES = 2;

    /** 周围散落的半砖可以有哪些款式——废墟边上落下什么都有可能。 */
    private static final Block[] SCATTER_SLABS = {
            Blocks.STONE_SLAB,
            Blocks.SMOOTH_STONE_SLAB,
            Blocks.COBBLESTONE_SLAB,
            Blocks.MOSSY_COBBLESTONE_SLAB,
            Blocks.STONE_BRICK_SLAB,
            Blocks.MOSSY_STONE_BRICK_SLAB
    };

    /** 补地基时混入多少苔石砖，让地基和风化过的墙面看起来是一套的。 */
    private static final float FOUNDATION_MOSS = 0.25F;

    public LittleHomeFeature(Codec<LittleHomeConfig> codec) {
        super(codec);
    }

    /**
     * 独立生成一栋房子时，先看看附近有没有别人家的房子。
     *
     * <p>房子是 17×17 的，斜对角摆放要 24 格才碰不到，所以独立生成的房子也按这个距离互相避让——
     * 否则两栋会撞在一起（这是「独立掷骰子」必然会发生的事，跟聚落无关）。</p>
     */
    private static final int INDEPENDENT_GAP = 24;

    @Override
    public boolean generate(FeatureContext<LittleHomeConfig> context) {
        // 独立生成（比如小家4号）：跟附近的房子保持距离，别挤成一团
        if (LittleHomeClusterFeature.hasHomesNearby(context.getWorld(), context.getOrigin(), INDEPENDENT_GAP)) {
            return false;
        }
        return placeOne(context.getWorld(), context.getRandom(), context.getOrigin(), context.getConfig());
    }

    /**
     * 在指定位置放下一栋房子。
     *
     * <p>整套生成逻辑都在这儿：抽变体、削地面、量地、挑地方、做旧、补地基、周围散落。
     * 之所以从地物入口里单独抽出来，是为了让{@link LittleHomeClusterFeature 聚落}那种
     * 一次要摆好几栋的调度层能直接调用它，而不必假装自己是一个地物。</p>
     *
     * @param world  世界
     * @param random 随机源
     * @param origin 落点
     * @param config 这栋房子用哪套参数
     * @return 有没有真的把房子放下去（落点不合适、或结构文件缺失时返回 {@code false}）
     */
    public static boolean placeOne(StructureWorldAccess world, Random random, BlockPos origin, LittleHomeConfig config) {
        return placeOne(world, random, origin, config, false);
    }

    /**
     * 同上，但可以要求它把「为什么没盖成」记进日志。
     *
     * <p>排查「明明算好了位置却一栋都没有」这种问题时用得上——不然只能看到一句
     * 「地形不肯收」，却不知道卡在哪一关。</p>
     *
     * @param diagnose 要不要把放弃的原因写进日志（只给调试用，正式生成时关掉）
     * @return 有没有真的把房子放下去
     */
    public static boolean placeOne(StructureWorldAccess world, Random random, BlockPos origin,
                                   LittleHomeConfig config, boolean diagnose) {
        ServerWorld serverWorld = world.toServerWorld();
        MinecraftServer server = serverWorld.getServer();
        if (server == null) {
            return refuse(diagnose, "拿不到服务器");
        }

        List<String> variants = config.variants();
        if (variants.isEmpty()) {
            return refuse(diagnose, "这套参数里一个变体都没配");
        }
        String variant = variants.get(random.nextInt(variants.size()));

        // 少数房子有自己的专属收获：抽到这类变体时，按配置的概率决定这次开哪张表，
        // 没抽中的（以及没配专属表的）一律走通用表
        Identifier lootTable = config.lootTable();
        Identifier exclusive = config.exclusiveLootTables().get(variant);
        if (exclusive != null && random.nextFloat() < config.exclusiveLootChance()) {
            lootTable = exclusive;
        }

        Optional<LittleHomeTemplate> loaded = LittleHomeTemplate.load(server, EternalRelic.id(variant));
        if (loaded.isEmpty()) {
            EternalRelic.LOGGER.warn("遗落小屋找不到结构文件：{}", variant);
            return false;
        }        LittleHomeTemplate template = loaded.get();

        // 第一步：削掉图纸里混进来的那层地形，只留下建筑本体
        List<LittleHomeTemplate.Piece> pieces = new ArrayList<>();
        for (LittleHomeTemplate.Piece piece : template.pieces()) {
            if (config.stripGround() && isGroundCover(piece.state(), config.keepFloors())) {
                continue;
            }
            pieces.add(piece);
        }
        if (pieces.isEmpty()) {
            return false;
        }

        // 第二步：挑朝向
        BlockRotation rotation = ROTATIONS[random.nextInt(ROTATIONS.length)];

        int lowestY = Integer.MAX_VALUE;
        for (LittleHomeTemplate.Piece piece : pieces) {
            lowestY = Math.min(lowestY, piece.offset().getY());
        }

        // 第三步：算出旋转之后的水平范围，好把房子摆在落点的正中间
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (LittleHomeTemplate.Piece piece : pieces) {
            BlockPos turned = turn(rotation, piece.offset().getX(), piece.offset().getZ());
            minX = Math.min(minX, turned.getX());
            maxX = Math.max(maxX, turned.getX());
            minZ = Math.min(minZ, turned.getZ());
            maxZ = Math.max(maxZ, turned.getZ());
        }
        int shiftX = origin.getX() - (minX + maxX) / 2;
        int shiftZ = origin.getZ() - (minZ + maxZ) / 2;
        int floorX0 = shiftX + minX;
        int floorX1 = shiftX + maxX;
        int floorZ0 = shiftZ + minZ;
        int floorZ1 = shiftZ + maxZ;

        // 第四步：量地。房子自己占的那块要量细（用来定基准、看平不平），
        // 外围还要多量一圈（用来看这一带是不是河岸、崖边）
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        long groundSum = 0L;
        int groundCount = 0;
        int lowestGround = Integer.MAX_VALUE;
        int highestGround = Integer.MIN_VALUE;
        long nearbySum = 0L;
        int nearbyCount = 0;
        int nearbyWater = 0;

        for (int x = floorX0 - NEARBY_MARGIN; x <= floorX1 + NEARBY_MARGIN; x++) {
            for (int z = floorZ0 - NEARBY_MARGIN; z <= floorZ1 + NEARBY_MARGIN; z++) {
                int top = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z);
                nearbySum += top;
                nearbyCount++;
                cursor.set(x, top - 1, z);
                if (!world.getFluidState(cursor).isEmpty()) {
                    nearbyWater++;
                }
                if (x >= floorX0 && x <= floorX1 && z >= floorZ0 && z <= floorZ1) {
                    groundSum += top;
                    groundCount++;
                    lowestGround = Math.min(lowestGround, top);
                    highestGround = Math.max(highestGround, top);
                }
            }
        }
        if (groundCount == 0 || nearbyCount == 0) {
            return refuse(diagnose, "量不到地面");
        }

        int averageGround = (int) (groundSum / groundCount);

        // 第五步：挑地方——聚落里的房子也一视同仁，只挑平整的地方
        // 一、房子自己占的这块地太陡就不盖
        if (highestGround - lowestGround > MAX_SLOPE) {
            return refuse(diagnose, "地面太陡（高差 " + (highestGround - lowestGround)
                    + "，上限 " + MAX_SLOPE + "）");
        }
        // 二、这一带水太多也不盖：河岸、湖岸、海湾
        if ((float) nearbyWater / nearbyCount > MAX_NEARBY_WATER) {
            return refuse(diagnose, "周围水太多（" + nearbyWater + "/" + nearbyCount + "）");
        }
        cursor.set(origin.getX(), lowestGround - 1, origin.getZ());
        BlockState ground = world.getBlockState(cursor);
        // 三、落点脚下的地面本身是水或冰，不盖
        if (ground.isAir()
                || !ground.getFluidState().isEmpty()
                || ground.isOf(Blocks.ICE)
                || ground.isOf(Blocks.PACKED_ICE)
                || ground.isOf(Blocks.BLUE_ICE)) {
            return refuse(diagnose, "脚下是空气／水／冰（" + ground.getBlock() + "）");
        }
        // 四、这块地已经有主了（村庄、前哨站、废弃传送门之类），让开
        if (insideOtherStructure(world, origin.getX(), lowestGround, origin.getZ())) {
            return refuse(diagnose, "落在别的结构里（村庄／矿井之类）");
        }

        int nearbyAverage = (int) (nearbySum / nearbyCount);
        // 五、周围地形跟落点差太多，说明正站在崖边或谷底，不盖
        if (Math.abs(nearbyAverage - averageGround) > MAX_NEARBY_DIFF) {
            return refuse(diagnose, "贴着崖边（周边平均 " + nearbyAverage
                    + "，脚下 " + averageGround + "）");
        }

        // 第六步：掷骰子——半埋与风化。名单上的变体永远不下沉
        boolean burrowed = !config.noBurrowVariants().contains(variant)
                && random.nextFloat() < config.burrowChance();
        int burrowDepth = burrowed ? 1 + random.nextInt(LittleHomeConfig.BURROW_MAX_DEPTH) : 0;
        LittleHomeWeathering weathering = LittleHomeWeathering.roll(random, world.getBiome(origin), config, burrowed);

        // 房子最低那层坐在平均地面上；半埋时整体再往下沉几格
        int baseY = averageGround - 1 - burrowDepth;

        // 先把房子内外一小片地面踩乱：草皮换成砂土、沙砾、圆石。
        // 必须在盖房子之前做——房子一盖上，这一层就被地基压住了
        roughenGround(world, random, floorX0, floorZ0, floorX1, floorZ1, config.keepFloors());

        if (config.requireOpenSpace() && !hasRoom(world, floorX0, baseY, floorZ0,
                floorX1 - floorX0 + 1, template.size().getY(), floorZ1 - floorZ0 + 1)) {
            return refuse(diagnose, "这块地方不够空");
        }

        int topY = lowestY;
        for (LittleHomeTemplate.Piece piece : pieces) {
            topY = Math.max(topY, piece.offset().getY());
        }
        int heightSpan = Math.max(1, topY - lowestY);

        // 第七步：先算出每块方块最终变成什么。分两趟是故意的——中间要插一道「塌方」，
        // 好把「下面被挖空、上面还挂着」的孤立方块一起清掉
        Map<BlockPos, BlockState> plan = new LinkedHashMap<>();
        Set<BlockPos> hollowed = new HashSet<>();

        for (LittleHomeTemplate.Piece piece : pieces) {
            BlockPos turned = turn(rotation, piece.offset().getX(), piece.offset().getZ());
            float heightRatio = (piece.offset().getY() - lowestY) / (float) heightSpan;
            BlockState weathered = weathering.apply(piece.state().rotate(rotation), heightRatio, random);
            BlockPos target = new BlockPos(
                    shiftX + turned.getX(),
                    baseY + (piece.offset().getY() - lowestY),
                    shiftZ + turned.getZ());
            if (weathered == null) {
                hollowed.add(target);
                continue;
            }
            plan.put(target, weathered);
        }

        // 塌方连锁：某一块被风化挖空之后，压在它上面的东西也塌，一路上塌到该列顶端。
        // 这样缺口是「从下往上烂穿」的，不会留下半空里孤零零的一块砖。
        // 注意只连锁「本来要放、后来被挖掉」的位置——图纸里本来就空的门窗洞口不算，
        // 所以门窗上方该留的砖一块都不会多塌
        for (BlockPos hole : hollowed) {
            BlockPos above = hole.up();
            while (plan.remove(above) != null) {
                above = above.up();
            }
        }

        // 第八步：动手盖。做旧程度随高度递增，所以前面要先知道这栋房子有多高
        int placed = 0;
        for (Map.Entry<BlockPos, BlockState> entry : plan.entrySet()) {
            BlockPos target = entry.getKey();
            BlockState state = entry.getValue();
            // 树可以穿过房子，房子不许把树劈开：这儿已经有树干树叶，就把这一块让给树
            BlockState existing = world.getBlockState(target);
            if (existing.isIn(BlockTags.LOGS) || existing.isIn(BlockTags.LEAVES)) {
                continue;
            }
            world.setBlockState(target, state, Block.NOTIFY_LISTENERS);
            if (state.isOf(Blocks.CHEST)) {
                // 图纸里的箱子是空的，内容交给战利品表：玩家第一次打开时才现抽。
                // 用哪张表由配置决定，所以不同档位的遗迹可以给不同的收获
                LootableContainerBlockEntity.setLootTable(world, random, target, lootTable);
            }
            placed++;
        }

        if (placed == 0) {
            return false;
        }

        // 第九步：把墙脚下方与实地之间的缝填上
        fillFoundation(world, random, floorX0, floorZ0, floorX1, floorZ1, baseY);

        // 第十步：周围散落几个台阶
        scatterSlabs(world, random, origin, config);
        return true;
    }

    /**
     * 放弃盖这一栋，顺便说明原因。
     *
     * <p>只有排查问题时才把原因写进日志（正式生成时吵得慌）。</p>
     *
     * @param diagnose 要不要把原因写进日志
     * @param reason   为什么放弃
     * @return 一律返回 {@code false}，好让调用处写成 {@code return refuse(...)}
     */
    private static boolean refuse(boolean diagnose, String reason) {
        if (diagnose) {
            EternalRelic.LOGGER.info("放弃一栋小屋：{}", reason);
        }
        return false;
    }

    /**
     * 把水平坐标按朝向转过去。竖直方向不受影响。
     *
     * @param rotation 这次采用的朝向
     * @param x        图纸上的横坐标
     * @param z        图纸上的纵坐标
     * @return 转好之后的坐标（y 恒为 0，只用来算水平位置）
     */
    private static BlockPos turn(BlockRotation rotation, int x, int z) {
        return switch (rotation) {
            case NONE -> new BlockPos(x, 0, z);
            case CLOCKWISE_90 -> new BlockPos(-z, 0, x);
            case CLOCKWISE_180 -> new BlockPos(-x, 0, -z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(z, 0, -x);
        };
    }

    /**
     * 把房子内外一小片地表踩乱：草皮换成砂土、沙砾、圆石。
     *
     * <p>一片干干净净的草地中间突然立着一栋石屋，怎么看都像刚放上去的。把周围和屋里的地面
     * 弄得斑斑驳驳，才有「这里曾经有人住过、后来荒掉了」的味道。</p>
     *
     * <h2>为什么要「长」而不是直接撒</h2>
     * <p>均匀地随机撒点，看上去就是一片均匀的噪声，像电视雪花，一点都不像人待过的地方。
     * 所以这里分两步：先随机撒少量种子，再让种子互相吸引、连成一片片斑块——
     * 出来的效果是「这儿一坨、那儿一坨」，才像踩出来的路子。</p>
     *
     * <p>只动最上面那一层，而且只认本来就长在地表的方块（草、泥土、沙子这类）；
     * 水面和已经铺好的石头不动。</p>
     */
    private static void roughenGround(StructureWorldAccess world, Random random,
                                      int minX, int minZ, int maxX, int maxZ, boolean keepFloors) {
        int startX = minX - GROUND_MARGIN;
        int startZ = minZ - GROUND_MARGIN;
        int width = (maxX + GROUND_MARGIN) - startX + 1;
        int depth = (maxZ + GROUND_MARGIN) - startZ + 1;
        if (width <= 0 || depth <= 0) {
            return;
        }

        // 第一步：随机撒种子
        boolean[] worn = new boolean[width * depth];
        for (int i = 0; i < worn.length; i++) {
            worn[i] = random.nextFloat() < GROUND_SEED_CHANCE;
        }

        // 第二步：让种子长起来。一格周围要是已经有两格被换过，它就更可能跟着换
        for (int pass = 0; pass < GROUND_GROW_PASSES; pass++) {
            boolean[] grown = worn.clone();
            for (int ix = 0; ix < width; ix++) {
                for (int iz = 0; iz < depth; iz++) {
                    int index = ix * depth + iz;
                    if (worn[index]) {
                        continue;
                    }
                    int neighbours = 0;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dz == 0) {
                                continue;
                            }
                            int nx = ix + dx;
                            int nz = iz + dz;
                            if (nx < 0 || nx >= width || nz < 0 || nz >= depth) {
                                continue;
                            }
                            if (worn[nx * depth + nz]) {
                                neighbours++;
                            }
                        }
                    }
                    if (neighbours >= 2 && random.nextFloat() < GROUND_GROW_CHANCE) {
                        grown[index] = true;
                    }
                }
            }
            worn = grown;
        }

        // 第三步：照着这张表动手换方块
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int ix = 0; ix < width; ix++) {
            for (int iz = 0; iz < depth; iz++) {
                if (!worn[ix * depth + iz]) {
                    continue;
                }
                int x = startX + ix;
                int z = startZ + iz;
                cursor.set(x, world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z) - 1, z);
                if (!world.getFluidState(cursor).isEmpty()) {
                    continue;
                }
                BlockState here = world.getBlockState(cursor);
                if (!isGroundCover(here, keepFloors)) {
                    continue;
                }
                world.setBlockState(cursor, pickGroundCover(random), Block.NOTIFY_LISTENERS);
            }
        }
    }

    /**
     * 抽一种踩乱地面用的方块：砂土四成、沙砾三成、圆石三成。
     *
     * @param random 随机源
     * @return 换上去的方块
     */
    private static BlockState pickGroundCover(Random random) {
        float roll = random.nextFloat();
        if (roll < 0.4F) {
            return Blocks.COARSE_DIRT.getDefaultState();
        }
        if (roll < 0.7F) {
            return Blocks.GRAVEL.getDefaultState();
        }
        return Blocks.COBBLESTONE.getDefaultState();
    }

    /**
     * 从墙脚往下填到实地，用和房子同样的石砖。
     *
     * <p>地面永远不可能和房子底面一样平，房子定在平均高度上，就意味着一半的位置底下有缝。
     * 与其让房子吊着，不如把这些缝用石砖垫实——看起来就是房子自己的地基，也不会一眼看出是补的。</p>
     */
    private static void fillFoundation(StructureWorldAccess world, Random random,
                                       int minX, int minZ, int maxX, int maxZ, int baseY) {
        BlockState plain = Blocks.STONE_BRICKS.getDefaultState();
        BlockState mossy = Blocks.MOSSY_STONE_BRICKS.getDefaultState();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int groundTop = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z) - 1;
                for (int y = baseY - 1; y > groundTop; y--) {
                    cursor.set(x, y, z);
                    if (!world.getBlockState(cursor).isAir()) {
                        continue;
                    }
                    world.setBlockState(cursor, random.nextFloat() < FOUNDATION_MOSS ? mossy : plain,
                            Block.NOTIFY_LISTENERS);
                }
            }
        }
    }

    /**
     * 检查落点是不是压在别人身上了——村庄、掠夺者前哨站、废弃传送门这些都算。
     *
     * <p>游戏自带的遗迹之间靠「间距」互相避让，而我们的房子是一种地物、没赶上那套机制，
     * 所以得自己问一句「这块地归谁」。</p>
     *
     * <p><b>不能只看区块。</b>结构规划时会把「我可能伸进这个区块」记在区块上，而且这个记号
     * 会往外扩散几十格。早年这里正是这么写的，结果是：只要附近有座矿井，整片区域的地都算
     * 「有主」，房子一栋也盖不出来——而矿井埋在地下几十格，跟地表上的房子本来毫无关系。
     * 现在改成问「这个点到底在不在某个结构的体积里」。</p>
     *
     * <p>整个判断包在 try 里：世界生成时抛异常会把游戏搞崩，宁可放弃这一次生成。</p>
     *
     * @param y 判断用的竖直位置。必须给地表高度——给 0 的话就跑到地底下去跟矿井做邻居了
     */
    private static boolean insideOtherStructure(StructureWorldAccess world, int x, int y, int z) {
        try {
            BlockPos pos = new BlockPos(x, y, z);
            StructureAccessor accessor = world.toServerWorld().getStructureAccessor();
            for (Structure structure : world.getRegistryManager().get(RegistryKeys.STRUCTURE)) {
                StructureStart start = accessor.getStructureAt(pos, structure);
                if (start != null && start.hasChildren()) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 检查落点够不够空。只有地下版本会用到。
     *
     * @return 空间里的空气占比达到 {@value #REQUIRED_OPEN_RATIO} 以上才算合格
     */
    private static boolean hasRoom(StructureWorldAccess world, int minX, int minY, int minZ,
                                   int sizeX, int sizeY, int sizeZ) {
        int total = 0;
        int open = 0;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    cursor.set(minX + x, minY + y, minZ + z);
                    total++;
                    if (world.getBlockState(cursor).isAir()) {
                        open++;
                    }
                }
            }
        }
        return open >= total * REQUIRED_OPEN_RATIO;
    }

    /**
     * 在房子周围的地面上丢一两个台阶，像是从墙上崩下来的一块。
     *
     * <p>数量、位置、朝向全随机；放不下（那儿是水面、或者已经有东西）就跳过，不硬塞。</p>
     */
    private static void scatterSlabs(StructureWorldAccess world, Random random,
                                     BlockPos origin, LittleHomeConfig config) {
        if (random.nextFloat() >= config.slabChance()) {
            return;
        }
        int count = 1 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = SLAB_MIN_DISTANCE + random.nextDouble() * (SLAB_MAX_DISTANCE - SLAB_MIN_DISTANCE);
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * distance);
            BlockPos top = world.getTopPosition(Heightmap.Type.WORLD_SURFACE_WG, new BlockPos(x, origin.getY(), z));
            BlockPos below = top.down();
            // 只能平放在实心地面上：上面那格得是空的，下面那格得是实体方块、而且不能是水。
            // 少了这几道检查，半砖就会孤零零地浮在水面上或树叶上
            if (!world.getBlockState(top).isAir()) {
                continue;
            }
            if (!world.getBlockState(below).isSolidBlock(world, below)) {
                continue;
            }
            if (!world.getFluidState(below).isEmpty()) {
                continue;
            }
            BlockState slab = SCATTER_SLABS[random.nextInt(SCATTER_SLABS.length)].getDefaultState();
            world.setBlockState(top, slab, Block.NOTIFY_LISTENERS);
        }
    }

    /**
     * 判断一块方块是不是「搭房子时顺手框进来的地面」。
     *
     * <p>有两档尺度：</p>
     * <ul>
     *   <li><b>默认</b>：泥土、石头、沙子这类自然地表方块一律算，只放过石砖、箱子等建筑方块。</li>
     *   <li><b>保留地板模式</b>（{@code keep_floors}）：只认草方块。因为草图省事、玩家不会拿它铺地板，
     *       所以它基本一定是野地；而泥土、石头、灰化土完全可能是用户特意铺的地板，得原样留给他。</li>
     * </ul>
     */
    private static boolean isGroundCover(BlockState state, boolean keepFloors) {
        if (keepFloors) {
            return state.isOf(Blocks.GRASS_BLOCK);
        }
        return state.isOf(Blocks.GRASS_BLOCK)
                || state.isOf(Blocks.DIRT)
                || state.isOf(Blocks.COARSE_DIRT)
                || state.isOf(Blocks.ROOTED_DIRT)
                || state.isOf(Blocks.PODZOL)
                || state.isOf(Blocks.MYCELIUM)
                || state.isOf(Blocks.MOSS_BLOCK)
                || state.isOf(Blocks.MUD)
                || state.isOf(Blocks.STONE)
                || state.isOf(Blocks.DEEPSLATE)
                || state.isOf(Blocks.GRANITE)
                || state.isOf(Blocks.DIORITE)
                || state.isOf(Blocks.ANDESITE)
                || state.isOf(Blocks.TUFF)
                || state.isOf(Blocks.CALCITE)
                || state.isOf(Blocks.SAND)
                || state.isOf(Blocks.RED_SAND)
                || state.isOf(Blocks.GRAVEL)
                || state.isOf(Blocks.CLAY)
                || state.isOf(Blocks.SNOW_BLOCK)
                || state.isOf(Blocks.SNOW);
    }
}
