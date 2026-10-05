package org.eternalrelic.item;

import java.util.Locale;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * 旧怀表 —— 拿在手上右键，报出此刻的钟点与「距天黑 / 天亮还有多久」。
 *
 * <p>它和磕碰的罗盘是同一路数：自己什么都不改，只把玩家本来要抬头看天、或者在心里估摸的东西
 * 说成一个数。区别在于罗盘报的是「你在哪」，怀表报的是「什么时候了」——
 * 对靠太阳纹章与月亮纹章吃饭的人来说，后者比前者更要紧。</p>
 *
 * <p><b>时间一律按钟点算，不看天色</b>：原版判断白天黑夜用的是世界的亮度
 * （{@code World#isDay}），于是雷暴的正午会被算成夜晚。怀表报的是「几点」，
 * 所以这里按世界时间折算，阴天下雨也照常走针——钟表本来就该这样。
 * 也因此它报的「白天 / 夜晚」与两枚时段纹章在雷暴天可能对不上，那是原版对「白天」的定义所致，
 * 不是表走错了。</p>
 *
 * <p><b>下界与末地的表是停的</b>：那两处没有昼夜循环（{@code DimensionType#hasFixedTime}），
 * 报出来只会是一串没有意义的数字，因此在那里它只说一句「这里不走字」。</p>
 *
 * <p>没有冷却，也不消耗东西：它不改任何状态，反复看也不会让谁变强。</p>
 */
public class PocketWatchItem extends Item {

    /**
     * 一天的刻数。原版一天正好 24000 刻，折合现实 20 分钟。
     */
    private static final long DAY_TICKS = 24000L;

    /**
     * 日落那一刻的刻数：0 刻是日出、12000 刻是日落，两者各占半天。
     */
    private static final long DUSK_TICKS = 12000L;

    /**
     * 一游戏小时是多少刻（24000 刻 ÷ 24 小时）。
     */
    private static final long TICKS_PER_HOUR = 1000L;

    /**
     * 一分钟是多少刻（24000 刻 ÷ 20 分钟），用来把剩余时间折成分钟报给玩家。
     */
    private static final long TICKS_PER_MINUTE = 1200L;

    /**
     * 游戏里的 0 刻对应钟表上的 6:00 —— 世界是从日出开始算的。
     */
    private static final long CLOCK_OFFSET_HOURS = 6L;

    public PocketWatchItem(Settings settings) {
        super(settings);
    }

    /**
     * 右键看一次表。
     *
     * <p>只由服务端发话：客户端那边也会跑一遍这个方法，两边都报就会刷出两条一模一样的消息。</p>
     *
     * @param world 玩家所在的世界
     * @param user  看表的玩家
     * @param hand  用的是哪只手
     * @return 使用结果；不消耗物品
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient()) {
            return TypedActionResult.success(stack);
        }

        if (world.getDimension().hasFixedTime()) {
            user.sendMessage(Text.translatable("message.eternal_relic.pocket_watch.timeless")
                    .formatted(Formatting.GRAY), false);
            return TypedActionResult.success(stack);
        }

        long timeOfDay = world.getTimeOfDay();

        // 世界时间是「从开档起一共过了多少刻」，因此取余得到当天第几刻、整除得到第几天
        long withinDay = Math.floorMod(timeOfDay, DAY_TICKS);
        long day = Math.floorDiv(timeOfDay, DAY_TICKS) + 1;
        boolean daytime = withinDay < DUSK_TICKS;

        user.sendMessage(Text.translatable(
                daytime ? "message.eternal_relic.pocket_watch.day" : "message.eternal_relic.pocket_watch.night",
                day,
                Text.literal(clockOf(withinDay)).formatted(Formatting.GOLD),
                minutesUntilChange(withinDay, daytime)), false);

        return TypedActionResult.success(stack);
    }

    /**
     * 把「当天第几刻」折成钟表上的时刻。
     *
     * @param withinDay 当天已经过去的刻数（0~23999）
     * @return 形如 {@code 07:30} 的时刻文字
     */
    private static String clockOf(long withinDay) {
        long totalMinutes = withinDay * 60L / TICKS_PER_HOUR;
        long hour = (totalMinutes / 60L + CLOCK_OFFSET_HOURS) % 24L;
        long minute = totalMinutes % 60L;

        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }

    /**
     * 距下一次天亮（或天黑）还有多少分钟。
     *
     * <p>向上取整：还差几秒就说「还有一分钟」，比说「还有零分钟」有用。</p>
     *
     * @param withinDay 当天已经过去的刻数
     * @param daytime   此刻是不是白天
     * @return 剩余分钟数
     */
    private static long minutesUntilChange(long withinDay, boolean daytime) {
        long remainingTicks = daytime ? DUSK_TICKS - withinDay : DAY_TICKS - withinDay;
        return (remainingTicks + TICKS_PER_MINUTE - 1L) / TICKS_PER_MINUTE;
    }
}
