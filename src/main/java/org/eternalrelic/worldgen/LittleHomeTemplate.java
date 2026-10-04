package org.eternalrelic.worldgen;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * <h1>一栋小石屋的「图纸」</h1>
 *
 * <p>把结构文件（{@code data/eternal_relic/structures/*.nbt}）读成一份方块清单：每一块方块在房子里的
 * 相对位置，以及它本该是什么。世界生成时就是照着这份清单，一块一块地把房子盖到世界里去。</p>
 *
 * <p><b>为什么不直接用游戏自带的结构放置功能？</b>因为那样是一次性整栋贴上去的，中间插不进手；
 * 而我们要在放下去之前逐块决定「这块换成苔石砖吗」「这块塌掉吗」，所以必须自己拿着清单来。</p>
 *
 * <p>空气不在清单里：结构文件里那些没放东西的位置（约占九成）会被丢掉，这样房子周围原有的树木、
 * 草地都不会被抹掉，只有真正有方块的位置才会被替换。</p>
 */
public final class LittleHomeTemplate {

    /**
     * 图纸上的一块方块。
     *
     * @param offset 相对房子西北下角的偏移，原点在房子的最小角上
     * @param state  这块方块本来是什么
     */
    public record Piece(BlockPos offset, BlockState state) {
    }

    /**
     * 图纸缓存。世界生成时同一栋房子会被反复取用，而读文件、解压、查方块状态都不便宜，
     * 所以读过一次就留着。用并发容器是因为世界生成可能同时跑在多个线程上。
     */
    private static final Map<Identifier, Optional<LittleHomeTemplate>> CACHE = new ConcurrentHashMap<>();

    private final BlockPos size;
    private final List<Piece> pieces;
    private final int lowestY;

    private LittleHomeTemplate(BlockPos size, List<Piece> pieces, int lowestY) {
        this.size = size;
        this.pieces = pieces;
        this.lowestY = lowestY;
    }

    /** @return 房子的外框尺寸（宽 × 高 × 深） */
    public BlockPos size() {
        return size;
    }

    /** @return 房子里的全部方块 */
    public List<Piece> pieces() {
        return pieces;
    }

    /** @return 最低的那块方块在第几层。削掉地面后，房子就是从这一层开始坐在地上的 */
    public int lowestY() {
        return lowestY;
    }

    /**
     * 读取一份图纸。
     *
     * @param server 服务器实例，用来访问数据包文件与方块注册表
     * @param id     结构文件名（不含 {@code structures/} 前缀与 {@code .nbt} 后缀）
     * @return 读好的图纸；文件不存在或读坏时为空
     */
    public static Optional<LittleHomeTemplate> load(MinecraftServer server, Identifier id) {
        return CACHE.computeIfAbsent(id, key -> read(server, key));
    }

    private static Optional<LittleHomeTemplate> read(MinecraftServer server, Identifier id) {
        ResourceManager resources = server.getResourceManager();
        Identifier file = new Identifier(id.getNamespace(), "structures/" + id.getPath() + ".nbt");
        Optional<Resource> found = resources.getResource(file);
        if (found.isEmpty()) {
            return Optional.empty();
        }

        try (InputStream stream = found.get().getInputStream()) {
            NbtCompound root = NbtIo.readCompressed(stream);

            NbtList sizeTag = root.getList("size", NbtElement.INT_TYPE);
            BlockPos size = new BlockPos(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2));

            RegistryWrapper.Impl<Block> blocks = server.getRegistryManager()
                    .get(RegistryKeys.BLOCK)
                    .getReadOnlyWrapper();

            NbtList paletteTag = root.getList("palette", NbtElement.COMPOUND_TYPE);
            List<BlockState> palette = new ArrayList<>(paletteTag.size());
            for (int i = 0; i < paletteTag.size(); i++) {
                palette.add(NbtHelper.toBlockState(blocks, paletteTag.getCompound(i)));
            }

            List<Piece> pieces = new ArrayList<>();
            int lowestY = Integer.MAX_VALUE;
            NbtList blocksTag = root.getList("blocks", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < blocksTag.size(); i++) {
                NbtCompound entry = blocksTag.getCompound(i);
                int stateIndex = entry.getInt("state");
                if (stateIndex < 0 || stateIndex >= palette.size()) {
                    continue;
                }
                BlockState state = palette.get(stateIndex);
                if (state.isAir()) {
                    continue;
                }
                NbtList posTag = entry.getList("pos", NbtElement.INT_TYPE);
                BlockPos offset = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
                pieces.add(new Piece(offset, state));
                lowestY = Math.min(lowestY, offset.getY());
            }

            if (pieces.isEmpty() || lowestY == Integer.MAX_VALUE) {
                return Optional.empty();
            }
            return Optional.of(new LittleHomeTemplate(size, pieces, lowestY));
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
