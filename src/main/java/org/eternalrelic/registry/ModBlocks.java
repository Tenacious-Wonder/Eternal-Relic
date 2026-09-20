package org.eternalrelic.registry;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

import org.eternalrelic.EternalRelic;
import org.eternalrelic.block.RelicStationBlock;

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
