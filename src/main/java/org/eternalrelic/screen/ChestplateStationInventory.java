package org.eternalrelic.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

/**
 * <h1>胸甲台界面的原料盘</h1>
 *
 * <p>
 * 胸甲台界面里的两个格子：待装的配件与它要用的配料。两格只活在这一次界面会话里——
 * 玩家取走成品或者关掉界面，剩下的东西就该还给他；需要长期留下来的只有台上那件胸甲，
 * 那属于 {@link org.eternalrelic.block.entity.ChestplateStationBlockEntity}。
 * 两者分开之后，界面开着时两个玩家各用各的原料盘，台上那件胸甲则是共用的。
 * </p>
 *
 * <ul>
 *     <li>{@link #SLOT_FITTING} —— 配件格：要装上去的那件配件（肩甲、内衬之类）；</li>
 *     <li>{@link #SLOT_MATERIAL} —— 配料格：装它要用的辅料（线、铁粒之类）。</li>
 * </ul>
 *
 * <p>
 * 点箭头之后两格各扣掉一个，扣减由 {@link ChestplateStationScreenHandler} 在服务端执行。
 * </p>
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
     * 两格都收——收什么由界面上的槽位把关。
     *
     * <p>两格的规矩不同，把关放在槽位那一侧，这里就不必分辨是第几格。</p>
     */
    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return true;
    }

    /** 两格不落盘，也没有需要在意的接收方，因此什么也不做。 */
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
