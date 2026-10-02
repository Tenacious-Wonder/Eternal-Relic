package org.eternalrelic.command;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import org.eternalrelic.EternalRelic;
import org.eternalrelic.worldgen.LittleHomeClusterConfig;
import org.eternalrelic.worldgen.LittleHomeClusterFeature;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.feature.ConfiguredFeature;

/**
 * <h1>调试与探路用的指令</h1>
 *
 * <p>之所以需要这些指令，是因为遗落小屋是<b>地物</b>而不是<b>结构</b>——
 * 游戏自带的 {@code /locate structure} 只能找结构，找不到地物。所以这里自己算、自己数。</p>
 *
 * <h2>为什么会「自己数」</h2>
 * <p>「这一处该有几栋」是可以掐指一算的（见 {@link LittleHomeClusterFeature#plan}），
 * 但<b>算出来的栋数不等于地上真有那么多</b>：那一处可能落在水里，也可能地形太陡、
 * 或者几栋房子的地皮被别的东西占了。所以 {@code find} 不是算完就把你扔过去，
 * 而是先把那片地形生成出来、<b>挨个落点数一遍砖石</b>，数够了才带你过去。</p>
 *
 * <h2>指令</h2>
 * <ul>
 *   <li>{@code /relic cluster find <几栋> [半径] [跳过前几处]} —— 找最近的一处
 *       <b>实地真盖成这么多栋</b>的聚落，并把你传送过去。不填半径默认 {@value #DEFAULT_RADIUS} 格。</li>
 *   <li>{@code /relic cluster here} —— 数一数你脚下这一片实地有几栋（想亲眼核对时用）。</li>
 * </ul>
 */
public final class RelicCommands {

    /** 不填半径时默认推算多远。 */
    private static final int DEFAULT_RADIUS = 4000;

    /** 半径最多填多大。 */
    private static final int MAX_RADIUS = 8000;

    /**
     * 最多试几处。
     *
     * <p>每一处都要先把地形生成出来才数得准，所以要有个上限——试太多会让人干等。</p>
     */
    private static final int MAX_ATTEMPTS = 10;

    /** 等地形生成最多等多少游戏刻（20 刻 = 1 秒）。 */
    private static final int WAIT_TICKS = 240;

    /** 房子占地的半宽（格），用来决定要等哪几片区块生成好。 */
    private static final int HOUSE_HALF_SIZE = 10;

    /** {@code /relic cluster here} 往四周数多大一圈。 */
    private static final int NEARBY_RADIUS = 64;

    private RelicCommands() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，把指令挂进游戏。
     */
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("relic")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.literal("cluster")
                                .then(CommandManager.literal("here")
                                        .executes(context -> here(context.getSource())))
                                .then(CommandManager.literal("find")
                                        .then(CommandManager.argument("homes",
                                                        IntegerArgumentType.integer(1, 13))
                                                .executes(context -> find(context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "homes"),
                                                        DEFAULT_RADIUS, 0))
                                                .then(CommandManager.argument("radius",
                                                                IntegerArgumentType.integer(32, MAX_RADIUS))
                                                        .executes(context -> find(context.getSource(),
                                                                IntegerArgumentType.getInteger(context, "homes"),
                                                                IntegerArgumentType.getInteger(context, "radius"), 0))
                                                        .then(CommandManager.argument("skip",
                                                                        IntegerArgumentType.integer(0, 64))
                                                                .executes(context -> find(context.getSource(),
                                                                        IntegerArgumentType.getInteger(context, "homes"),
                                                                        IntegerArgumentType.getInteger(context, "radius"),
                                                                        IntegerArgumentType.getInteger(context, "skip"))))))))));
    }

    /**
     * 找一处实地真盖成这么多栋的聚落，并把人送过去。
     *
     * @param source 指令来源
     * @param homes  要几栋
     * @param radius 最远推算多远
     * @param skip   跳过最近的这么多处（用来看下一处）
     * @return 指令执行结果（1 表示已经开始找，0 表示压根没算出来）
     */
    private static int find(ServerCommandSource source, int homes, int radius, int skip) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("这条指令得由玩家来执行——总得有个能传送的人。"));
            return 0;
        }

        ServerWorld world = source.getWorld();
        LittleHomeClusterConfig config = clusterConfig(world);
        if (config == null) {
            source.sendError(Text.literal("读不到聚落参数（eternal_relic:little_home_cluster），没法推算。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("正在找实地至少有 " + homes + " 栋的聚落，最远 " + radius + " 格……"), false);
        return attempt(source, world, player, config, homes, radius, skip, 0);
    }

    /**
     * 试下一处候选。
     *
     * @param attempts 这是第几次尝试（每一处都要生成地形、实地数一遍，数不够就换下一处）
     * @return 指令执行结果
     */
    private static int attempt(ServerCommandSource source, ServerWorld world, ServerPlayerEntity player,
                               LittleHomeClusterConfig config, int homes, int radius, int skip, int attempts) {
        Target candidate = search(world, player, config, radius, homes, skip);
        if (candidate == null) {
            source.sendError(Text.literal(skip == 0
                    ? "往外推算了 " + radius + " 格也没算出符合条件的聚落，把半径填大一点或把栋数调低一档试试。"
                    : "附近算出来的候选都试过了，没有实地达到 " + homes + " 栋的。"));
            return 0;
        }
        settle(source, world, player, config, homes, radius, skip, attempts, candidate, 0);
        return 1;
    }

    /**
     * 把候选那一处的地形生成出来、数一数实地有几栋，够数就传送。
     *
     * <p>两件事不能省：一是<b>等地形生成好</b>（没生成的地方读出来是「世界底部」，
     * 人会掉进地底）；二是<b>实地数</b>（算出来的栋数只是上限，地形太陡的地方盖不下去）。</p>
     *
     * @param waited 已经等了多少游戏刻
     */
    private static void settle(ServerCommandSource source, ServerWorld world, ServerPlayerEntity player,
                               LittleHomeClusterConfig config, int homes, int radius, int skip,
                               int attempts, Target candidate, int waited) {
        if (!isReady(world, candidate)) {
            if (waited >= WAIT_TICKS) {
                source.sendError(Text.literal("那一处的地形迟迟生成不出来，过一会儿再试一次。"));
                return;
            }
            world.getServer().execute(() -> settle(source, world, player, config, homes, radius, skip,
                    attempts, candidate, waited + 1));
            return;
        }

        if (isWet(world.getBiome(candidate.center()))) {
            next(source, world, player, config, homes, radius, skip, attempts, candidate, "那一处落在水里／河边");
            return;
        }

        int actual = countPlaced(world, candidate);
        if (actual >= homes) {
            BlockPos center = candidate.center();
            int x = center.getX();
            int z = center.getZ();
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
            player.teleport(world, x + 0.5, y, z + 0.5, player.getYaw(), player.getPitch());
            source.sendFeedback(() -> Text.literal("到了！就在 " + candidate.distance() + " 格外（"
                    + x + ", " + y + ", " + z + "）。这一处实地认到 " + actual + " 栋"
                    + "（当初算出来是 " + candidate.planned() + " 栋）。"), false);
            return;
        }

        next(source, world, player, config, homes, radius, skip, attempts, candidate,
                "实地只有 " + actual + " 栋（算出来 " + candidate.planned() + " 栋）");
    }

    /**
     * 这一处不够数，换下一处。
     *
     * @param reason 为什么放弃这一处（会报给玩家看）
     */
    private static void next(ServerCommandSource source, ServerWorld world, ServerPlayerEntity player,
                             LittleHomeClusterConfig config, int homes, int radius, int skip,
                             int attempts, Target candidate, String reason) {
        if (attempts + 1 >= MAX_ATTEMPTS) {
            source.sendError(Text.literal("连着试了 " + (attempts + 1) + " 处都不够 "
                    + homes + " 栋（最后停在 " + candidate.center().getX() + ", " + candidate.center().getZ()
                    + "：" + reason + "）。换个方向、把半径填大一点，或者把栋数调低一档再试。"));
            return;
        }
        source.sendFeedback(() -> Text.literal(reason + "，换下一处继续找……"), false);
        attempt(source, world, player, config, homes, radius, skip + 1, attempts + 1);
    }

    /**
     * 数一数玩家脚下这一片实地有几栋房子。
     *
     * <p>数法是拿「计划」当尺子：把附近每个区块算出来的落点收集起来，逐个去看那儿有没有砖石。
     * 比满地乱扫准得多——落点就是房子本该在的地方。</p>
     *
     * @return 指令执行结果
     */
    private static int here(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("这条指令得由玩家来执行。"));
            return 0;
        }
        ServerWorld world = source.getWorld();
        LittleHomeClusterConfig config = clusterConfig(world);
        if (config == null) {
            source.sendError(Text.literal("读不到聚落参数，没法推算。"));
            return 0;
        }

        BlockPos origin = player.getBlockPos();
        long seed = world.getSeed();
        int centerChunkX = ChunkSectionPos.getSectionCoord(origin.getX());
        int centerChunkZ = ChunkSectionPos.getSectionCoord(origin.getZ());
        int reach = NEARBY_RADIUS / 16 + 5;

        int built = 0;
        int planned = 0;
        int clusters = 0;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                LittleHomeClusterFeature.Settlement plan = LittleHomeClusterFeature.plan(
                        seed, config, chunkX, chunkZ);
                if (plan.isEmpty()) {
                    continue;
                }
                boolean counted = false;
                for (BlockPos spot : plan.spots()) {
                    if (Math.abs(spot.getX() - origin.getX()) > NEARBY_RADIUS
                            || Math.abs(spot.getZ() - origin.getZ()) > NEARBY_RADIUS) {
                        continue;
                    }
                    if (!counted) {
                        counted = true;
                        clusters++;
                    }
                    planned++;
                    if (hasBricks(world, spot)) {
                        built++;
                    }
                }
            }
        }

        if (planned == 0) {
            source.sendFeedback(() -> Text.literal("你周围 " + NEARBY_RADIUS
                    + " 格内没有落在聚落里的房子（这一带本来就没安排）。"), false);
            return 1;
        }
        int plannedTotal = planned;
        int builtTotal = built;
        int clusterTotal = clusters;
        source.sendFeedback(() -> Text.literal("你周围 " + NEARBY_RADIUS + " 格内：安排 " + clusterTotal
                + " 处聚落、共 " + plannedTotal + " 个落点，实地盖成 " + builtTotal + " 栋。"), false);
        return 1;
    }

    /**
     * 把要用的那几片区块都「点」一遍，看它们准备好了没有。
     *
     * <p>点一下就等于请游戏开始生成它们。</p>
     *
     * @return 该有的区块都到齐了就返回 {@code true}
     */
    private static boolean isReady(ServerWorld world, Target candidate) {
        boolean ready = true;
        for (int cx = ChunkSectionPos.getSectionCoord(candidate.minX());
             cx <= ChunkSectionPos.getSectionCoord(candidate.maxX()); cx++) {
            for (int cz = ChunkSectionPos.getSectionCoord(candidate.minZ());
                 cz <= ChunkSectionPos.getSectionCoord(candidate.maxZ()); cz++) {
                if (!isChunkReady(world, cx, cz)) {
                    ready = false;
                }
            }
        }
        return ready;
    }

    /**
     * 单看一片区块好了没有。顺手请求生成它。
     *
     * @return 已经是完整区块了就返回 {@code true}
     */
    private static boolean isChunkReady(ServerWorld world, int chunkX, int chunkZ) {
        try {
            Chunk chunk = world.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
            return chunk != null && chunk.getStatus().isAtLeast(ChunkStatus.FULL);
        } catch (RuntimeException e) {
            // 还没准备好时游戏内部可能直接抛异常，那就算「没好」
            return false;
        }
    }

    /**
     * 这一处的几个落点，实地认到了几个。
     *
     * @return 认到房子的落点数
     */
    private static int countPlaced(ServerWorld world, Target candidate) {
        int count = 0;
        for (BlockPos spot : candidate.spots()) {
            if (hasBricks(world, spot)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 这个落点脚下有没有石砖。
     *
     * <p>每隔 2 格采一列、从地面往上再往上扫一段。之所以采得这么密：房子的图纸虽然有 17×17，
     * 真正砌起来的部分往往更窄，只按外沿采样会整整齐齐地漏过去。</p>
     *
     * @return 认到就返回 {@code true}
     */
    private static boolean hasBricks(ServerWorld world, BlockPos center) {
        if (!world.isChunkLoaded(ChunkSectionPos.getSectionCoord(center.getX()),
                ChunkSectionPos.getSectionCoord(center.getZ()))) {
            return false;
        }
        for (int dx = -7; dx <= 7; dx += 2) {
            for (int dz = -7; dz <= 7; dz += 2) {
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                if (!world.isChunkLoaded(ChunkSectionPos.getSectionCoord(x), ChunkSectionPos.getSectionCoord(z))) {
                    continue;
                }
                int top = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z);
                for (int y = top - 2; y <= top + 10; y++) {
                    BlockState state = world.getBlockState(new BlockPos(x, y, z));
                    if (state.isOf(Blocks.STONE_BRICKS)
                            || state.isOf(Blocks.MOSSY_STONE_BRICKS)
                            || state.isOf(Blocks.CRACKED_STONE_BRICKS)
                            || state.isOf(Blocks.CHISELED_STONE_BRICKS)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 在附近的区块里算出第 {@code skip + 1} 处「计划栋数不少于 homes」的聚落。
     *
     * <p>注意这里只按计划筛，不做实地核对——实地核对交给 {@link #settle}。</p>
     *
     * @return 选中那一处；没算出来则 {@code null}
     */
    private static Target search(ServerWorld world, ServerPlayerEntity player,
                                 LittleHomeClusterConfig config, int radius, int homes, int skip) {
        long seed = world.getSeed();
        int centerChunkX = ChunkSectionPos.getSectionCoord(player.getBlockX());
        int centerChunkZ = ChunkSectionPos.getSectionCoord(player.getBlockZ());
        int chunkRadius = radius / 16 + 1;

        int matched = 0;
        for (int ring = 0; ring <= chunkRadius; ring++) {
            // 一圈一圈往外走：先看近的，找到的就是最近的那一处
            for (int[] offset : ringOffsets(ring)) {
                int chunkX = centerChunkX + offset[0];
                int chunkZ = centerChunkZ + offset[1];
                LittleHomeClusterFeature.Settlement plan = LittleHomeClusterFeature.plan(
                        seed, config, chunkX, chunkZ);
                if (plan.isEmpty() || plan.size() < homes) {
                    continue;
                }
                BlockPos center = plan.center();
                long dx = player.getBlockX() - center.getX();
                long dz = player.getBlockZ() - center.getZ();
                long distance = Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
                if (distance > radius) {
                    continue;
                }
                if (matched++ < skip) {
                    continue;
                }
                return Target.of(plan, (int) distance);
            }
        }
        return null;
    }

    /**
     * 列出「距离中心第几圈」上的全部区块偏移。
     *
     * <p>只列这一圈的边——里圈在上一轮已经看过了。这样扫过的区块数正比于半径的平方，
     * 而不是立方。</p>
     *
     * @param ring 第几圈（0 就是中心区块自己）
     * @return 这一圈的区块偏移
     */
    private static List<int[]> ringOffsets(int ring) {
        List<int[]> offsets = new ArrayList<>();
        if (ring == 0) {
            offsets.add(new int[] { 0, 0 });
            return offsets;
        }
        for (int dx = -ring; dx <= ring; dx++) {
            offsets.add(new int[] { dx, -ring });
            offsets.add(new int[] { dx, ring });
        }
        for (int dz = -ring + 1; dz <= ring - 1; dz++) {
            offsets.add(new int[] { -ring, dz });
            offsets.add(new int[] { ring, dz });
        }
        return offsets;
    }

    /**
     * 这一处落在水里、河里或者沙滩上吗——那几种地方不长房子。
     *
     * @return 是水域或水边就返回 {@code true}
     */
    private static boolean isWet(RegistryEntry<Biome> biome) {
        return biome.isIn(BiomeTags.IS_OCEAN)
                || biome.isIn(BiomeTags.IS_DEEP_OCEAN)
                || biome.isIn(BiomeTags.IS_RIVER)
                || biome.isIn(BiomeTags.IS_BEACH);
    }

    /**
     * 读出聚落那一套参数。
     *
     * @param world 世界
     * @return 参数；读不到则 {@code null}
     */
    private static LittleHomeClusterConfig clusterConfig(ServerWorld world) {
        ConfiguredFeature<?, ?> feature = world.getRegistryManager()
                .get(RegistryKeys.CONFIGURED_FEATURE)
                .get(EternalRelic.id("little_home_cluster"));
        if (feature != null && feature.config() instanceof LittleHomeClusterConfig config) {
            return config;
        }
        return null;
    }

    /**
     * 选中那一处聚落的名册：几个落点、计划几栋、离玩家多远。
     *
     * @param spots    每一栋的落点
     * @param planned  计划栋数
     * @param distance 离玩家多远（格）
     */
    private record Target(List<BlockPos> spots, int planned, int distance) {

        static Target of(LittleHomeClusterFeature.Settlement plan, int distance) {
            return new Target(List.copyOf(plan.spots()), plan.size(), distance);
        }

        BlockPos center() {
            return spots.get(0);
        }

        int minX() {
            return spots.stream().mapToInt(BlockPos::getX).min().orElse(0) - HOUSE_HALF_SIZE;
        }

        int maxX() {
            return spots.stream().mapToInt(BlockPos::getX).max().orElse(0) + HOUSE_HALF_SIZE;
        }

        int minZ() {
            return spots.stream().mapToInt(BlockPos::getZ).min().orElse(0) - HOUSE_HALF_SIZE;
        }

        int maxZ() {
            return spots.stream().mapToInt(BlockPos::getZ).max().orElse(0) + HOUSE_HALF_SIZE;
        }
    }
}
