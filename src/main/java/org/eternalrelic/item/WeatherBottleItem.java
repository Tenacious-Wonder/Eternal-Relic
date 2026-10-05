package org.eternalrelic.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.level.ServerWorldProperties;

/**
 * 气象瓶 —— 拿在手上右键，报出接下来的天气，以及大约还有多久变天。
 *
 * <p>原版的天气是<b>定时切换</b>的：世界自己记着「距离变天还有多少刻」，到点就翻面
 * （晴转雨、雨转晴），因此这件东西不是猜，而是把那个数读出来。它给的是世界级的预报：
 * 站在沙漠里的人也会听说「别处要下雨了」，所以额外问一句脚下的生物群系下不下雨，
 * 免得玩家以为瓶子坏了。</p>
 *
 * <p><b>三种情况它读不出来</b>，各自有一句话交代：下界与末地没有天气这一说；
 * 关掉「天气变化」这条游戏规则之后，那个计数器就停住了；脚下是不下雨的生物群系时，
 * 报「要下雨」纯属误导。</p>
 *
 * <p>没有冷却，也不消耗东西：它不改任何状态，反复读也不会让谁变强。</p>
 */
public class WeatherBottleItem extends Item {

    /**
     * 一分钟是多少刻（24000 刻 ÷ 20 分钟），用来把剩余时间折成分钟报给玩家。
     */
    private static final long TICKS_PER_MINUTE = 1200L;

    public WeatherBottleItem(Settings settings) {
        super(settings);
    }

    /**
     * 右键看一眼天气。
     *
     * <p>只由服务端发话：天气计时器与服务端的天气状态才是权威，客户端那一份可能还没同步到。</p>
     *
     * @param world 玩家所在的世界
     * @param user  看瓶子的玩家
     * @param hand  用的是哪只手
     * @return 使用结果；不消耗物品
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient()) {
            return TypedActionResult.success(stack);
        }

        // 下界与末地根本没有天气循环，那个计数器不存在
        if (!world.getDimension().hasSkyLight()) {
            report(user, "message.eternal_relic.weather_bottle.no_sky");
            return TypedActionResult.success(stack);
        }

        // 关掉「天气变化」之后，世界不再推进天气，读出来的数会永远停在原地
        if (!world.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE)) {
            report(user, "message.eternal_relic.weather_bottle.frozen");
            return TypedActionResult.success(stack);
        }

        if (!(world.getLevelProperties() instanceof ServerWorldProperties properties)) {
            report(user, "message.eternal_relic.weather_bottle.no_sky");
            return TypedActionResult.success(stack);
        }

        // 沙漠、恶地这类地方永远不下雨：世界在下雨，头顶上也没有一滴
        Biome.Precipitation precipitation = world.getBiome(user.getBlockPos()).value()
                .getPrecipitation(user.getBlockPos());
        if (precipitation == Biome.Precipitation.NONE) {
            report(user, "message.eternal_relic.weather_bottle.dry");
            return TypedActionResult.success(stack);
        }

        long minutes = (properties.getRainTime() + TICKS_PER_MINUTE - 1L) / TICKS_PER_MINUTE;

        String key;
        if (world.isThundering()) {
            key = "message.eternal_relic.weather_bottle.thunder";
        } else if (world.isRaining()) {
            key = "message.eternal_relic.weather_bottle.rain";
        } else {
            key = "message.eternal_relic.weather_bottle.clear";
        }

        user.sendMessage(Text.translatable(key,
                Text.literal(Long.toString(minutes)).formatted(Formatting.GOLD)), false);

        return TypedActionResult.success(stack);
    }

    /**
     * 直接把一句话发给玩家。
     *
     * @param user 收话的玩家
     * @param key  语言文件里的键
     */
    private static void report(PlayerEntity user, String key) {
        user.sendMessage(Text.translatable(key).formatted(Formatting.GRAY), false);
    }
}
