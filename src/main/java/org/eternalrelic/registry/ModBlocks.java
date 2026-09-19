package org.eternalrelic.registry;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

import org.eternalrelic.EternalRelic;
import org.eternalrelic.block.RelicStationBlock;
import org.eternalrelic.block.RelicStationBlockEntity;

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
     * 遗物装卸台的方块实体类型。
     *
     * <p>它把「这个方块里能存东西」这件事告诉游戏，并绑定到 {@link #RELIC_STATION} 上。</p>
     */
    public static final BlockEntityType<RelicStationBlockEntity> RELIC_STATION_ENTITY =
            Registry.register(Registries.BLOCK_ENTITY_TYPE,
                    EternalRelic.id("relic_station"),
                    FabricBlockEntityTypeBuilder.create(RelicStationBlockEntity::new, RELIC_STATION).build());

    private ModBlocks() {
    }

    /**
     * 由 {@link EternalRelic#onInitialize()} 调用，触发本类静态字段初始化并完成方块注册。
     */
    public static void register() {
    }

    private static Block register(String name, Block block) {
        return Registry.register(Registries.BLOCK, EternalRelic.id(name), block);
    }

    private static BlockItem registerItem(String name, BlockItem item) {
        return Registry.register(Registries.ITEM, EternalRelic.id(name), item);
    }
}
