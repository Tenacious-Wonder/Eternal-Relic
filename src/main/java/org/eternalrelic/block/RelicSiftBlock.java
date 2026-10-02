package org.eternalrelic.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BrushableBlock;
import net.minecraft.block.entity.BrushableBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * <h1>遗物埋藏块 —— 看起来是普通方块，其实能刷</h1>
 *
 * <p>玩法完全照搬原版的可疑沙子：拿刷子在它上面刷十下，方块会一格一格「起灰」，
 * 刷满之后裂开、吐出一样东西，自己变回对应的普通方块。刷子的进度、音效、飞灰粒子都是原版那一套——
 * 因为我们直接用原版的方块实体，只是换了个外壳。</p>
 *
 * <p><b>为什么它看起来和普通方块一模一样</b>：它的模型直接沿用了原版方块（草方块、沙子、沙砾、灰化土）的模型，
 * 所以肉眼分辨不出来。这正是「埋藏」的意思——玩家得靠刷子碰运气。</p>
 *
 * <h2>和原版的一个差别</h2>
 * <p>原版可疑沙子里的东西写在<b>结构文件</b>里（所以自然生成的那些才有东西，玩家自己放的是空的）。
 * 我们这里改成<b>放下时就挂好战利品表</b>，因此创造模式摆出来的也能刷出东西，方便测试。</p>
 */
public class RelicSiftBlock extends BrushableBlock {

    private final Identifier lootTable;

    /**
     * @param baseBlock          刷完之后变回哪个方块
     * @param settings           方块属性（硬度、音效、挖掘工具等）
     * @param brushingSound      刷的过程中的声音
     * @param brushingCompleteSound 刷完那一下的声音
     * @param lootTable          里面埋着什么
     */
    public RelicSiftBlock(Block baseBlock, Settings settings,
                          SoundEvent brushingSound, SoundEvent brushingCompleteSound,
                          Identifier lootTable) {
        super(baseBlock, settings, brushingSound, brushingCompleteSound);
        this.lootTable = lootTable;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);

        // 放下的一瞬间就把战利品表挂上（原版靠结构文件提供，我们这里自己给）
        if (!world.isClient() && world.getBlockEntity(pos) instanceof BrushableBlockEntity brushable) {
            brushable.setLootTable(this.lootTable, world.random.nextLong());
        }
    }
}
