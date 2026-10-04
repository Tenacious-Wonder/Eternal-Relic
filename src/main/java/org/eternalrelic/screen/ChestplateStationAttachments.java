package org.eternalrelic.screen;

import java.util.List;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.eternalrelic.block.entity.ChestplateStationBlockEntity;
import org.eternalrelic.relic.RelicAttachment;

/**
 * <h1>已装配件的只读视图</h1>
 *
 * <p>
 * 界面上的六格「已装配件」不是另存一份清单，而是台上那件胸甲的实时视图：格子里的东西取自
 * 那件胸甲身上记的配件（见 {@link RelicAttachment}），拿走一枚就等于拆下一枚。
 * </p>
 *
 * <h2>三条取走路径</h2>
 * <p>
 * 原版容器框架把"把东西从一格取走"分成好几条路：有的调用 {@link #removeStack}，有的直接改动
 * {@link #getStack} 返回的对象，还有的靠 {@link #setStack} 传一个空堆来表示"这格清空了"。
 * 只对齐其中一条，就会出现"东西进了玩家背包、配件却还挂在胸甲上"的复制。因此这里三条路
 * 全部归到同一件事：<b>格子变空就是真的拆下那一枚</b>。
 * </p>
 *
 * <h2>只出不进</h2>
 * <p>
 * {@link #isValid} 一律返回 {@code false}，任何东西都放不进来；否则玩家可以直接把背包里的配件
 * 拖进去，跳过配料完成一次装配。
 * </p>
 *
 * <h2>不参与通用搬运</h2>
 * <p>
 * 六格被排除在一切"通用搬运"之外（见 {@link ChestplateStationScreenHandler#quickMove}）：
 * 原版搬运逻辑里有一段"合并同款物品"，它不检查能否放置，只比对物品是否相同就把玩家手里那叠
 * 清零、去改一个临时对象——落到只读视图上就是玩家的物品凭空消失。
 * </p>
 *
 * <p>
 * 拆下一枚要在台子那儿响一声，但本类不认识世界与坐标：那一响由构造时收到的通知转发出去。
 * </p>
 */
public class ChestplateStationAttachments implements Inventory {
    /** 六格要照的那件胸甲的来源。 */
    private final ChestplateStationBlockEntity station;

    /** 拆下一枚时按的通知：由界面在台子那一格的位置播声音。 */
    private final Runnable hammer;

    public ChestplateStationAttachments(ChestplateStationBlockEntity station, Runnable hammer) {
        this.station = station;
        this.hammer = hammer;
    }

    /**
     * @return 台上那件胸甲的副本；台上空着（或界面远端拿不到方块实体）时返回空堆
     */
    private ItemStack center() {
        return this.station == null ? ItemStack.EMPTY : this.station.getChestplate();
    }

    @Override
    public int size() {
        return RelicAttachment.MAX_FITTINGS;
    }

    @Override
    public boolean isEmpty() {
        return RelicAttachment.fittingsOn(center()).isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        // 只列「装备配件」这一桶：纹章一类归遗物装卸台管，两边各看各的
        List<Item> attached = RelicAttachment.fittingsOn(center());
        return slot >= 0 && slot < attached.size() ? new ItemStack(attached.get(slot)) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack shown = getStack(slot);
        if (shown.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // 「拿走」就是「拆下」：这是唯一真正改动记录的地方，其余两条路都汇到这里
        detachAt(shown);
        return shown;
    }

    @Override
    public ItemStack removeStack(int slot) {
        return removeStack(slot, 1);
    }

    /**
     * 写入一律忽略，除非写进来的是空堆。
     *
     * <p>
     * 空堆的意思是"框架要求这一格变空"，原版"按数字键把格子里的东西换到快捷栏"就走这条路
     * （先把手里那件塞进来、再把原来那格置空）。忽略它会出现"快捷栏拿到一枚、胸甲上还挂着一枚"
     * 的复制，因此这里把它翻译成真正的动作：拆下。
     * </p>
     */
    @Override
    public void setStack(int slot, ItemStack stack) {
        if (!stack.isEmpty()) {
            return;
        }

        ItemStack shown = getStack(slot);
        if (!shown.isEmpty()) {
            detachAt(shown);
        }
    }

    /**
     * 把该格当前显示的那一枚从台上的胸甲上拆下来。
     *
     * <p>
     * 按显示的这一件去拆，而不是按调用方手里的东西——视图是按顺序铺开的，调用方手里那份可能已经过期。
     * </p>
     *
     * @param shown 该格当前显示的那一枚（拆之前先取好）
     */
    private void detachAt(ItemStack shown) {
        // center() 给的是副本：改完必须写回，否则台面上的胸甲不会变
        ItemStack chestplate = center();
        if (RelicAttachment.detach(chestplate, shown.getItem())) {
            this.station.setChestplate(chestplate);

            // 拆下来了：锤子敲一下
            this.hammer.run();
        }
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public void markDirty() {
        // 台面内容的存档与下发已随每次 setChestplate 完成，这里不需要额外动作
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return true;
    }

    @Override
    public void clear() {
        // 清空等于把配件全部拆掉，而拆卸必须由玩家一枚一枚地点——这里不提供"一键全拆"
    }
}
