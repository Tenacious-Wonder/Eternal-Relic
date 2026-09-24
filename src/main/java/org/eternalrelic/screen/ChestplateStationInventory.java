package org.eternalrelic.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

/**
 * 胸甲台界面里的两个格子 —— 放待装的配件与它要用的配料，随界面而生、随界面而灭。
 *
 * <p><b>它为什么不是方块实体</b>：这两格只是"这一次装配动作的原料盘"，玩家拿走成品或者关掉界面，
 * 剩下的东西就该还给他。真正要长期留下来的只有台上的那件胸甲，那归
 * {@code block.entity.ChestplateStationBlockEntity} 管。两者分开之后，界面开着时两个玩家各用各的原料盘，
 * 而台上那件胸甲是共用的——这也正是这台子的本意。</p>
 *
 * <p>两格的分工与界面上的位置一致：</p>
 *
 * <ul>
 *   <li>{@link #SLOT_FITTING} —— <b>配件格</b>：要装上去的那件配件（肩甲、内衬之类），在上；</li>
 *   <li>{@link #SLOT_MATERIAL} —— <b>配料格</b>：装它要用的辅料（线、铁粒之类），在下。</li>
 * </ul>
 *
 * <p>点箭头之后，这两格各扣掉一个——扣减由 {@link ChestplateStationScreenHandler} 在服务端执行，
 * 因为"装上去"这件事只有服务端说了算。</p>
 */
public class ChestplateStationInventory implements Inventory {

    /** 配件格：要装上去的那一件配件。 */
    public static final int SLOT_FITTING = 0;

    /** 配料格：装它要用的辅料。 */
    public static final int SLOT_MATERIAL = 1;

    /** 一共几个格子。 */
    public static final int SIZE = 2;

    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);

    @Override
    public int size() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        return this.items.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack removed = Inventories.splitStack(this.items, slot, amount);
        markDirty();
        return removed;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack stack = this.items.get(slot);
        return stack.isEmpty() ? ItemStack.EMPTY : removeStack(slot, stack.getCount());
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        this.items.set(slot, stack);
        markDirty();
    }

    /**
     * 两格都收——收什么由界面上的槽位自己把关（配件格只收配件、配料格只收辅料）。
     *
     * <p>把关写在槽位那一侧而不是这里，是因为两格的规矩不同；写在这里就得先知道是第几格，
     * 反而绕。</p>
     */
    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return true;
    }

    /**
     * 什么也不做。
     *
     * <p>这两格不落盘，也没有要在意"脏了"的接收方——内容只活在这一次界面会话里。</p>
     */
    @Override
    public void markDirty() {
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return true;
    }

    @Override
    public void clear() {
        this.items.clear();
    }
}
