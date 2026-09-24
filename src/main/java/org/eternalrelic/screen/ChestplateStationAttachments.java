package org.eternalrelic.screen;

import java.util.List;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.eternalrelic.block.entity.ChestplateStationBlockEntity;
import org.eternalrelic.relic.RelicAttachment;

/**
 * 胸甲台界面上那六格「已装配件」的内容 —— 它不是另存一份清单，而是**台上那件胸甲的实时视图**。
 *
 * <p>台上那件胸甲身上记着"装了哪些配件"（见 {@link RelicAttachment}），这个类把那份记录
 * 当成六格库房来用：读出来就是格子里的东西，拿走一枚就等于拆下一枚。</p>
 *
 * <p><b>⚠️ 与原版容器框架之间的约定，是这个类最容易出事的地方。</b>原版把"把东西从一格取走"
 * 分成好几条路，其中有的会调用 {@link #removeStack}，有的<b>改的是 {@link #getStack} 返回的那个对象</b>、
 * 还有的靠 {@link #setStack} 传一个空堆来表示"这格清空了"。只实现其中一条，就会出现
 * "东西进了玩家背包、而配件还挂在胸甲上"——也就是凭空复制。因此这里三条路全部对齐到同一件事：
 * <b>让格子变空 = 真的拆下那一枚</b>。</p>
 *
 * <p><b>只出不进</b>：{@link #isValid} 一律返回 {@code false}，任何东西都塞不进来。
 * 这不是刁难——如果能往里放，玩家就能把自己背包里的配件直接拖进去，**跳过配料白嫖一次装配**。</p>
 *
 * <p><b>还有一条同样重要的规矩在容器那边</b>：六格被排除在一切"通用搬运"之外
 * （见 {@link ChestplateStationScreenHandler#quickMove}）。原因是原版搬运逻辑里有一段"合并同款物品"，
 * 它<b>不检查能不能放</b>，只比对物品是否相同就把玩家手里那叠东西清零、去改一个临时对象——
 * 落到只读视图上就是<b>玩家的东西凭空消失</b>。</p>
 *
 * <p>拆下一枚要在台子那儿响一声，但本类不认识世界与坐标：那一响由构造时收到的通知转发出去。</p>
 */
public class ChestplateStationAttachments implements Inventory {

    /** 六格要照的那件胸甲的来源。 */
    private final ChestplateStationBlockEntity station;

    /** 拆下一枚时按的通知：由界面在台子那一格的位置播声音。 */
    private final Runnable hammer;

    /**
     * @param station 六格要照的那台胸甲台
     * @param hammer  拆下一枚时按的通知
     */
    public ChestplateStationAttachments(ChestplateStationBlockEntity station, Runnable hammer) {
        this.station = station;
        this.hammer = hammer;
    }

    /**
     * @return 台上那件胸甲；台上空着（或界面远端拿不到方块实体）时返回空堆
     */
    private ItemStack center() {
        return this.station == null ? ItemStack.EMPTY : this.station.getChestplate();
    }

    @Override
    public int size() {
        return RelicAttachment.MAX_ATTACHMENTS;
    }

    @Override
    public boolean isEmpty() {
        return RelicAttachment.attachedTo(center()).isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        List<Item> attached = RelicAttachment.attachedTo(center());
        return slot >= 0 && slot < attached.size() ? new ItemStack(attached.get(slot)) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack shown = getStack(slot);
        if (shown.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // 「拿走」就是「拆下」：直接改台上那件胸甲身上的记录。
        // 这是唯一会真正改动记录的地方，其余几条路都汇到这里。
        detachAt(shown);
        return shown;
    }

    @Override
    public ItemStack removeStack(int slot) {
        return removeStack(slot, 1);
    }

    /**
     * 写入一律忽略 —— 除非写进来的是<b>空堆</b>。
     *
     * <p>空堆的意思是"框架要求这一格变成空的"，也就是原版"按数字键把格子里的东西换到快捷栏"
     * 那条路所用的方式（它会先把手里的东西塞进来、再把原来那格 {@code setStack(EMPTY)}）。
     * 照单全收地忽略它，就会出现"快捷栏拿到一枚、胸甲上还挂着那一枚"的复制。
     * 所以这里把它翻译成真正的动作：<b>拆下</b>。</p>
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
     * 某一枚配件若还挂在台上的胸甲上，就把它拆下来。
     *
     * <p>按"这一格现在显示的是哪一件"去拆，而不是按调用方给的东西去拆 —— 视图是按顺序铺开的，
     * 调用方手里的那份可能已经过期。</p>
     *
     * @param shown 该格当前显示的那一枚（拆之前先取好）
     */
    private void detachAt(ItemStack shown) {
        ItemStack chestplate = center();
        if (RelicAttachment.detach(chestplate, shown.getItem())) {
            this.station.markChanged();

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
        if (this.station != null) {
            this.station.markChanged();
        }
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
