package org.eternalrelic.relic;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.collection.DefaultedList;

import org.eternalrelic.capability.carried.CarriedStacks;
import org.eternalrelic.registry.ModItems;

/**
 * 「拾荒口袋」自己的内容 —— 这个口袋能装 54 格东西（与原版大箱子一样大），
 * 装了什么<b>记在口袋这件物品自己的数据里</b>。
 *
 * <p>为什么不另立一份「哪个玩家有几号口袋、里面装着什么」的名册：理由与引魂燃灯的魂火、
 * 装备上的附件完全一致——口袋是会被丢出去、放进箱子、交给别人的东西，
 * 里面的东西必须<b>跟着它走</b>。这样一来，退出重进、跨维度、送人，口袋里的东西都不会丢，
 * 服务端也不必额外维护状态，更不会出现「名册说里面有三块铁、实物却是空的」这种错位。</p>
 *
 * <p>读写用的是原版容器那套格式（{@code Inventories.readNbt / writeNbt}，
 * 也就是箱子、潜影盒用的 {@code Items} 列表），因此口袋里的东西与任何原版容器长得一模一样，
 * 出问题时用通用的眼光就能看懂。</p>
 *
 * <p><b>口袋里不能再装一口袋</b>（见 {@link #insert}）：那会套出一层又一层的数据，
 * 而且 NBT 一旦自引用就可能把存档写坏。原版的潜影盒也是同样的规矩。</p>
 */
public final class PocketStorage {

    /** 口袋的格数：与原版大箱子一致（6 行 × 9 格）。 */
    public static final int SIZE = 54;

    private PocketStorage() {
    }

    /**
     * 读出口袋里现在装着的东西。
     *
     * <p>返回的是一份<b>新列表</b>，但里面的物品堆还是口袋里的那一批对象；
     * 改完记得用 {@link #write} 写回去。</p>
     *
     * @param pocket 口袋这件物品
     * @return 54 格的内容（没有数据时全是空气）
     */
    public static DefaultedList<ItemStack> read(ItemStack pocket) {
        DefaultedList<ItemStack> contents = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);

        NbtCompound nbt = pocket.getNbt();
        if (nbt != null) {
            Inventories.readNbt(nbt, contents);
        }

        return contents;
    }

    /**
     * 把内容写回口袋。
     *
     * @param pocket   口袋这件物品
     * @param contents 要写进去的 54 格内容
     */
    public static void write(ItemStack pocket, DefaultedList<ItemStack> contents) {
        Inventories.writeNbt(pocket.getOrCreateNbt(), contents);
    }

    /**
     * 口袋里是否装着某件东西（逐格找，不看数量）。
     *
     * @param pocket 口袋这件物品
     * @param item   要找的东西
     * @return 是否装着至少一个
     */
    public static boolean contains(ItemStack pocket, Item item) {
        for (ItemStack stack : read(pocket)) {
            if (!stack.isEmpty() && stack.isOf(item)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 找出玩家身上那口「装着拾荒符石」的口袋。
     *
     * <p>只看主背包与副手（与其它携带类遗物同一套口径）。</p>
     *
     * @param player 目标玩家
     * @return 口袋这件物品<b>原本的那一堆</b>（改它就是改玩家背包里的东西）；没找到时返回空气
     */
    public static ItemStack magnetPocketOf(PlayerEntity player) {
        for (ItemStack stack : CarriedStacks.of(player)) {
            if (stack.isOf(ModItems.SCAVENGER_POCKET) && contains(stack, ModItems.SCAVENGER_MAGNET)) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    /**
     * 把一份物品塞进口袋，能塞多少塞多少（先并进已有的同类堆，再找空格）。
     *
     * <p><b>口袋本身、以及另一口口袋都塞不进去</b>：防的是「口袋里装口袋」套出一层又一层，
     * 那是能把存档写坏的写法。</p>
     *
     * @param pocket 口袋这件物品
     * @param stack  要放进去的东西；<b>会被就地扣减</b>，剩下的是没塞进去的部分
     * @return 真正塞进去的数量
     */
    public static int insert(ItemStack pocket, ItemStack stack) {
        if (pocket.isEmpty() || stack.isEmpty() || stack.isOf(ModItems.SCAVENGER_POCKET)) {
            return 0;
        }

        DefaultedList<ItemStack> contents = read(pocket);
        int remaining = stack.getCount();

        // 先并进已有的同类堆：口袋不会因为东西碎着放而先被塞满
        for (ItemStack slot : contents) {
            if (remaining <= 0) {
                break;
            }

            if (slot.isEmpty() || !ItemStack.canCombine(slot, stack)) {
                continue;
            }

            int room = slot.getMaxCount() - slot.getCount();
            if (room <= 0) {
                continue;
            }

            int moved = Math.min(room, remaining);
            slot.increment(moved);
            remaining -= moved;
        }

        // 再找空格放新的
        for (int slot = 0; slot < contents.size() && remaining > 0; slot++) {
            if (!contents.get(slot).isEmpty()) {
                continue;
            }

            int moved = Math.min(stack.getMaxCount(), remaining);
            ItemStack copy = stack.copy();
            copy.setCount(moved);
            contents.set(slot, copy);
            remaining -= moved;
        }

        int inserted = stack.getCount() - remaining;
        if (inserted > 0) {
            stack.decrement(inserted);
            write(pocket, contents);
        }

        return inserted;
    }

    /**
     * 玩家带着「装了拾荒符石的口袋」时，把这一份物品优先塞进那口口袋。
     *
     * <p>这就是磁石与口袋的配套效果：只要磁石在口袋里，捡到的东西就先往口袋里走，
     * 不占玩家自己的背包。没带这样的口袋时返回 0，调用方照常走原来的路。</p>
     *
     * @param player 目标玩家
     * @param stack  捡到的东西；<b>会被就地扣减</b>
     * @return 塞进口袋的数量
     */
    public static int insertIntoMagnetPocket(PlayerEntity player, ItemStack stack) {
        ItemStack pocket = magnetPocketOf(player);
        return pocket.isEmpty() ? 0 : insert(pocket, stack);
    }
}
