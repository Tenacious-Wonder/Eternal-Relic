package org.eternalrelic.worldgen;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;

import org.eternalrelic.EternalRelic;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * <h1>地表岩石 —— 把裸岩散在野外</h1>
 *
 * <p>分两类，做法完全不同：</p>
 *
 * <h2>小石头：程序直接铺几块石台阶</h2>
 * <p>不再用图纸，而是在落点附近挑几列，把地表那一格<b>直接换成一块石台阶</b>。
 * 台阶跟地面齐平，<b>在物理上就不可能悬空</b>——所以这类不需要挑地形，坡上、坡下、
 * 树根旁边都能长，自由度最高。</p>
 *
 * <h2>大石头：用图纸，但只在平缓地生成</h2>
 * <p>用 {@link LittleHomeTemplate} 放图纸（石块堆），条件卡得很死：</p>
 * <ul>
 *   <li><b>这一小片必须平</b>：石头占地范围内实心地面的高差不能超过
 *       {@value #MAX_GROUND_DIFF_OUTCROP} 格；</li>
 *   <li><b>绝不悬空</b>：石头坐在这一小片最高的地面上，逐列把正下方的空隙用
 *       <b>当地地表同款方块</b>垫实（草地垫草、沙地垫沙），所以底下永远是实的；</li>
 *   <li><b>不铺图纸自带的那层草皮</b>：那层整层跳过，免得在草地上留一块方形补丁。</li>
 * </ul>
 *
 * <h2>量地面量的是「实心地面」</h2>
 * <p>草、花、树叶都算「非空气方块」，若按最高非空气方块来量，森林里量到的就是<b>树冠顶</b>。
 * 所以这里量 {@link Heightmap.Type#OCEAN_FLOOR_WG}：最高的<b>实心</b>方块，自动跳过草木与树叶。</p>
 *
 * <h2>两类的比例与共同规矩</h2>
 * <p>比例由 {@link SurfaceRockConfig} 决定：多裸岩的地方偏大石头，其余地方偏小石头，
 * 但两边都会出现，不会「永远见不到某一种」。</p>
 * <p>两类都要过这几关：脚下不是水／冰；木头不超过 {@value #LOG_LIMIT} 格；附近没有我们的房子；
 * 不压在游戏自带的遗迹上。</p>
 *
 * <h2>材质会随地方变</h2>
 * <p>大约每二十块石头里有一块会被换成当地特色的石材：森林针叶林换成苔石，山地换成安山岩／凝灰岩，
 * 其余地方换圆石。小石头的台阶也按同一套地方特色挑。</p>
 */
public class SurfaceRockFeature extends Feature<SurfaceRockConfig> {

    /** 大石头占地范围内允许的高差（格）。卡得很死：只在不平不超过一格的地方才放大石头。 */
    private static final int MAX_GROUND_DIFF_OUTCROP = 1;

    /** 小石头铺台阶时，落点往四周最多散开几格。 */
    private static final int SLAB_SPREAD = 2;

    /** 找「附近有没有房子」时用什么半径。 */
    private static final int HOME_CLEARANCE = 12;

    /** 数木头时往上查几格。树干就长在这一段里。 */
    private static final int LOG_COUNT_UP = 12;

    /**
     * 落点范围内最多容许几格木头（原木／木头）。
     *
     * <p>超过这个数，说明这一小片基本就是树心，让开；没超过就照放——树根旁边、树冠底下
     * 有几块石头再自然不过，没必要因为看见一棵树就绕着走。</p>
     */
    private static final int LOG_LIMIT = 15;

    public SurfaceRockFeature(Codec<SurfaceRockConfig> codec) {
        super(codec);
    }

    @Override
    public boolean generate(FeatureContext<SurfaceRockConfig> context) {
        return tryPlace(context.getWorld(), context.getRandom(), context.getOrigin(), context.getConfig()) == null;
    }

    /**
     * 在指定位置试着放一处岩石。
     *
     * <p>把「挑地方」的每一步都做成会回话的形式，是为了让 {@code /relic rock} 能直接告诉玩家
     * 「这儿为什么放不了」——不然只能干瞪眼等它随机出现。</p>
     *
     * @param world  世界
     * @param random 随机源
     * @param origin 落点（只需要水平坐标；竖直方向会现量）
     * @param config 岩石参数
     * @return 放成了返回 {@code null}；没放成返回原因（给玩家看的一句话）
     */
    public static String tryPlace(StructureWorldAccess world, Random random, BlockPos origin,
                                  SurfaceRockConfig config) {
        MinecraftServer server = world.toServerWorld().getServer();
        if (server == null) {
            return "拿不到服务器";
        }

        RegistryEntry<Biome> biome = world.getBiome(origin);

        // 两类在任何地方都会出现，只是比例随地方变：
        // 多裸岩的地方偏向「大石头」，其余地方偏向「小石头」——但都不会永远见不到
        float outcropChance = isRugged(biome) ? config.outcropChanceRocky() : config.outcropChance();
        boolean wantOutcrop = !config.outcropVariants().isEmpty()
                && (config.slabCount() <= 0 || random.nextFloat() < outcropChance);

        if (!wantOutcrop) {
            return placeSlabs(world, random, origin, config, biome);
        }
        return placeOutcrop(world, random, origin, config, biome, server);
    }

    /**
     * 小石头：在落点附近挑几列，把地表那一格直接换成石台阶。
     *
     * <p>台阶跟地面齐平，所以<b>不可能悬空</b>，也就没必要挑地形——这是它比大石头自由的原因。</p>
     *
     * @return 放成了返回 {@code null}；一块都没铺成则返回原因
     */
    private static String placeSlabs(StructureWorldAccess world, Random random, BlockPos origin,
                                     SurfaceRockConfig config, RegistryEntry<Biome> biome) {
        Palette palette = paletteFor(biome);
        int wanted = 1 + random.nextInt(Math.max(1, config.slabCount()));
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int placed = 0;

        for (int i = 0; i < wanted; i++) {
            int x = origin.getX() + random.nextInt(SLAB_SPREAD * 2 + 1) - SLAB_SPREAD;
            int z = origin.getZ() + random.nextInt(SLAB_SPREAD * 2 + 1) - SLAB_SPREAD;
            int top = world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, x, z);
            BlockPos ground = new BlockPos(x, top - 1, z);
            if (!world.isValidForSetBlock(ground)) {
                continue;
            }
            BlockState below = world.getBlockState(ground);
            if (below.isAir() || !below.getFluidState().isEmpty() || isFrozen(below)) {
                continue;
            }
            // 台阶直接顶掉地表那一格：跟地面齐平，底下还是原来的土，怎么都不会悬空
            world.setBlockState(ground, palette.slab().get(random.nextInt(palette.slab().size())),
                    Block.NOTIFY_LISTENERS);
            // 顺手把压在上面的草和花清掉，免得台阶上还长着一丛草
            BlockPos above = ground.up();
            cursor.set(above);
            if (world.getBlockState(cursor).getBlock() instanceof PlantBlock) {
                world.setBlockState(above, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
            placed++;
        }

        return placed > 0 ? null : "这一小块地上没找到能铺台阶的地方";
    }

    /**
     * 大石头：放图纸，但只在平缓的地方，而且保证底下垫实、绝不悬空。
     *
     * @return 放成了返回 {@code null}；没放成返回原因
     */
    private static String placeOutcrop(StructureWorldAccess world, Random random, BlockPos origin,
                                       SurfaceRockConfig config, RegistryEntry<Biome> biome,
                                       MinecraftServer server) {
        String variant = config.outcropVariants().get(random.nextInt(config.outcropVariants().size()));
        Optional<LittleHomeTemplate> loaded = LittleHomeTemplate.load(server, EternalRelic.id(variant));
        if (loaded.isEmpty()) {
            EternalRelic.LOGGER.warn("地表岩石找不到结构文件：{}", variant);
            return "找不到结构文件 " + variant;
        }
        LittleHomeTemplate template = loaded.get();

        int width = Math.max(1, template.size().getX());
        int depth = Math.max(1, template.size().getZ());
        int x0 = origin.getX() - width / 2;
        int z0 = origin.getZ() - depth / 2;

        int lowestTop = Integer.MAX_VALUE;
        int highestTop = Integer.MIN_VALUE;
        int[][] grounds = new int[width][depth];
        BlockState[][] surfaceBlocks = new BlockState[width][depth];
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int ix = 0; ix < width; ix++) {
            for (int iz = 0; iz < depth; iz++) {
                int x = x0 + ix;
                int z = z0 + iz;
                // 量「实心地面」：草木与树叶不算
                int top = world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, x, z);
                grounds[ix][iz] = top;
                lowestTop = Math.min(lowestTop, top);
                highestTop = Math.max(highestTop, top);
                cursor.set(x, top - 1, z);
                BlockState ground = world.getBlockState(cursor);
                surfaceBlocks[ix][iz] = ground;
                if (ground.isAir() || !ground.getFluidState().isEmpty() || isFrozen(ground)) {
                    return "脚下是水、冰或空的（" + ground.getBlock() + "）";
                }
            }
        }

        // 只在平缓的地方放大石头：不平就换地方，不将就
        if (highestTop - lowestTop > MAX_GROUND_DIFF_OUTCROP) {
            return "地面不够平（这一小片高差 " + (highestTop - lowestTop)
                    + " 格，上限 " + MAX_GROUND_DIFF_OUTCROP + "）";
        }

        String taken = blockingReason(world, x0, lowestTop, z0, width, depth, template.size().getY());
        if (taken != null) {
            return taken;
        }

        // 坐在这一小片「最高」的地面上：宁可底下垫起来，也不让石头陷进土里
        int baseY = highestTop - 1;
        Palette palette = paletteFor(biome);

        // 图纸自带的那层草皮／泥土不铺——那是存档时脚下的地，铺下去会在草地上留一块
        // 方方正正、材质还未必对的补丁。石头本体照放，悬空的空缺随后单独补
        int groundLayer = template.lowestY();
        int[][] lowestPlaced = new int[width][depth];
        for (int ix = 0; ix < width; ix++) {
            Arrays.fill(lowestPlaced[ix], Integer.MAX_VALUE);
        }

        for (LittleHomeTemplate.Piece piece : template.pieces()) {
            if (piece.offset().getY() == groundLayer && isLooseGround(piece.state())) {
                continue;
            }
            BlockPos target = new BlockPos(
                    x0 + piece.offset().getX(),
                    baseY + piece.offset().getY(),
                    z0 + piece.offset().getZ());
            if (!world.isValidForSetBlock(target)) {
                continue;
            }
            world.setBlockState(target, adapt(piece.state(), palette, random, config.replaceChance()),
                    Block.NOTIFY_LISTENERS);

            int ix = target.getX() - x0;
            int iz = target.getZ() - z0;
            if (ix >= 0 && ix < width && iz >= 0 && iz < depth) {
                lowestPlaced[ix][iz] = Math.min(lowestPlaced[ix][iz], target.getY());
            }
        }

        // 逐列把石头正下方的空隙填实，用当地地表同款方块（草地垫草、沙地垫沙）。
        // 这一步是「绝不悬空」的最后保证
        for (int ix = 0; ix < width; ix++) {
            for (int iz = 0; iz < depth; iz++) {
                if (lowestPlaced[ix][iz] == Integer.MAX_VALUE) {
                    continue;
                }
                for (int y = lowestPlaced[ix][iz] - 1; y >= grounds[ix][iz]; y--) {
                    BlockPos under = new BlockPos(x0 + ix, y, z0 + iz);
                    if (world.isValidForSetBlock(under)) {
                        world.setBlockState(under, surfaceBlocks[ix][iz], Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }
        return null;
    }

    /**
     * 这块地儿是不是已经有主了。
     *
     * @param height 图纸的高度，用来决定往上查几格
     * @return 有主就返回原因，空着返回 {@code null}
     */
    private static String blockingReason(StructureWorldAccess world, int x0, int top, int z0,
                                         int width, int depth, int height) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        // 只数木头（原木／木头），树叶不算：树冠底下的石头很自然。
        // 数出来一大堆才说明这儿是树心，让开
        int logs = 0;
        for (int x = x0; x < x0 + width; x++) {
            for (int z = z0; z < z0 + depth; z++) {
                for (int y = top - 1; y <= top + Math.max(2, height) + LOG_COUNT_UP; y++) {
                    cursor.set(x, y, z);
                    if (world.getBlockState(cursor).isIn(BlockTags.LOGS)) {
                        logs++;
                    }
                }
            }
        }
        if (logs > LOG_LIMIT) {
            return "这一片木头太多（" + logs + " 格，上限 " + LOG_LIMIT + "）";
        }

        int centerX = x0 + width / 2;
        int centerZ = z0 + depth / 2;
        // 我们自己的房子：认石砖
        if (LittleHomeClusterFeature.hasHomesNearby(world, new BlockPos(centerX, top, centerZ), HOME_CLEARANCE)) {
            return "附近有我们的房子";
        }
        // 游戏自带的遗迹：村庄、前哨站之类
        if (LittleHomeFeature.insideOtherStructure(world, centerX, top, centerZ)) {
            return "落在别的建筑里（村庄之类）";
        }
        return null;
    }

    /**
     * 是不是水或冰——那两种地方不放石头。
     *
     * @param state 地面方块
     * @return 是冰或含水就返回 {@code true}
     */
    private static boolean isFrozen(BlockState state) {
        return state.isOf(Blocks.ICE) || state.isOf(Blocks.PACKED_ICE) || state.isOf(Blocks.BLUE_ICE);
    }

    /**
     * 这是不是「软地面」——图纸自带的那层地表。
     *
     * <p>岩石图纸是连着脚下的地一起存的，最底下那层通常是草皮和泥土。那层不该原样铺到世界里：
     * 它会在草地上留一块方方正正的补丁，落在沙地、灰化土上材质还不对。所以这层整层跳过，
     * 只在石头会悬空时才用<b>当地地表同款方块</b>补回去。</p>
     *
     * @param state 图纸上的方块
     * @return 属于软地面就返回 {@code true}
     */
    private static boolean isLooseGround(BlockState state) {
        return state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.DIRT)
                || state.isOf(Blocks.COARSE_DIRT) || state.isOf(Blocks.ROOTED_DIRT)
                || state.isOf(Blocks.PODZOL) || state.isOf(Blocks.MYCELIUM)
                || state.isOf(Blocks.MUD) || state.isOf(Blocks.SAND)
                || state.isOf(Blocks.RED_SAND) || state.isOf(Blocks.GRAVEL);
    }

    /**
     * 这一带该不该放大石头。
     *
     * <p>山地、风袭丘陵、针叶林本来就到处是裸岩；其余地方更适合小石头。</p>
     *
     * @param biome 落点所在的生物群系
     * @return 属于多岩石的地方就返回 {@code true}
     */
    private static boolean isRugged(RegistryEntry<Biome> biome) {
        String id = biome.getKey().map(key -> key.getValue().getPath()).orElse("");
        return id.contains("stony") || id.contains("windswept") || id.contains("peak")
                || id.contains("mountain") || id.contains("taiga") || id.contains("grove")
                || id.contains("slope") || id.contains("jagged") || id.contains("meadow");
    }

    /**
     * 按生物群系挑一套替换用的石材。
     *
     * @param biome 落点所在的生物群系
     * @return 这一带换材质时用的两档方块（整方块 / 半砖）
     */
    private static Palette paletteFor(RegistryEntry<Biome> biome) {
        String id = biome.getKey().map(key -> key.getValue().getPath()).orElse("");

        if (id.contains("forest") || id.contains("taiga") || id.contains("jungle")
                || id.contains("swamp") || id.contains("mangrove") || id.contains("grove")) {
            return Palette.of(
                    List.of(Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE),
                    List.of(Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.COBBLESTONE_SLAB));
        }
        if (id.contains("stony") || id.contains("windswept") || id.contains("peak")
                || id.contains("mountain") || id.contains("slope") || id.contains("meadow")) {
            return Palette.of(
                    List.of(Blocks.ANDESITE, Blocks.COBBLESTONE, Blocks.TUFF, Blocks.STONE),
                    List.of(Blocks.ANDESITE_SLAB, Blocks.COBBLESTONE_SLAB, Blocks.STONE_SLAB));
        }
        if (id.contains("savanna") || id.contains("badlands") || id.contains("desert")) {
            return Palette.of(
                    List.of(Blocks.GRANITE, Blocks.TERRACOTTA, Blocks.COBBLESTONE),
                    List.of(Blocks.COBBLESTONE_SLAB, Blocks.STONE_SLAB));
        }
        return Palette.of(
                List.of(Blocks.COBBLESTONE, Blocks.STONE, Blocks.COBBLESTONE),
                List.of(Blocks.COBBLESTONE_SLAB, Blocks.STONE_SLAB));
    }

    /**
     * 决定这一块最终放什么。
     *
     * <p>只有「石质的整方块」和「石质半砖」会被换掉，而且大概二十分之一才换一次；
     * 草皮、泥土、台阶一律照原样——台阶的朝向和形状很讲究，换材质容易换歪。</p>
     *
     * @param state   图纸上本来是什么
     * @param palette 这一带的替换候选
     * @param chance  每一块被换掉的概率
     * @return 实际放下去的方块
     */
    private static BlockState adapt(BlockState state, Palette palette, Random random, float chance) {
        if (chance <= 0.0F || random.nextFloat() >= chance) {
            return state;
        }

        if (state.isOf(Blocks.STONE) || state.isOf(Blocks.COBBLESTONE) || state.isOf(Blocks.ANDESITE)
                || state.isOf(Blocks.GRANITE) || state.isOf(Blocks.DIORITE) || state.isOf(Blocks.TUFF)
                || state.isOf(Blocks.STONE_BRICKS) || state.isOf(Blocks.CRACKED_STONE_BRICKS)
                || state.isOf(Blocks.MOSSY_STONE_BRICKS)) {
            return palette.full().get(random.nextInt(palette.full().size()));
        }

        if (state.isOf(Blocks.STONE_SLAB) || state.isOf(Blocks.COBBLESTONE_SLAB)
                || state.isOf(Blocks.ANDESITE_SLAB)) {
            // 半砖要保住「上半还是下半」，否则会翻过来
            return palette.slab().get(random.nextInt(palette.slab().size()))
                    .with(SlabBlock.TYPE, state.get(SlabBlock.TYPE));
        }

        return state;
    }

    /**
     * 一带的替换石材。
     *
     * @param full 整方块候选
     * @param slab 半砖候选
     */
    private record Palette(List<BlockState> full, List<BlockState> slab) {

        /**
         * 用「方块」列出候选，内部转成方块状态。
         *
         * <p>做成静态工厂而不是另一个构造函数：记录类型不允许再写一个参数类型相同的构造函数。</p>
         *
         * @param full 整方块候选
         * @param slab 半砖候选
         * @return 这一带的替换候选
         */
        static Palette of(List<Block> full, List<Block> slab) {
            return new Palette(full.stream().map(Block::getDefaultState).toList(),
                    slab.stream().map(Block::getDefaultState).toList());
        }
    }
}
