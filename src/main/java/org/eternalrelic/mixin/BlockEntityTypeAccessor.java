package org.eternalrelic.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;

/**
 * <h1>让原版「可刷方块」这个方块实体类型多认得几个方块</h1>
 *
 * <p>原版的可疑沙子／可疑沙砾共用一种方块实体（负责「刷十下、逐渐开裂、最后吐东西」那套逻辑），
 * 而这个类型在注册时把「支持哪些方块」做成了<b>不可变集合</b>，游戏也没提供往里加方块的口子。
 * 所以这里借 Mixin 把那个集合取出来、换成一个可变的，好让我们自己的埋藏块也能用上同一套逻辑。</p>
 *
 * <p><b>它不改变原版可疑沙子的任何行为</b>——只是让那个容器多装两个方块。原版方块照旧走原样。</p>
 */
@Mixin(BlockEntityType.class)
public interface BlockEntityTypeAccessor {

    /**
     * @return 这个方块实体类型当前支持的全部方块
     */
    @Accessor("blocks")
    Set<Block> getBlocks();

    /**
     * 换掉支持列表。配合 {@link Mutable} 才能写入原本被 final 锁住的字段。
     *
     * @param blocks 新的支持列表
     */
    @Mutable
    @Accessor("blocks")
    void setBlocks(Set<Block> blocks);
}
