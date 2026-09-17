package org.eternalrelic.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import org.eternalrelic.capability.worn.WornRelicEffect;
import org.eternalrelic.relic.NightwatchEye;

/**
 * 守夜之瞳 —— 拿在手上右键就能装进眼窝的遗物。
 *
 * <p>它和奥塔的枝叶的生效方式不同：枝叶放在背包里就自动生效，而守夜之瞳必须由玩家主动
 * 「装上去」。装入后物品从手上消失，效果转为在低光环境下被动生效，
 * 具体由 {@link WornRelicEffect} 负责。</p>
 *
 * <p>能量耗尽的守夜之瞳（装入后被取下、尚未充能的那只）不能再次装入：拿着它右键不会有任何
 * 反应，需要先在工作台里与附魔之瓶合成，恢复成可用的那一只。</p>
 *
 * <p>名称、品阶与效果说明由语言文件和遗物界面承担，提示框里只留一句「按左 Shift 详细查看」，
 * 因此本类只管「右键装入」这一件事。</p>
 */
public class NightwatchEyeItem extends Item {

    /** 这颗眼珠属于左眼还是右眼。 */
    private final NightwatchEye eye;

    /** 能量是否已经耗尽；耗尽时不能装入。 */
    private final boolean drained;

    public NightwatchEyeItem(Settings settings, NightwatchEye eye, boolean drained) {
        super(settings);
        this.eye = eye;
        this.drained = drained;
    }

    /**
     * 玩家拿着它右键时，把它装进对应的眼窝。
     *
     * <p>真正生效的部分只在服务端执行。客户端只负责播放挥手动作——若两边都改属性，
     * 客户端算出来的数值随后会被服务端覆盖，等于白算一次。</p>
     *
     * @param world 玩家所在的世界
     * @param user  右键的玩家
     * @param hand  使用的是哪只手
     * @return 使用结果；装不进去时返回失败，游戏便不会消耗这件物品
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (this.drained) {
            return TypedActionResult.fail(stack);
        }

        if (world.isClient) {
            return TypedActionResult.success(stack);
        }

        if (!WornRelicEffect.wear(user, this.eye)) {
            return TypedActionResult.fail(stack);
        }

        stack.decrement(1);
        return TypedActionResult.success(stack);
    }
}
