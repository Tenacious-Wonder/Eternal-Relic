package org.eternalrelic.item;

import java.util.List;

import net.minecraft.entity.mob.MobEntity;
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

/**
 * 回音石 —— 拿在手上右键，喊一声把附近的怪物引过来。
 *
 * <p>这是一件<b>双刃剑</b>：它不伤害任何东西，只是让周围的怪物当场认定「你在那里」。
 * 用法由玩家自己想——把怪从队友身边引开、把它们聚到一堆用一次范围攻击清掉、
 * 或者只是给自己找点麻烦。</p>
 *
 * <p><b>「吸引」是怎么做的</b>：给每只怪物设一个攻击目标（原版 {@code MobEntity#setTarget}），
 * 也就是让它们自己把你当成要追的人。这是原版 AI 本来就有的那一个开关，不必另写寻路，
 * 因此怪会照常绕开障碍、跳过栅栏，也不会隔着墙直线漂过来。</p>
 *
 * <p><b>只认敌对生物</b>：牛羊村民再近也不会理你。远处的怪（20 格以外）也听不见——
 * 那样它就从一件「引怪的石头」变成了一件「把半张地图的怪都叫来」的东西。</p>
 *
 * <p>它带有 20 秒冷却，交给游戏的物品冷却去计：否则一路按着右键，就会把沿途所有怪都拖着走。</p>
 */
public class EchoStoneItem extends Item {

    /**
     * 喊声传得到多远（格）。
     */
    private static final double RADIUS = 20.0D;

    /**
     * 两次喊声之间至少隔多久。400 刻 = 20 秒；交给游戏的物品冷却去计。
     */
    private static final int COOLDOWN_TICKS = 400;

    public EchoStoneItem(Settings settings) {
        super(settings);
    }

    /**
     * 右键喊一声。
     *
     * <p>只由服务端执行：怪物的攻击目标由服务端决定，客户端改了也会被立刻纠正回去。</p>
     *
     * @param world 玩家所在的世界
     * @param user  用石头的玩家
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

        // 只要敌对生物，而且得是能被人盯上的那种（MobEntity 才有「攻击目标」这一栏）
        List<MobEntity> mobs = world.getEntitiesByClass(MobEntity.class, area,
                mob -> mob.isAlive() && mob instanceof Monster);

        if (mobs.isEmpty()) {
            user.sendMessage(Text.translatable("message.eternal_relic.echo_stone.none")
                    .formatted(Formatting.GRAY), false);
            return TypedActionResult.success(stack);
        }

        for (MobEntity mob : mobs) {
            mob.setTarget(user);
        }

        user.sendMessage(Text.translatable("message.eternal_relic.echo_stone.called",
                Text.literal(Integer.toString(mobs.size())).formatted(Formatting.RED)), false);

        user.getItemCooldownManager().set(this, COOLDOWN_TICKS);

        return TypedActionResult.success(stack);
    }
}
