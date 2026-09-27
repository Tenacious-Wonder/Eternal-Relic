package org.eternalrelic.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import org.eternalrelic.capability.consumed.RoundCakeEffect;

/**
 * 「一个圆形的饼」—— 吃下去就回到重生点，并飞快回血的一块吃食。
 *
 * <p><b>为什么这里可以不改动游戏内部代码</b>：游戏给食物留了一个「吃完那一刻」的口子
 * （{@code Item#finishUsing}），填饱肚子、扣掉物品都由原版照常处理，本类只在那之后补上
 * 自己的那份效果，因此整件事都在游戏允许的范围内完成。</p>
 *
 * <p>这块饼的饱食度与饥饿度直接用原版南瓜派那一份（{@link net.minecraft.item.FoodComponents#PUMPKIN_PIE}），
 * 数值与原版逐字一致，不另外手写一遍。</p>
 *
 * <p>效果只给玩家：狐狸、狼这类生物吃掉它时，照原版一样只是填肚子。</p>
 */
public class RoundCakeItem extends Item {

    public RoundCakeItem(Settings settings) {
        super(settings);
    }

    /**
     * 吃完时补上「还乡」效果。
     *
     * <p>先让原版把这一口吃完（回饱食度、扣掉一个饼），再送人回家、给回血。</p>
     *
     * @param stack 刚被吃掉的那一份
     * @param world 玩家所在的世界
     * @param user  吃下它的生物
     * @return 吃完之后手上剩下的东西（由原版决定）
     */
    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        ItemStack remaining = super.finishUsing(stack, world, user);

        if (user instanceof ServerPlayerEntity player) {
            RoundCakeEffect.bless(player);
        }

        return remaining;
    }
}
