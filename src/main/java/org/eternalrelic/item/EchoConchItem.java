package org.eternalrelic.item;

import java.util.List;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import org.eternalrelic.util.Bearing;

/**
 * 回声螺壳 —— 拿在手上右键，听一听四周有什么。
 *
 * <p>它报的不是「有没有怪物」，而是<b>分类计数</b>：敌对、其它活物、玩家各有多少，
 * 外加最近那只敌对生物在哪个方向、离多远。玩家据此判断的是「这一带安不安全」，
 * 而不是「我能不能透视」——墙后面的东西它照样只报个数，不报位置。</p>
 *
 * <p><b>为什么不报最近的那一只动物</b>：牛与羊不会来咬人，把它们的方向也报出来只会让消息变长、
 * 让真正要紧的那条被淹掉。因此只有敌对生物才报方位。</p>
 *
 * <p>它带有 5 秒冷却：消息是发到聊天栏的，没有冷却的话按住右键就会刷屏。</p>
 */
public class EchoConchItem extends Item {

    /**
     * 能听见多远（格）。24 格略大于怪物的常见索敌距离，够玩家提前知道前面有东西。
     */
    private static final double RADIUS = 24.0D;

    /**
     * 两次回响之间至少隔多久。100 刻 = 5 秒，只为挡住按住右键刷屏。
     */
    private static final int COOLDOWN_TICKS = 100;

    public EchoConchItem(Settings settings) {
        super(settings);
    }

    /**
     * 右键听一次。
     *
     * <p>只由服务端发话：实体名单只有服务端手里是全的，客户端那边只有眼前看得到的那些。</p>
     *
     * @param world 玩家所在的世界
     * @param user  听螺壳的玩家
     * @param hand  用的是哪只手
     * @return 使用结果；不消耗物品
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient()) {
            return TypedActionResult.success(stack);
        }

        Box area = user.getBoundingBox().expand(RADIUS);
        List<LivingEntity> found = world.getEntitiesByClass(LivingEntity.class, area,
                other -> other.isAlive() && other != user);

        long hostiles = 0L;
        long players = 0L;
        LivingEntity nearestHostile = null;
        double nearestSquared = Double.MAX_VALUE;

        for (LivingEntity one : found) {
            if (one instanceof PlayerEntity) {
                players++;
                continue;
            }

            if (one instanceof Monster) {
                hostiles++;

                double squared = one.squaredDistanceTo(user);
                if (squared < nearestSquared) {
                    nearestSquared = squared;
                    nearestHostile = one;
                }
            }
        }

        // 除掉敌对与玩家之后剩下的都算「其它活物」：牛羊猪鸡、村民、傀儡都归这一栏
        long creatures = found.size() - hostiles - players;

        if (found.isEmpty()) {
            user.sendMessage(Text.translatable("message.eternal_relic.echo_conch.silent")
                    .formatted(Formatting.GRAY), false);
        } else {
            user.sendMessage(Text.translatable("message.eternal_relic.echo_conch.summary",
                    coloured(hostiles), coloured(creatures), coloured(players)), false);

            if (nearestHostile != null) {
                double offsetX = nearestHostile.getX() - user.getX();
                double offsetZ = nearestHostile.getZ() - user.getZ();
                long distance = Math.round(Math.sqrt(nearestSquared));

                user.sendMessage(Text.translatable("message.eternal_relic.echo_conch.nearest",
                        Bearing.of(offsetX, offsetZ).formatted(Formatting.RED),
                        coloured(distance)), false);
            }
        }

        // 用游戏的物品冷却去挡连点：图标上会出现一圈遮罩，冷却中右键完全没反应
        user.getItemCooldownManager().set(this, COOLDOWN_TICKS);

        return TypedActionResult.success(stack);
    }

    /**
     * 给数字上个色，好让玩家在聊天栏里一眼把数挑出来。
     *
     * @param value 要显示的数
     * @return 染成金色的数字文本
     */
    private static Text coloured(long value) {
        return Text.literal(Long.toString(value)).formatted(Formatting.GOLD);
    }
}
