package org.eternalrelic.capability.carried;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;

import org.eternalrelic.registry.ModItems;
import org.twcore.api.event.PlayerDamageEvent;
import org.twcore.api.event.TwEventPhases;

/**
 * 「损伤颠倒」能力：带着颠倒吊坠受伤时，偶尔会把这一下<b>反过来</b>——
 * 不掉血，反而回等量的血，代价是扣掉等量的经验点数。
 *
 * <p>它是「挨打反应」这一类里的新面孔：回响之环把伤害<b>挡下来</b>、荆棘之誓把伤害<b>还回去</b>、
 * 附魔兔脚只是<b>搭个顺风车</b>，而这一件是<b>把伤害反过来当治疗用</b>——
 * 唯一要付代价的一次，也是唯一会在受伤时"变好"的一次。</p>
 *
 * <h2>几条刻意的规矩</h2>
 * <ul>
 *   <li><b>算的是最终值、不是原始伤害</b>：伤害结算事件给出的数值是护甲、保护附魔与
 *       胸甲护具都算完之后，游戏真正会从血条上扣掉的那个数。所以它回的血刚好等于"本来会掉的那点血"，
 *       穿好甲的人省经验、裸着挨打的人费经验——而且费得更多，这正是这条代价该有的样子。</li>
 *   <li><b>扣的是经验点数，不是等级</b>：走的是原版 {@code addExperience}，因此等级与经验条
 *       由游戏自己折算，扣到掉级也照原版的规矩来，本模组不另算一份账。</li>
 *   <li><b>经验不够就发动失败</b>：这一下照常挨。也就是说它<b>不保证</b>救命，
 *       空着经验条挨打时身上带着它也是一块普通铜板。</li>
 *   <li><b>血满时不发动</b>（制作者未指定，这是本模组补的一条）：血已经满了还反转，
 *       等于白白扣掉一笔经验、什么都换不来，玩家只会觉得被坑。宁可这一次不发动，
 *       把机会留给真正掉血的时候。</li>
 * </ul>
 *
 * <p><b>不限定"被谁打的"</b>：中毒、岩浆、摔落、虚空一样可能被反过来——
 * 制作者要的是"每次受到伤害"，这里就照字面来。反正概率只有 5.5%，还得付经验。</p>
 */
public final class ReversalPendantEffect {

    /** 触发概率：5.5%。 */
    private static final float CHANCE = 0.055F;

    /** 反转成功时在玩家身上炸开的心形粒子数。 */
    private static final int PARTICLE_COUNT = 6;

    private ReversalPendantEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，接上伤害结算事件。
     *
     * <p>排在最后：它要把这一击整个接下来，应在其它挨打能力都表达过意见之后再定。</p>
     */
    public static void register() {
        PlayerDamageEvent.PLAYER_DAMAGE.register(TwEventPhases.LOWEST, ReversalPendantEffect::onDamage);
    }

    /**
     * 决定这一击要不要被颠倒过来。
     *
     * @param context       本次结算的只读信息
     * @param currentDamage 前序订阅方处理之后的伤害值
     * @return 反转成功时把这一击归零并终止后续订阅；否则放行
     */
    private static PlayerDamageEvent.Result onDamage(PlayerDamageEvent.Context context, float currentDamage) {
        return reverses(context.player(), currentDamage)
                ? PlayerDamageEvent.Result.cancel()
                : PlayerDamageEvent.Result.keep();
    }

    /**
     * 这一击要不要被颠倒过来。
     *
     * <p><b>会就地改玩家状态</b>（回血、扣经验、放粒子），因此只在真的要反转时调它一次；
     * 返回真时调用方应当把这一击的伤害<b>归零</b>。</p>
     *
     * @param player      挨打的玩家
     * @param finalDamage 玩家实际会承受的伤害（护甲、附魔与胸甲护具都算完之后）
     * @return 是否反转成功
     */
    public static boolean reverses(ServerPlayerEntity player, float finalDamage) {
        if (finalDamage <= 0.0F || player.isDead()) {
            return false;
        }

        if (!CarriedStacks.inEffect(player, ModItems.REVERSAL_PENDANT)) {
            return false;
        }

        // 血已经满了：换不来任何东西，这一下就别发动，也别白扣经验
        if (player.getHealth() >= player.getMaxHealth()) {
            return false;
        }

        if (player.getRandom().nextFloat() >= CHANCE) {
            return false;
        }

        // 经验按"点数"扣，向上取整——宁可多扣一点，不让它比回的血还便宜。
        // 1.20.1 的总经验点数是 PlayerEntity 上的公开字段（没有 getter），直接读它就是
        int cost = MathHelper.ceil(finalDamage);
        if (player.totalExperience < cost) {
            return false;
        }

        player.addExperience(-cost);
        player.heal(finalDamage);
        spawnReversalParticles(player);

        return true;
    }

    /**
     * 在玩家身上炸开几颗心 —— 让"这一下变成回血了"看得见。
     *
     * @param player 触发反转的玩家
     */
    private static void spawnReversalParticles(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        world.spawnParticles(ParticleTypes.HEART,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                PARTICLE_COUNT, 0.35D, 0.4D, 0.35D, 0.02D);
    }
}
