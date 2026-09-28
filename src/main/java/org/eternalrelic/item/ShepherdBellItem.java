package org.eternalrelic.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import org.eternalrelic.capability.carried.ShepherdBellEffect;

/**
 * 牧羊人铃铛 —— 拿在手上右键就能摇响的遗物。
 *
 * <p>它和奥塔的枝叶那种「放在背包里自动生效」的遗物不同，必须由玩家主动摇一次；
 * 也不需要像守夜之瞳那样付出代价——摇铃只是让周围的羊朝你走过来，摇完铃铛还在手上。
 * 真正的效果由 {@link ShepherdBellEffect} 结算。</p>
 *
 * <p><b>它自己不消耗</b>：摇铃不扣物品、也不掉耐久；唯一的门槛是那 30 秒冷却，
 * 而冷却是交给游戏的「物品冷却」去计的——铃铛图标上会出现一圈逐渐消退的遮罩，
 * 冷却没走完时右键不会有任何反应。</p>
 *
 * <p>名称、品阶与效果说明由语言文件和遗物界面承担，提示框里只留一句「按左 Shift 详细查看」，
 * 因此本类只管「右键摇铃」这一件事。</p>
 */
public class ShepherdBellItem extends Item {

    public ShepherdBellItem(Settings settings) {
        super(settings);
    }

    /**
     * 玩家拿在手上右键时摇响铃铛。
     *
     * <p>真正生效的部分只在服务端执行；客户端提前返回只是为了走完使用流程——若两边都去招呼羊，
     * 客户端招呼的那一遍随后会被服务端的结果覆盖，等于白算。</p>
     *
     * <p><b>返回的是 {@code consume}，不是 {@code success}。</b>这两档都表示「这一下确实用出去了」
     * （{@code isAccepted()} 都为真），差别只在<b>要不要把手臂往前挥一下</b>：原版
     * {@code ActionResult.SUCCESS} 会挥手，{@code CONSUME} 不会（见
     * {@code ActionResult.shouldSwingHand()}）。摇铃本身已经有动作了，再叠一个「往前挥」会像在戳空气，
     * 因此取不挥手的那一档——这也是原版为「执行了、但不该有动画」专门留的档位。</p>
     *
     * <p><b>冷却是游戏替我们拦的</b>：铃铛进了冷却之后，客户端与服务端都会在门口把这次右键拦下
     * （与末影珍珠、紫颂果走的是同一条路子），压根到不了本方法，因此这里不必判"还能不能摇"。</p>
     *
     * @param world 玩家所在的世界
     * @param user  右键的玩家
     * @param hand  使用的是哪只手
     * @return 使用结果；不挥手
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient) {
            return TypedActionResult.consume(stack);
        }

        ShepherdBellEffect.ring(user);
        return TypedActionResult.consume(stack);
    }
}
