package org.eternalrelic.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PlantBlock;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.Biome;

/**
 * <h1>给聚落铺小路</h1>
 *
 * <p>房子摆完之后，把大多数房子用小路串起来。做法是：每栋房子都去找<b>离它最近的、已经接上路的</b>
 * 那一栋，在两者之间扭一条路——最后连出来是一张树状的网，而不是每栋都往中心拉一根辐条。</p>
 *
 * <h2>为什么看起来是断断续续的</h2>
 * <p>三个原因叠在一起：</p>
 * <ul>
 *   <li>约五分之一的房子<b>干脆不接</b>——孤零零杵在路边，正是「凌乱」的来源；</li>
 *   <li>路面本身是扭的：沿路随机左右摆，越靠中间摆得越开，到了房子跟前又收回来；</li>
 *   <li>遇到水面、悬崖、或者房子占着的地方就<b>直接停下</b>，不硬接过去。</li>
 * </ul>
 *
 * <h2>材质</h2>
 * <p>主角是砂土、土径，圆石偶尔露脸，再<b>掺进草方块</b>——路年久失修、被草啃回去一点。
 * 另外按房子所在的生物群系加料（沙漠沙滩换沙子与土坯、丛林沼泽针叶林加苔石、山地加石头……）。
 * 一律用整方块，<b>不铺台阶</b>：半砖拼出来的路会露出半高断面，看着像没铺完。
 * 整体偏素，不抢房子的戏。</p>
 */
public final class LittleHomePaths {

    /** 路固定几格宽。 */
    private static final int WIDTH = 2;

    /** 有多大概率「这栋房子不接路」。 */
    private static final float SKIP_CHANCE = 0.22F;


    /** 房子占地的半宽。房子是 17×17，所以这里连它周围一格一起避开。 */
    private static final int HOUSE_HALF_SIZE = 9;

    /** 每一格路线上采几个点——点越密，路越平滑。 */
    private static final double SAMPLES_PER_BLOCK = 1.6D;

    /** 路面可以覆盖的「天然地面」。人工方块一律不动，免得把房子啃掉。 */
    private static final Set<Block> PAVABLE = Set.of(
            Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT,
            Blocks.PODZOL, Blocks.MYCELIUM, Blocks.MOSS_BLOCK,
            Blocks.SAND, Blocks.RED_SAND, Blocks.SANDSTONE, Blocks.RED_SANDSTONE,
            Blocks.GRAVEL, Blocks.CLAY, Blocks.MUD,
            Blocks.STONE, Blocks.ANDESITE, Blocks.GRANITE, Blocks.DIORITE,
            Blocks.TUFF, Blocks.CALCITE, Blocks.TERRACOTTA,
            Blocks.SNOW_BLOCK, Blocks.DIRT_PATH);

    private LittleHomePaths() {
    }

    /**
     * 把这几栋房子用小路口连起来。
     *
     * <p>只有一栋房子时什么都不做——没有邻居可连。</p>
     *
     * @param world  世界
     * @param random 随机源
     * @param houses 已经<b>真的盖好了</b>的房子的落点（按摆放顺序）
     */
    public static void connect(StructureWorldAccess world, Random random, List<BlockPos> houses) {
        if (houses.size() < 2) {
            return;
        }

        Palette palette = paletteFor(world.getBiome(houses.get(0)));

        List<BlockPos> linked = new ArrayList<>();
        linked.add(houses.get(0));

        for (int i = 1; i < houses.size(); i++) {
            BlockPos target = houses.get(i);
            BlockPos nearest = nearest(target, linked);
            // 抽中了就这栋不接路——「大多数连起来」而不是「全部连起来」
            if (random.nextFloat() >= SKIP_CHANCE) {
                carve(world, random, nearest, target, houses, palette);
            }
            linked.add(target);
        }
    }

    /**
     * 在已经接上路的房子里找离目标最近的那一栋。
     *
     * @param from       目标房子
     * @param candidates 已经接上路的房子
     * @return 最近的那一栋
     */
    private static BlockPos nearest(BlockPos from, List<BlockPos> candidates) {
        BlockPos best = candidates.get(0);
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : candidates) {
            double dx = candidate.getX() - from.getX();
            double dz = candidate.getZ() - from.getZ();
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * 在两栋房子之间扭出一条路。
     *
     * @param from    起点（已接上路的房子）
     * @param to      终点
     * @param houses  本聚落全部房子，用来避开房子本体
     * @param palette 这一带的路面调色板
     */
    private static void carve(StructureWorldAccess world, Random random,
                              BlockPos from, BlockPos to, List<BlockPos> houses, Palette palette) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0D) {
            return;
        }

        // 垂直于路线的方向：随机左右摆动就是沿这个方向推
        double nx = -dz / length;
        double nz = dx / length;

        int steps = Math.max(2, (int) Math.ceil(length * SAMPLES_PER_BLOCK));
        double wobble = 0.0D;

        for (int step = 0; step <= steps; step++) {
            double t = (double) step / steps;

            // 随机游走：每一步都偏一点，同时慢慢往回收，免得摆出去回不来
            wobble = wobble * 0.82D + (random.nextDouble() - 0.5D) * 2.4D;
            // 两头收窄：贴着房子的时候不扭，免得路钻进别人家里
            double offset = wobble * Math.sin(Math.PI * t);

            int x = (int) Math.round(from.getX() + dx * t + nx * offset);
            int z = (int) Math.round(from.getZ() + dz * t + nz * offset);

            stamp(world, random, x, z, houses, palette);
        }
    }

    /**
     * 在以某一点为中心铺一块方形的路面（{@value #WIDTH}×{@value #WIDTH} 格）。
     *
     * <p>用方头而不是圆头，是因为方块世界里方头看起来更像人踩出来的路。</p>
     */
    private static void stamp(StructureWorldAccess world, Random random, int centerX, int centerZ,
                              List<BlockPos> houses, Palette palette) {
        int half = WIDTH / 2;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                pave(world, random, centerX + dx, centerZ + dz, houses, palette);
            }
        }
    }

    /**
     * 把一列地面铺成路。
     *
     * <p>三种情况直接放手：落在房子占地里、地面不是天然方块、头顶被东西压着
     * （说明这儿是室内或屋檐下）。</p>
     */
    private static void pave(StructureWorldAccess world, Random random, int x, int z,
                             List<BlockPos> houses, Palette palette) {
        if (insideHouse(x, z, houses)) {
            return;
        }

        int top = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z);
        BlockPos surface = new BlockPos(x, top - 1, z);
        if (!world.isValidForSetBlock(surface)) {
            return;
        }

        BlockState ground = world.getBlockState(surface);
        if (!PAVABLE.contains(ground.getBlock())) {
            return;
        }

        // 头顶得是天空：是实心方块说明这儿在屋顶下或洞里，不该有路
        BlockState above = world.getBlockState(surface.up());
        if (!isOpenSky(above)) {
            return;
        }

        world.setBlockState(surface, palette.pick(random), Block.NOTIFY_LISTENERS);

        BlockState sky = world.getBlockState(surface.up());
        if (isPlant(sky)) {
            world.setBlockState(surface.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
    }

    /**
     * 这个位置是不是被某栋房子占着。
     *
     * @return 落在任意一栋房子的占地（含外扩一格）里就返回 {@code true}
     */
    private static boolean insideHouse(int x, int z, List<BlockPos> houses) {
        for (BlockPos house : houses) {
            if (Math.abs(x - house.getX()) <= HOUSE_HALF_SIZE && Math.abs(z - house.getZ()) <= HOUSE_HALF_SIZE) {
                return true;
            }
        }
        return false;
    }

    /**
     * 头顶是空的（或者只有一层雪）——那就是露天，可以铺路。
     *
     * @return 露天就返回 {@code true}
     */
    private static boolean isOpenSky(BlockState state) {
        return state.isAir() || state.isOf(Blocks.SNOW) || isPlant(state);
    }

    /**
     * 这是不是草、花之类一踩就没的东西。
     *
     * @return 是植物就返回 {@code true}
     */
    private static boolean isPlant(BlockState state) {
        return state.getBlock() instanceof PlantBlock;
    }

    /**
     * 按生物群系配一套路面材质。
     *
     * <p>一律用整方块，<b>不铺台阶</b>——半砖拼出来的路在方块世界里会露出半高断面，
     * 看着像没铺完，还不如干脆不要。</p>
     *
     * <p>写法是「排队」：同一种方块放几份就等于抽中它的概率大几倍。
     * 砂土和土径是主角，圆石只是偶尔露个脸，另外<b>掺进一些草方块</b>——
     * 路年久失修、被草啃回去一点，比一条干干净净的路更耐看。
     * 沙漠／沙滩那几种地方不掺草（那儿长草才奇怪），改用沙子。</p>
     *
     * @param biome 聚落所在的生物群系
     * @return 这一带的路面材质
     */
    private static Palette paletteFor(RegistryEntry<Biome> biome) {
        String id = biome.getKey().map(key -> key.getValue().getPath()).orElse("");
        boolean sandy = id.contains("desert") || id.contains("badlands") || id.contains("beach");

        List<BlockState> blocks = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            blocks.add(Blocks.COARSE_DIRT.getDefaultState());
        }
        for (int i = 0; i < 3; i++) {
            blocks.add(Blocks.DIRT_PATH.getDefaultState());
        }
        blocks.add(Blocks.COBBLESTONE.getDefaultState());

        if (sandy) {
            blocks.add(Blocks.SAND.getDefaultState());
            blocks.add(Blocks.SAND.getDefaultState());
            blocks.add(Blocks.PACKED_MUD.getDefaultState());
        } else {
            // 草方块：让路看起来被野草啃过
            blocks.add(Blocks.GRASS_BLOCK.getDefaultState());
            blocks.add(Blocks.GRASS_BLOCK.getDefaultState());
        }

        if (id.contains("desert") || id.contains("badlands")) {
            blocks.add(Blocks.SAND.getDefaultState());
            blocks.add(Blocks.PACKED_MUD.getDefaultState());
        }
        if (id.contains("jungle") || id.contains("swamp") || id.contains("mangrove")) {
            blocks.add(Blocks.MOSSY_COBBLESTONE.getDefaultState());
            blocks.add(Blocks.MUD.getDefaultState());
            blocks.add(Blocks.MUD.getDefaultState());
        }
        if (id.contains("taiga") || id.contains("forest") || id.contains("grove")) {
            blocks.add(Blocks.MOSSY_COBBLESTONE.getDefaultState());
            blocks.add(Blocks.PODZOL.getDefaultState());
            blocks.add(Blocks.PODZOL.getDefaultState());
            blocks.add(Blocks.GRASS_BLOCK.getDefaultState());
        }
        if (id.contains("snow") || id.contains("frozen") || id.contains("ice")) {
            blocks.add(Blocks.GRAVEL.getDefaultState());
            blocks.add(Blocks.GRAVEL.getDefaultState());
            blocks.add(Blocks.STONE.getDefaultState());
        }
        if (id.contains("stony") || id.contains("windswept") || id.contains("peak")
                || id.contains("mountain") || id.contains("meadow")) {
            blocks.add(Blocks.STONE.getDefaultState());
            blocks.add(Blocks.ANDESITE.getDefaultState());
            blocks.add(Blocks.GRAVEL.getDefaultState());
        }
        if (id.contains("savanna")) {
            blocks.add(Blocks.COARSE_DIRT.getDefaultState());
        }

        return new Palette(blocks);
    }

    /**
     * 一带的路面材质。
     *
     * @param blocks 候选方块。同一种放几份，就等于抽中它的概率大几倍
     */
    private record Palette(List<BlockState> blocks) {

        /**
         * 随机挑一块路面。
         *
         * @param random 随机源
         * @return 这一格铺什么
         */
        BlockState pick(Random random) {
            return blocks.get(random.nextInt(blocks.size()));
        }
    }
}
