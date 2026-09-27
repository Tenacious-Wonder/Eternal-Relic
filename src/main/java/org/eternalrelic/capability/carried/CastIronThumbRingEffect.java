package org.eternalrelic.capability.carried;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import org.eternalrelic.registry.ModItems;
import org.eternalrelic.relic.AttachTarget;

/**
 * 「工具与武器偶尔不掉耐久」能力：带着铸铁拇指戒时，每一次耐久损耗都有机会不落下来。
 *
 * <p><b>它拦的是「游戏已经决定要扣耐久」的那一刻</b>：游戏里所有耐久损耗——挖方块、砍怪、
 * 拉弓射箭、剪羊毛、用打火石——最后都会汇到同一个方法上，那里才是真正把耐久加上去的一步。
 * 判定挂在那一步上（见 {@link org.eternalrelic.mixin.ItemStackDurabilityMixin}），
 * 这里只回答「这一次要不要保住」。</p>
 *
 * <p><b>只认工具与武器</b>：护甲挨打时也会掉耐久，而它走的是同一条路。因此这里按物品本身的
 * 类别把关，判据直接复用 {@link AttachTarget} 那份已有分类——原版或别的模组新添的工具武器
 * 都会被自动认出来，不必回来补名单。盾牌与鞘翅不算。</p>
 *
 * <p><b>带多枚也只算 5%</b>：只问「带没带」，不问带了几枚。</p>
 */
public final class CastIronThumbRingEffect {

    /** 一次耐久损耗被保住的概率。 */
    private static final float SAVE_CHANCE = 0.05F;

    private CastIronThumbRingEffect() {
    }

    /**
     * 这一次耐久损耗要不要被保住。
     *
     * <p>随机源取持有者身上的那个，而不是调用方传进来的——两者本来看的就是同一个，
     * 但这样写不必担心别的调用方传了空值进来。</p>
     *
     * @param stack  正在被磨损的那件东西
     * @param holder 用着它的人；发射器、漏斗这类非玩家造成的损耗在这里是 {@code null}
     * @return 保住时返回 {@code true}（这一次的耐久一点都不会掉）
     */
    public static boolean savesDurability(ItemStack stack, ServerPlayerEntity holder) {
        if (holder == null || !stack.isDamageable() || !isToolOrWeapon(stack)) {
            return false;
        }

        if (!CarriedStacks.inEffect(holder, ModItems.CAST_IRON_THUMB_RING)) {
            return false;
        }

        return holder.getRandom().nextFloat() < SAVE_CHANCE;
    }

    /**
     * 这件东西算不算「工具或武器」。
     *
     * <p>护甲与盾牌不算——它们挨打时也会掉耐久，而那不是这枚戒指要管的事。</p>
     *
     * @param stack 待判断的物品堆
     * @return 属于工具或武器时返回 {@code true}
     */
    private static boolean isToolOrWeapon(ItemStack stack) {
        return AttachTarget.WEAPON.covers(stack.getItem()) || AttachTarget.TOOL.covers(stack.getItem());
    }
}
