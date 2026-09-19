package org.eternalrelic.capability.carried;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.eternalrelic.relic.RelicAttachment;

/**
 * 「玩家身上这件遗物生效了没有」的共用查法 —— 所有「带在身上就生效」的遗物都从这里问。
 *
 * <p><b>生效有两个来源</b>：一是**背包里放着本体**（主背包与副手，遗物换到副手时效果应当继续，
 * 放进箱子、丢在地上则不算）；二是**正穿着或正拿着的装备上附着了一枚**
 * （见 {@link org.eternalrelic.relic.RelicAttachment}）。</p>
 *
 * <p>这条口径必须所有遗物一致——否则同一件东西挪到副手、或钉在胸甲上，
 * 一件遗物认、另一件不认，玩家只会觉得莫名其妙。因此把它收在这里，
 * 而不是让每个能力类各写一遍。</p>
 *
 * <p><b>三种问法各管一件事</b>：{@link #carries} / {@link #inEffect} 只回答「有没有」，
 * 不复制背包，可以放心放在「每次挨打」这类高频路径上；{@link #firstOf} 交出的是
 * <b>背包格子里原本的那一个物品堆</b>，调用方拿到就能往上写数据；
 * {@link #of} 给出的是一份副本，只在确实需要「一份不受后续改动影响的快照」时才用——
 * 改副本对玩家背包没有任何影响。</p>
 */
public final class CarriedStacks {

    private CarriedStacks() {
    }

    /**
     * 收集玩家身上参与判定的物品格。
     *
     * <p>这是一份**副本**，给需要「一份不会变的名单」的调用方用（例如先收集、之后再遍历）。
     * 只是想知道有没有某件东西、或想找到某一格原件时不要用它：前者用
     * {@link #carries} / {@link #inEffect}，后者用 {@link #firstOf}——
     * 这个方法每次都要把整份背包复制一遍，放在每次挨打这类高频路径上白白分配内存。</p>
     *
     * @param player 目标玩家
     * @return 主背包与副手的物品堆副本
     */
    public static List<ItemStack> of(PlayerEntity player) {
        List<ItemStack> stacks = new ArrayList<>(player.getInventory().main);
        stacks.addAll(player.getInventory().offHand);
        return stacks;
    }

    /**
     * 核对玩家身上是否带着某件物品。
     *
     * <p>就地遍历，不复制背包——这个方法在「每次挨打」的判定里被调用。</p>
     *
     * @param player 目标玩家
     * @param item   要找的物品
     * @return 主背包或副手里是否有至少一个该物品
     */
    public static boolean carries(PlayerEntity player, Item item) {
        return !firstIn(player.getInventory().main, item).isEmpty()
                || !firstIn(player.getInventory().offHand, item).isEmpty();
    }

    /**
     * 找出玩家身上第一件指定物品，并交出**那一格原本的物品堆**。
     *
     * <p>查找顺序是主背包 → 副手，与遗物「换到副手也照样生效」的口径一致。
     * 附着在装备上的那一份不算——它没有独立的物品格，数据在装备自己身上，
     * 拿不到「原件」也就写不进去。</p>
     *
     * <p><b>返回的不是副本</b>：调用方拿到之后往上面写数据（例如给碎裂的遗物记恢复时刻），
     * 改的就是玩家背包里的那一件。要找的东西还没确定时先看 {@link #carries}，
     * 需要一份不会变的快照再用 {@link #of}。</p>
     *
     * @param player 目标玩家
     * @param item   要找的物品
     * @return 那一格原本的物品堆；没有携带时返回 {@link ItemStack#EMPTY}
     */
    public static ItemStack firstOf(PlayerEntity player, Item item) {
        ItemStack found = firstIn(player.getInventory().main, item);
        return found.isEmpty() ? firstIn(player.getInventory().offHand, item) : found;
    }

    /**
     * 在一组物品格里就地找出第一件指定物品。
     *
     * @param slots 待查找的物品格
     * @param item  要找的物品
     * @return 那一格原本的物品堆；没有时返回 {@link ItemStack#EMPTY}
     */
    private static ItemStack firstIn(List<ItemStack> slots, Item item) {
        for (ItemStack stack : slots) {
            if (!stack.isEmpty() && stack.isOf(item)) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    /**
     * 这件遗物此刻是否在玩家身上生效。
     *
     * <p>背包里放着本体，或者正穿着 / 正拿着的装备上附着一枚，都算。</p>
     *
     * @param player 目标玩家
     * @param item   那件遗物
     * @return 是否生效
     */
    public static boolean inEffect(PlayerEntity player, Item item) {
        return carries(player, item) || RelicAttachment.activeOn(player, item);
    }
}
