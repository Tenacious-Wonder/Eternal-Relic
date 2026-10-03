package org.eternalrelic.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import org.eternalrelic.capability.carried.HomestoneEffect;

/**
 * 归乡石 —— 拿在手上右键：第一次记下脚下这一处，再右键一次人立刻回到那里。
 *
 * <p>交互方式与牧羊人铃铛、蜡封手账一类相同：<b>拿在手上右键</b>，而不是另设一个按键。
 * 这样做的另一个好处是<b>用的一定是手上那一块</b>——玩家身上同时带着两块归乡石时，
 * 不必去猜模组会挑哪一块（记下的地点与冷却都写在各块石头自己身上）。</p>
 *
 * <p>只由服务端发话：客户端那边也会跑一遍 {@code use}，两边都报就会刷出两条一模一样的消息；
 * 传送更是只能由服务端来做。因此开头一句就把客户端挡回去。</p>
 */
public class HomestoneItem extends Item {

    public HomestoneItem(Settings settings) {
        super(settings);
    }

    /**
     * 右键用一次归乡石。
     *
     * @param world 玩家所在的世界
     * @param user  用石头的玩家
     * @param hand  用的是哪只手（主手副手都行）
     * @return 使用结果；不消耗物品
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient()) {
            return TypedActionResult.success(stack);
        }

        if (!(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.success(stack);
        }

        player.sendMessage(messageFor(HomestoneEffect.use(player, stack)), false);
        return TypedActionResult.success(stack);
    }

    /**
     * 把服务端算出来的结果翻成给玩家看的那句话。
     *
     * @param result 这一次右键的结果
     * @return 该回给玩家的文本
     */
    private static Text messageFor(HomestoneEffect.Result result) {
        return switch (result) {
            case RECORDED -> Text.translatable("message.eternal_relic.homestone_recorded");
            case RETURNED -> Text.translatable("message.eternal_relic.homestone_returned");
            case COOLING -> Text.translatable("message.eternal_relic.homestone_cooling");
            case OTHER_DIMENSION -> Text.translatable("message.eternal_relic.homestone_other_dimension");
            case NO_EXPERIENCE -> Text.translatable("message.eternal_relic.homestone_no_experience");
        };
    }
}
