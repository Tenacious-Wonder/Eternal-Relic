package org.eternalrelic.capability.carried;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.Box;

import org.eternalrelic.registry.ModItems;
import org.eternalrelic.relic.PocketStorage;

/**
 * 「拾荒符石」能力：带着它时，身边三格内的掉落物会自己进到身上。
 *
 * <p><b>往哪儿进</b>：身上若带着一口<b>装着磁石的口袋</b>，东西就优先塞进那口口袋；
 * 没有这样的口袋（或者口袋满了）才照常进玩家自己的背包。这条「优先进口袋」的口径
 * 收在 {@link PocketStorage#insertIntoMagnetPocket} 里，磁石与「走过去捡」两条路都用同一份判断。</p>
 *
 * <p><b>只吸「本来就能捡的」</b>：刚丢出去的东西有一段拾取延迟（原版给 10~40 刻），
 * 那段时间里它不算数——否则玩家想把东西丢给别人时会被自己吸回来。
 * 同样地，别人丢下的东西若有主人，也照原版规矩不碰。</p>
 *
 * <p>每 5 刻扫一次（与核对背包同一节奏），开销等同于在玩家周围数一遍掉落物，
 * 没有磁石的人连这一遍都不做。</p>
 */
public final class ScavengerMagnetEffect {

    /** 每隔多少刻扫一次身边的掉落物。5 刻约为 0.25 秒。 */
    private static final int CHECK_INTERVAL_TICKS = 5;

    /** 能吸多远（格）。 */
    private static final double RADIUS = 3.0D;

    private ScavengerMagnetEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.EternalRelic#onInitialize()} 调用，挂上定期清扫的回调。
     */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % CHECK_INTERVAL_TICKS != 0) {
                return;
            }

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (CarriedStacks.inEffect(player, ModItems.SCAVENGER_MAGNET)) {
                    collectNearby(player);
                }
            }
        });
    }

    /**
     * 把身边能捡的掉落物收进身上。
     *
     * @param player 带着拾荒符石的玩家
     */
    private static void collectNearby(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Box box = player.getBoundingBox().expand(RADIUS);

        for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, box, entity -> canBeTaken(entity, player))) {
            ItemStack stack = item.getStack();
            if (stack.isEmpty()) {
                continue;
            }

            // 物品的类型要在收进去之前记下：收完之后 stack 可能已经空了，那时再问它是空气
            Item type = stack.getItem();
            int before = stack.getCount();

            PocketStorage.insertIntoMagnetPocket(player, stack);

            if (!stack.isEmpty()) {
                player.getInventory().insertStack(stack);
            }

            int taken = before - stack.getCount();
            if (taken <= 0) {
                continue;
            }

            // 照原版拾取那一套补上反馈：拾取动画与统计都算数，玩家看到的就是「捡起来了」
            player.sendPickup(item, taken);
            player.increaseStat(Stats.PICKED_UP.getOrCreateStat(type), taken);

            if (stack.isEmpty()) {
                item.discard();
            }
        }
    }

    /**
     * 这一份掉落物现在能不能被这名玩家捡起来。
     *
     * @param item   地上的掉落物
     * @param player 目标玩家
     * @return 是否可捡
     */
    private static boolean canBeTaken(ItemEntity item, ServerPlayerEntity player) {
        if (!item.isAlive() || item.cannotPickup()) {
            return false;
        }

        Entity owner = item.getOwner();
        return owner == null || owner.getUuid().equals(player.getUuid());
    }
}
