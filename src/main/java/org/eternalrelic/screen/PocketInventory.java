package org.eternalrelic.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

import org.eternalrelic.registry.ModItems;
import org.eternalrelic.relic.PocketStorage;

/**
 * 打开拾荒口袋时，界面用的那份 54 格内容。
 *
 * <p><b>它是一层「视图」，真正的东西在口袋这件物品的数据里</b>：构造时把物品数据读进来，
 * 此后每次内容变动（{@link #markDirty()}）就写回物品数据。因此界面开着的时候，
 * 玩家把口袋丢掉、换手、被别人拿走，里面的东西都跟着口袋走，不会出现「界面里有一套、
 * 物品上是另一套」。</p>
 *
 * <p><b>为什么写回必须发生在每次变动、而不是关界面时</b>：玩家关界面的方式太多
 * （按 E、按 Esc、被怪打死、掉线、服务器崩），而后三种根本走不到「关闭」那一步。
 * 每变动一次就写一次，最坏情况只差最后一次点击，而那个差别肉眼看不出来。</p>
 *
 * <p>界面本身用的是原版大箱子那一套（{@code GenericContainerScreenHandler} 的 6 行款），
 * 因此翻页、Shift 搬运、快捷栏数字键全都是原版行为，本模组一行都不用写。</p>
 */
public class PocketInventory extends SimpleInventory {

    /** 这一份内容属于哪一口口袋。 */
    private final ItemStack pocket;

    /** 正在从物品数据里读入时不要再写回去（否则构造过程中会反复写同一份内容）。 */
    private boolean loading;

    /**
     * @param pocket 被右键打开的那一口口袋
     */
    public PocketInventory(ItemStack pocket) {
        super(PocketStorage.SIZE);
        this.pocket = pocket;

        this.loading = true;
        DefaultedList<ItemStack> contents = PocketStorage.read(pocket);
        for (int slot = 0; slot < contents.size(); slot++) {
            this.stacks.set(slot, contents.get(slot));
        }
        this.loading = false;
    }

    /**
     * 口袋里不能再放一口袋 —— 与 {@link PocketStorage#insert} 同一道闸，
     * 挡住「界面里塞进去」这条路径。
     */
    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return !stack.isOf(ModItems.SCAVENGER_POCKET);
    }

    /**
     * 内容有变动时写回物品数据，并照常通知界面刷新。
     *
     * <p>必须调 {@code super.markDirty()}：界面靠它同步格子，漏了的话点一下界面不动。</p>
     */
    @Override
    public void markDirty() {
        super.markDirty();

        if (!this.loading) {
            PocketStorage.write(this.pocket, this.stacks);
        }
    }

    /**
     * 必须是<b>这一口</b>口袋还在玩家身上，界面才算数。
     *
     * <p>为什么不写成「他身上还有拾荒口袋就行」：那样玩家在界面开着的时候把手上这口换成另一口，
     * 界面会继续开着，而后面的每一次变动都被写回<b>原来那一口</b>——看起来就像东西放进了新的口袋、
     * 实际却跑回了旧的。用同一对象比对（{@code main.contains} 走的就是引用相等），
     * 换掉即关界面，与潜影盒那类容器的表现一致。</p>
     */
    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return player.getInventory().main.contains(this.pocket)
                || player.getInventory().offHand.contains(this.pocket);
    }
}
