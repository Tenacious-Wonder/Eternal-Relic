package org.eternalrelic.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import org.eternalrelic.capability.consumed.RedClayWaxEffect;
import org.eternalrelic.registry.ModParticleTypes;

/**
 * 「红土蜡块」—— 拿在主手右键，把副手那件东西补回一段耐久的消耗品。
 *
 * <p><b>为什么修的是副手</b>：主手已经被蜡块自己占着了。要找一件「手上拿着、但不在使用中的
 * 位置」放待修的东西，副手是唯一的选择，而且玩家一看就懂：左手拿着要修的剑，右手拿着蜡块擦。</p>
 *
 * <p><b>修不成时什么都不发生。</b>副手空着、那件东西没有耐久、或者本来就是完好的，右键既不会
 * 消耗蜡块、也不会响、也不会挥手（返回 {@code fail}）。它是一次性的东西，白白扔掉一块
 * 比「按了没反应」更让人恼火。</p>
 *
 * <p><b>真的修成了才消耗</b>：扣物品这一步只在服务端做，客户端提前返回——两边都扣的话，
 * 客户端扣掉的那一个随后会被服务端的同步覆盖，等于白算（与牧羊人铃铛同一路写法）。</p>
 */
public class RedClayWaxItem extends Item {

    /** 涂蜡声的音量。 */
    private static final float WAX_VOLUME = 1.0F;

    /** 涂蜡声的音调。沿用原版蜜脾涂蜡的那一档，不加修饰。 */
    private static final float WAX_PITCH = 1.0F;

    /** 修补成功时溅出的蜡点颗数。刻意很少，只是给一个「确实用上了」的视觉交代。 */
    private static final int WAX_PARTICLE_COUNT = 6;

    /** 蜡点散开的范围（格）。贴着胸口那一小块，不往四周飞。 */
    private static final double WAX_PARTICLE_SPREAD = 0.3D;

    /** 蜡点出生高度在眼睛下方多少（格）：落到胸口位置，对得上手上的动作。 */
    private static final double WAX_PARTICLE_DROP = 0.3D;

    public RedClayWaxItem(Settings settings) {
        super(settings);
    }

    /**
     * 右键把蜡块揉开、抹在副手那件东西上。
     *
     * @param world 玩家所在的世界
     * @param user  右键的玩家
     * @param hand  使用的是哪只手（蜡块在主手）
     * @return 使用结果；副手没什么可修时返回 {@code fail}，不消耗也不挥手
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack held = user.getStackInHand(hand);
        ItemStack target = user.getOffHandStack();

        if (!RedClayWaxEffect.canRestore(target)) {
            return TypedActionResult.fail(held);
        }

        // 客户端只应一声，好让使用流程与挥手动画照常走；真正扣物品与补耐久都在服务端做。
        // 顺便：蜡点与声响只有服务端世界才发得出去，因此这一步也顺带把关
        if (!(world instanceof ServerWorld serverWorld)) {
            return TypedActionResult.success(held);
        }

        RedClayWaxEffect.restore(target);
        playWaxFeedback(serverWorld, user);
        held.decrement(1);

        return TypedActionResult.success(held);
    }

    /**
     * 播放涂抹的声响与蜡点。
     *
     * <p>用的是原版「蜜脾涂蜡」那一套——蜡块本就是往东西上抹的东西，借它现成的音色最贴切，
     * 也省得为一件消耗品再添一份音频资源。蜡点从胸口高度散开，位置对得上手上的动作。</p>
     *
     * @param world 玩家所在的服务端世界
     * @param user  使用蜡块的玩家
     */
    private void playWaxFeedback(ServerWorld world, PlayerEntity user) {
        world.playSound(null, user.getX(), user.getEyeY(), user.getZ(),
                SoundEvents.ITEM_HONEYCOMB_WAX_ON, SoundCategory.PLAYERS, WAX_VOLUME, WAX_PITCH);

        world.spawnParticles(ModParticleTypes.redWaxSpark(),
                user.getX(), user.getEyeY() - WAX_PARTICLE_DROP, user.getZ(),
                WAX_PARTICLE_COUNT, WAX_PARTICLE_SPREAD, WAX_PARTICLE_SPREAD * 0.7D,
                WAX_PARTICLE_SPREAD, 0.0D);
    }
}
