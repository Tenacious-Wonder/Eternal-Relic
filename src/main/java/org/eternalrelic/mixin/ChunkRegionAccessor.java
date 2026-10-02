package org.eternalrelic.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.ChunkRegion;

/**
 * <h1>借原版「这一步能往多远写方块」的旋钮用一下</h1>
 *
 * <p>世界生成是一步一步来的（先地形、再挖洞、再放地物……）。每走一步，游戏都会把
 * <b>当前区块连同周围一圈区块</b>打包成一个「工作区」交给这一步，同时限定：这一步只准在
 * 离中心区块<b>若干个区块以内</b>写方块，写远了直接丢掉、连报错都不给。</p>
 *
 * <p>放地物这一步，原版给的限额是「上下左右各 1 个区块」——因为原版自己的树、花草、
 * 冰刺都只在自己身边几格内动土，1 个区块绰绰有余。可我们的聚落要一口气铺到几十格开外，
 * 于是第二栋之后全被这条规则悄悄吃掉了。</p>
 *
 * <p>这个接口把那枚旋钮暴露出来：读得到、改得动。改的时候是「就地改」——
 * 谁在摆聚落，谁就把自己手上这份工作区的范围临时调宽，摆完立刻调回去，
 * 因此<b>不会影响原版或其它模组的任何地物</b>，它们拿到的还是原样那 1 个区块。</p>
 */
@Mixin(ChunkRegion.class)
public interface ChunkRegionAccessor {

    /**
     * @return 当前允许写到离中心区块几个区块以外
     */
    @Accessor("placementRadius")
    int getPlacementRadius();

    /**
     * 改掉允许写入的范围。
     *
     * <p>配合 {@link Mutable} 才能写入原本被 final 锁住的字段。</p>
     *
     * @param placementRadius 新的范围（单位：区块）
     */
    @Mutable
    @Accessor("placementRadius")
    void setPlacementRadius(int placementRadius);
}
