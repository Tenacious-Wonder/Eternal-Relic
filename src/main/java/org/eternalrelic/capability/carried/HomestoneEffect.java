package org.eternalrelic.capability.carried;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/**
 * 「记下归途」能力：拿着归乡石右键，它记下脚下这一处；再右键一次，人立刻回到那里。
 *
 * <p><b>它是本模组第一件「把位置存起来、之后再兑现」的遗物。</b>此前与地点有关的只有蜡封手账，
 * 但它只<b>报方位与距离</b>，人还得自己走过去；归乡石则是把这件事一次做完。</p>
 *
 * <p><b>位置与冷却都记在石头自己身上</b>（物品数据），与蜡封手账、拾荒口袋、装备附件同一条道理：
 * 丢进箱子、交给队友、带过维度都跟着走。因此这块石头可以交给别人用，
 * 而它记住的是<b>按下时的那个地方</b>——不是某个玩家的私人档案。
 * 玩家身上带着两块时，右键用的就是手上那一块，各记各的、各冷各的。</p>
 *
 * <p><b>跨维度一律拒绝</b>：记下的地点在别的维度时，右键只回一句「那一页在另一个维度」，
 * 人留在原地、记录也不清空（下次回到那个维度仍旧能用）。这与蜡封手账对跨维度的说法一致。
 * 之所以不让人跨维度回去：那等于凭空多出一条随时抽身的退路，而穿过传送门本来就该是件有代价的事。</p>
 *
 * <p><b>每次传送要付一个经验等级</b>（经验条上那个数字减一，当前进度条保留，与原版附魔扣级
 * 是同一套算法）。等级不足 1 时符文亮不起来：人不走、记下的那一处也留着，攒够再来。
 * 之所以收这道路费：归乡石是「随时抽身」的东西，若一毫不付，它会把所有长途跋涉都变成
 * 一次右键——那正是制作者要避免的。</p>
 *
 * <p><b>回去之后记录就清空</b>：再右键一次便重新记下当下站的地方——
 * 因此不存在「想重新记一处、却先被传送回去」这种事。</p>
 */
public final class HomestoneEffect {

    /** 记下地点所用的四个读取键：三轴坐标与它所在的维度。 */
    private static final String HOME_X_KEY = "HomeX";
    private static final String HOME_Y_KEY = "HomeY";
    private static final String HOME_Z_KEY = "HomeZ";
    private static final String HOME_DIMENSION_KEY = "HomeDimension";

    /** 冷却结束时刻在物品数据里的键名（世界总刻数）。 */
    private static final String COOLDOWN_KEY = "CooldownEnd";

    /** 回去之后的冷却。3600 刻 = 3 分钟。 */
    private static final int COOLDOWN_TICKS = 3600;

    /**
     * 每次传送要付出的经验等级数。
     *
     * <p>扣的是<b>等级</b>（经验条上那个数字），当前进度条保留——与原版附魔台、铁砧
     * 消耗等级是同一套算法（{@code PlayerEntity#addExperienceLevels} 传负数）。</p>
     */
    private static final int EXPERIENCE_COST_LEVELS = 1;

    private HomestoneEffect() {
    }

    /**
     * 用一次归乡石的结果 —— 由物品类据此回一句相应的话。
     *
     * <p>把「成没成、为什么没成」摊成几种结果，而不是只回一个成不成的真假：
     * 这件遗物失败时有两种完全不同的说辞（还在冷却、那一页在别的维度），
     * 用一句话笼统带过，玩家只会反复右键猜原因。</p>
     */
    public enum Result {

        /** 记下了当下站的地方。 */
        RECORDED,

        /** 人已经回到记下的那处。 */
        RETURNED,

        /** 还在冷却里。 */
        COOLING,

        /** 记下的那处不在此刻所在的维度。 */
        OTHER_DIMENSION,

        /** 没有经验等级可以当路费，这一次传送做不成。 */
        NO_EXPERIENCE
    }

    /**
     * 使用一次归乡石。
     *
     * <p>第一次右键只是记录，第二次才传送。冷却从<b>传送成功</b>那一刻起算，
     * 记录与冷却都写在石头自己身上，因此换一块石头并不会把另一块的冷却带过来。</p>
     *
     * @param player 右键的玩家（只可能是服务端那一位）
     * @param stone  手上那一块归乡石 —— 记录与冷却都写进它
     * @return 这一次右键的结果
     */
    public static Result use(ServerPlayerEntity player, ItemStack stone) {
        ServerWorld world = player.getServerWorld();
        long now = world.getTime();

        NbtCompound nbt = stone.getOrCreateNbt();

        if (now < nbt.getLong(COOLDOWN_KEY)) {
            return Result.COOLING;
        }

        if (!nbt.contains(HOME_X_KEY)) {
            remember(player, nbt);
            return Result.RECORDED;
        }

        // 记下的那处在别的维度：不传送、也不清记录，只是说一声
        if (!nbt.getString(HOME_DIMENSION_KEY).equals(world.getRegistryKey().getValue().toString())) {
            return Result.OTHER_DIMENSION;
        }

        // 传送要拿一个经验等级当路费。等级不够时符文亮不起来：人不走，记下的那一处也留着，
        // 等攒够等级再来右键一次即可
        if (player.experienceLevel < EXPERIENCE_COST_LEVELS) {
            return Result.NO_EXPERIENCE;
        }

        Vec3d home = new Vec3d(nbt.getDouble(HOME_X_KEY), nbt.getDouble(HOME_Y_KEY), nbt.getDouble(HOME_Z_KEY));

        // 传送到记下的那一处：朝向不动（人保持自己面对的方向），这也是「回去」该有的样子
        player.teleport(world, home.x, home.y, home.z, player.getYaw(), player.getPitch());

        // 路费在这里收：传送确实成了才扣，人没走成的时候一个等级也不动
        player.addExperienceLevels(-EXPERIENCE_COST_LEVELS);

        // 回去的那一下用原版末影人的传送声：玩家一听就知道「成了」，不必再去读聊天栏
        world.playSound(null, home.x, home.y, home.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                SoundCategory.PLAYERS, 1.0F, 1.0F);

        // 落地即清空这一页记录，免得一右键再右键反复传送；随后进入三分钟冷却。
        // 清的是四个坐标键，冷却键留着——它不是记录的一部分
        nbt.remove(HOME_X_KEY);
        nbt.remove(HOME_Y_KEY);
        nbt.remove(HOME_Z_KEY);
        nbt.remove(HOME_DIMENSION_KEY);
        nbt.putLong(COOLDOWN_KEY, now + COOLDOWN_TICKS);

        return Result.RETURNED;
    }

    /**
     * 把玩家此刻站的位置写进石头。
     *
     * @param player 右键的玩家
     * @param nbt    这块石头的物品数据
     */
    private static void remember(ServerPlayerEntity player, NbtCompound nbt) {
        nbt.putDouble(HOME_X_KEY, player.getX());
        nbt.putDouble(HOME_Y_KEY, player.getY());
        nbt.putDouble(HOME_Z_KEY, player.getZ());
        nbt.putString(HOME_DIMENSION_KEY, player.getServerWorld().getRegistryKey().getValue().toString());

        // 记下时给一声清脆的响（紫水晶那一声）：与「回去」的传送声分得开，
        // 玩家不看提示也知道这一次是记下了、不是回去了
        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0F, 1.0F);
    }
}
