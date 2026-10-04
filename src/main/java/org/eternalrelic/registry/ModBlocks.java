package org.eternalrelic.registry;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import org.eternalrelic.EternalRelic;
import org.eternalrelic.block.ChestplateStationBlock;
import org.eternalrelic.block.RelicSiftBlock;
import org.eternalrelic.block.RelicStationBlock;
import org.eternalrelic.mixin.BlockEntityTypeAccessor;

/**
 * 本模组的方块注册入口。
 *
 * <p>每件方块以「静态常量 + 私有 register 方法」的形式声明，与 {@link ModItems} 同一套写法。
 * 方块对应的物品（{@link BlockItem}）也在这里一并注册——它必须和方块同名，否则放置与拾取会对不上。</p>
 */
public final class ModBlocks {

    /**
     * 遗物装卸台 —— 既能装遗物、也能拆遗物的工作方块。
     *
     * <p>硬度与音效都比照原版锻造台：它是同类东西，玩家上手时应当有一样的质感。</p>
     *
     * <p>它没有方块实体：内容全在打开界面的那一次会话里（见
     * {@link org.eternalrelic.screen.RelicStationScreenHandler}），方块只当开关用。</p>
     */
    public static final Block RELIC_STATION = register("relic_station",
            new RelicStationBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.IRON_GRAY)
                    .requiresTool()
                    .strength(4.0F)
                    .sounds(BlockSoundGroup.METAL)));

    /** 遗物装卸台的方块物品。 */
    public static final BlockItem RELIC_STATION_ITEM = registerItem("relic_station",
            new BlockItem(RELIC_STATION, new Item.Settings()));

    /**
     * 胸甲台 —— 给胸甲装卸盔甲配件的工作方块，四个部位台子中的第一个。
     *
     * <p>胸甲平时摆在台面上展示，因此这个方块需要方块实体把它存下来
     * （见 {@link ModBlockEntityTypes}）。</p>
     */
    public static final Block CHESTPLATE_STATION = register("chestplate_station",
            new ChestplateStationBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.IRON_GRAY)
                    .strength(2.0F)
                    .nonOpaque()
                    .sounds(BlockSoundGroup.STONE)));

    /** 胸甲台的方块物品。 */
    public static final BlockItem CHESTPLATE_STATION_ITEM = registerItem("chestplate_station",
            new BlockItem(CHESTPLATE_STATION, new Item.Settings()));

    /**
     * 埋藏块里埋着的东西。先照搬原版沙漠水井的考古表，等玩法定下来再换成自己的内容。
     *
     * <p>表在 {@code data/eternal_relic/loot_tables/archaeology/relic_sift.json}。</p>
     */
    private static final Identifier SIFT_LOOT_TABLE = EternalRelic.id("archaeology/relic_sift");

    /** 可疑的草地 —— 看着和草方块一模一样，其实底下埋着东西。 */
    public static final Block RELIC_GRASS = registerSift("relic_grass",
            Blocks.GRASS_BLOCK,
            SoundEvents.ITEM_BRUSH_BRUSHING_GRAVEL,
            SoundEvents.ITEM_BRUSH_BRUSHING_GRAVEL_COMPLETE);

    /** 可疑草地的方块物品。 */
    public static final BlockItem RELIC_GRASS_ITEM = registerItem("relic_grass",
            new BlockItem(RELIC_GRASS, new Item.Settings()));

    /** 可疑的沙子 —— 看着和沙子一模一样。 */
    public static final Block RELIC_SAND = registerSift("relic_sand",
            Blocks.SAND,
            SoundEvents.ITEM_BRUSH_BRUSHING_SAND,
            SoundEvents.ITEM_BRUSH_BRUSHING_SAND_COMPLETE);

    /** 可疑沙子的方块物品。 */
    public static final BlockItem RELIC_SAND_ITEM = registerItem("relic_sand",
            new BlockItem(RELIC_SAND, new Item.Settings()));

    /** 可疑的沙砾 —— 看着和沙砾一模一样。 */
    public static final Block RELIC_GRAVEL = registerSift("relic_gravel",
            Blocks.GRAVEL,
            SoundEvents.ITEM_BRUSH_BRUSHING_GRAVEL,
            SoundEvents.ITEM_BRUSH_BRUSHING_GRAVEL_COMPLETE);

    /** 可疑沙砾的方块物品。 */
    public static final BlockItem RELIC_GRAVEL_ITEM = registerItem("relic_gravel",
            new BlockItem(RELIC_GRAVEL, new Item.Settings()));

    /** 可疑的灰化土 —— 看着和灰化土一模一样。 */
    public static final Block RELIC_PODZOL = registerSift("relic_podzol",
            Blocks.PODZOL,
            SoundEvents.ITEM_BRUSH_BRUSHING_GRAVEL,
            SoundEvents.ITEM_BRUSH_BRUSHING_GRAVEL_COMPLETE);

    /** 可疑灰化土的方块物品。 */
    public static final BlockItem RELIC_PODZOL_ITEM = registerItem("relic_podzol",
            new BlockItem(RELIC_PODZOL, new Item.Settings()));

    /**
     * 裂石砖楼梯 —— 补原版的缺。
     *
     * <p>1.20.1 只有裂石砖方块，没有配套的楼梯和台阶（那是后来版本才补的），
     * 而遗迹做旧正需要它们：一面墙如果整砖裂了、台阶却是好的，成色就对不上了。</p>
     *
     * <p>贴图直接用裂石砖那张；属性（硬度、音效、挖掘等级）比照原版石砖楼梯。</p>
     */
    public static final Block CRACKED_STONE_BRICK_STAIRS = register("cracked_stone_brick_stairs",
            new StairsBlock(Blocks.CRACKED_STONE_BRICKS.getDefaultState(),
                    AbstractBlock.Settings.copy(Blocks.STONE_BRICK_STAIRS)));

    /** 裂石砖楼梯的方块物品。 */
    public static final BlockItem CRACKED_STONE_BRICK_STAIRS_ITEM = registerItem("cracked_stone_brick_stairs",
            new BlockItem(CRACKED_STONE_BRICK_STAIRS, new Item.Settings()));

    /** 裂石砖台阶（半砖）—— 同样是补原版的缺。 */
    public static final Block CRACKED_STONE_BRICK_SLAB = register("cracked_stone_brick_slab",
            new SlabBlock(AbstractBlock.Settings.copy(Blocks.STONE_BRICK_SLAB)));

    /** 裂石砖台阶的方块物品。 */
    public static final BlockItem CRACKED_STONE_BRICK_SLAB_ITEM = registerItem("cracked_stone_brick_slab",
            new BlockItem(CRACKED_STONE_BRICK_SLAB, new Item.Settings()));

    private ModBlocks() {
    }

    /**
     * 由 {@link EternalRelic#onInitialize()} 调用，触发本类静态字段初始化并完成方块注册。
     */
    public static void register() {
        bindBrushable();
    }

    /**
     * 把四个埋藏块登记进原版的「可刷方块」类型，让它们能共用原版那套刷取逻辑。
     *
     * <p>原版那个类型的支持列表是不可变集合，所以要取出内容、换成新的可变集合再写回去
     * （见 {@link BlockEntityTypeAccessor}）。这只是让那个容器多装四个方块，
     * <b>不改变原版可疑沙子的任何行为</b>。</p>
     */
    private static void bindBrushable() {
        BlockEntityTypeAccessor accessor = (BlockEntityTypeAccessor) BlockEntityType.BRUSHABLE_BLOCK;
        Set<Block> supported = new HashSet<>(accessor.getBlocks());
        supported.add(RELIC_GRASS);
        supported.add(RELIC_SAND);
        supported.add(RELIC_GRAVEL);
        supported.add(RELIC_PODZOL);
        accessor.setBlocks(supported);
    }

    /**
     * 登记一个「遗物埋藏块」。
     *
     * @param name    方块名
     * @param baseBlock 刷完之后变回哪个方块，同时它的属性（硬度、音效、挖掘等级）也照抄过来
     * @param brushing 刷的过程中的声音
     * @param complete 刷完那一下的声音
     * @return 登记好的方块
     */
    private static Block registerSift(String name, Block baseBlock, SoundEvent brushing, SoundEvent complete) {
        return register(name, new RelicSiftBlock(baseBlock,
                AbstractBlock.Settings.copy(baseBlock), brushing, complete, SIFT_LOOT_TABLE));
    }

    private static Block register(String name, Block block) {
        return Registry.register(Registries.BLOCK, EternalRelic.id(name), block);
    }

    private static BlockItem registerItem(String name, BlockItem item) {
        return Registry.register(Registries.ITEM, EternalRelic.id(name), item);
    }
}
