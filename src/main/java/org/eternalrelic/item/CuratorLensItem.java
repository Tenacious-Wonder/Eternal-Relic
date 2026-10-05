package org.eternalrelic.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/**
 * 馆藏透镜 —— 举起来看的那一类物品，行为逐条照搬原版望远镜。
 *
 * <p><b>它为什么能白拿望远镜的全套表现</b>：原版望远镜不是一个独立的系统，而是由一条
 * 「使用方式」串起来的——物品把 {@link #getUseAction} 报成 {@link UseAction#SPYGLASS}，
 * 游戏便会自动替它办好四件事：按住右键持续使用、画上望远镜的边框遮罩、把手摆成举镜的姿势、
 * 以及把视野拉近。因此这里只要照抄原版望远镜的这几个方法，表现就一分不差地跟过来，
 * 不必自己写任何画面代码。</p>
 *
 * <p><b>与望远镜的两处差别</b>：一是放大倍率——原版是十倍，这里只要两倍，而倍率是由游戏
 * 客户端写死的，只能另打一处补丁去改（见 {@code AbstractClientPlayerEntityMixin}）；
 * 二是举镜时身边十三格内的箱子会浮起一枚淡黄光斑（见
 * {@link org.eternalrelic.capability.using.CuratorLensEffect}）。</p>
 *
 * <p>开关的另一半在 {@code PlayerEntityMixin}：游戏的「是否正在用望远镜」只认原版那件物品，
 * 那里补上一句「举着馆藏透镜也算」，上面的四件事才会真的生效。</p>
 */
public class CuratorLensItem extends Item {

    /** 一次举起最多持续多少刻。沿用原版望远镜的 1200 刻（60 秒），松手随时中断。 */
    private static final int MAX_USE_TIME = 1200;

    /** 举起与放下时那两记声响的音量。与原版望远镜一致。 */
    private static final float USE_SOUND_VOLUME = 1.0F;

    /** 举起与放下时那两记声响的音调。与原版望远镜一致，不额外压调或提调。 */
    private static final float USE_SOUND_PITCH = 1.0F;

    public CuratorLensItem(Settings settings) {
        super(settings);
    }

    /**
     * @return 一次举起最多持续多少刻
     */
    @Override
    public int getMaxUseTime(ItemStack stack) {
        return MAX_USE_TIME;
    }

    /**
     * @return 使用方式：望远镜。这是借用原版全套表现的那个开关。
     */
    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.SPYGLASS;
    }

    /**
     * 右键举起透镜：响一记举镜声，记一次使用统计，然后进入持续使用状态。
     *
     * @param world 使用者所在的世界
     * @param user  举起透镜的玩家
     * @param hand  用的哪只手
     * @return 消耗结果；与原版望远镜相同，物品本身不被消耗
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        user.playSound(SoundEvents.ITEM_SPYGLASS_USE, USE_SOUND_VOLUME, USE_SOUND_PITCH);
        user.incrementStat(Stats.USED.getOrCreateStat(this));
        return ItemUsage.consumeHeldItem(world, user, hand);
    }

    /**
     * 一直举到时间上限（极难发生，60 秒）时收手，响一记放下声。
     *
     * @param stack 举着的透镜
     * @param world 使用者所在的世界
     * @param user  举着透镜的玩家
     * @return 原样的物品堆，不消耗
     */
    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        this.playStopSound(user);
        return stack;
    }

    /**
     * 中途松手放下透镜时，响一记放下声。
     *
     * <p>这才是玩家真正结束观望的那条路径：绝大多数情况下他都是松开右键，而不是举满 60 秒，
     * 因此这个方法与 {@link #finishUsing} 各管一头，两处都要响。</p>
     *
     * @param stack              放下的透镜
     * @param world              使用者所在的世界
     * @param user               放下透镜的玩家
     * @param remainingUseTicks  剩余的使用刻数（本处用不到）
     */
    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        this.playStopSound(user);
    }

    /**
     * 播放放下透镜的声响。
     *
     * @param user 放下透镜的玩家
     */
    private void playStopSound(LivingEntity user) {
        user.playSound(SoundEvents.ITEM_SPYGLASS_STOP_USING, USE_SOUND_VOLUME, USE_SOUND_PITCH);
    }
}
