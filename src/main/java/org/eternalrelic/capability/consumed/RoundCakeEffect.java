package org.eternalrelic.capability.consumed;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 「一口还乡」能力：吃下这块饼的人当场回到自己的重生点，并在十秒里飞快回血。
 *
 * <p><b>它是本模组第一件「吃下去才生效」的遗物</b>，与既有各类都不同：携带类、守护类、
 * 附着类都要求那件东西**还留在身上**，而这一件是<b>消耗品</b>——效果发生在它消失的那一刻，
 * 之后什么都不剩。因此它没有「携带生效」可言，触发点由
 * {@link org.eternalrelic.item.RoundCakeItem} 接住游戏给出的「吃完时」这一个时机。</p>
 *
 * <p><b>回的是玩家自己记录的重生点</b>（睡过的那张床），与游戏复活时用的是同一份数据；
 * 没睡过床的玩家退回世界的出生点。跨维度同样有效：在下界吃，人就回主世界。</p>
 */
public final class RoundCakeEffect {

    /** 回血持续多久：200 刻 = 10 秒。 */
    private static final int REGENERATION_TICKS = 200;

    /** 回血给到几级：原版从 0 数起，因此 4 表示第五级。 */
    private static final int REGENERATION_AMPLIFIER = 4;

    private RoundCakeEffect() {
    }

    /**
     * 把吃下饼的玩家送回重生点，并给一段快速回血。
     *
     * @param player 吃下饼的玩家
     */
    public static void bless(ServerPlayerEntity player) {
        sendHome(player);
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.REGENERATION, REGENERATION_TICKS, REGENERATION_AMPLIFIER));
    }

    /**
     * 把人送回复生点。
     *
     * <p>落点就是他当初登记的那一格，朝向沿用登记时的朝向；没登记过（没睡过床）则用
     * 世界的出生点。游戏自己的复活流程会再多找几个格子试试有没有被堵住，这里不做那一层
     * ——床被埋住时人可能会卡在方块里，这一点与直觉一致，也留给玩家自己处理。</p>
     *
     * @param player 要送回去的玩家
     */
    private static void sendHome(ServerPlayerEntity player) {
        ServerWorld world = spawnWorldOf(player);
        if (world == null) {
            return;
        }

        BlockPos spawnPos = player.getSpawnPointPosition();
        float angle;

        if (spawnPos == null) {
            spawnPos = world.getSpawnPos();
            angle = world.getSpawnAngle();
        } else {
            angle = player.getSpawnAngle();
        }

        player.teleport(world, spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, angle, 0.0F);
    }

    /**
     * 找出这名玩家的重生点在哪个世界。
     *
     * @param player 目标玩家
     * @return 重生点所在的世界；服务器或那个世界取不到时返回 {@code null}
     */
    private static ServerWorld spawnWorldOf(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return null;
        }

        RegistryKey<World> dimension = player.getSpawnPointDimension();
        return dimension == null ? server.getOverworld() : server.getWorld(dimension);
    }
}
