package org.eternalrelic.capability.carried;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.CamelEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

/**
 * 「唤马」能力：吹响唤马哨之后，把远处的马、驴、骡带到主人身边。
 *
 * <p><b>为什么要分两段走</b>：原版生物寻路的「视力」就是它自己的跟随范围
 * （{@code generic.follow_range}，马只有 16 格）。站在三百格外的马<b>根本找不到路</b>，
 * 会原地发呆——所以这里不能只下一条指令了事。做法是：</p>
 *
 * <ol>
 *   <li><b>先搬过来</b>：离主人超过 {@link #DROP_IN_DISTANCE} 格的，先传送到主人周围
 *       {@link #DROP_IN_MIN}~{@link #DROP_IN_MAX} 格的一处<b>安全落点</b>（地面上、上方有空、
 *       不在水里），这一段由传送完成，因此穿墙、过河、翻山都不成问题；</li>
 *   <li><b>再让它自己走</b>：落到十二格以内之后，寻路范围就够用了，每半秒给它下一次
 *       「走向主人」的指令，最后这一小段由它自己跑完——玩家看到的是马自己冲你跑过来，
 *       而不是被硬拽到脚下。</li>
 * </ol>
 *
 * <p><b>只认自己的坐骑</b>：马、驴、骡（同属 {@link AbstractHorseEntity}），而且必须是
 * <b>认你为主</b>且已驯服的；骆驼被特意排除在外（它不是「马」），队友的马也不会被叫走。</p>
 *
 * <p><b>一个绕不过去的限制</b>：玩家离马三百格时，那片区域通常已经被游戏卸载，
 * 而<b>没加载的区域里的马根本不会被计算</b>——任何代码都叫不动它。因此实际能招呼到的
 * 是仍处在加载范围内的那些（通常一百多格以内）。这一点无解，除非强行加载区块，代价太大。</p>
 *
 * <p><b>计时记的是「结束时刻」</b>而不是「还剩多久」，与牧羊人铃铛、引魂燃灯同一套做法：
 * 世界时间一路向前，记结束时刻就不会因为跨维度、重登、服务器卡顿而算错。</p>
 */
public final class HorseWhistleEffect {

    /** 哨声传得到多远（格）。范围内的坐骑都会被招呼。 */
    private static final double RADIUS = 300.0D;

    /** 离主人超过这个距离就先搬过来（格）。原版寻路范围是 16 格，这里留出余量。 */
    private static final double DROP_IN_DISTANCE = 12.0D;

    /** 搬到主人周围多远的圈上（格）。太近会跟主人叠在一起，太远又怕寻路够不着。 */
    private static final double DROP_IN_MIN = 8.0D;

    /** 同上，圈的外沿。 */
    private static final double DROP_IN_MAX = 12.0D;

    /** 搬过来之后催它走的时长。400 刻 = 20 秒。 */
    private static final int CALLING_TICKS = 400;

    /** 每隔多少刻重新下一次寻路指令。10 刻 = 半秒。 */
    private static final int CALL_INTERVAL_TICKS = 10;

    /** 走到这么近就不再催它（格）。 */
    private static final double CLOSE_ENOUGH = 3.0D;

    /** 朝主人走的快步。比散步快一点，像被哨声催着跑。 */
    private static final double WALK_SPEED = 1.2D;

    /** 找安全落点时最多试几个方向。 */
    private static final int LANDING_ATTEMPTS = 24;

    /** 找不到安全落点时的退路：坐骑摆在主人周围多远的圈上（格）。 */
    private static final double FALLBACK_RING_RADIUS = 1.6D;

    /** 每位主人「催马持续到哪一刻」：玩家编号 → 结束时的世界刻数。 */
    private static final Map<UUID, Integer> CALLING_UNTIL = new HashMap<>();

    private HorseWhistleEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，
     * 挂上「推进催马」与「下线清理」两件事。
     */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int now = server.getTicks();

            CALLING_UNTIL.entrySet().removeIf(entry -> now >= entry.getValue());

            if (CALLING_UNTIL.isEmpty() || now % CALL_INTERVAL_TICKS != 0) {
                return;
            }

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (CALLING_UNTIL.containsKey(player.getUuid())) {
                    urge(player);
                }
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                CALLING_UNTIL.remove(handler.getPlayer().getUuid()));
    }

    /**
     * 吹一次哨：把三百格内的坐骑搬近、再催它们走过来。
     *
     * @param player 吹哨的玩家
     * @return 被招呼到的坐骑数量；一头都没有时返回 0，物品也不进冷却
     */
    public static int whistle(ServerPlayerEntity player) {
        List<AbstractHorseEntity> horses = horsesNear(player, RADIUS);

        if (horses.isEmpty()) {
            return 0;
        }

        for (int i = 0; i < horses.size(); i++) {
            AbstractHorseEntity horse = horses.get(i);

            if (horse.squaredDistanceTo(player) <= DROP_IN_DISTANCE * DROP_IN_DISTANCE) {
                continue;
            }

            Optional<Vec3d> spot = landingSpot(player);
            Vec3d target = spot.isPresent() ? spot.get() : fallbackSpot(player, i, horses.size());

            horse.teleport(player.getServerWorld(),
                    target.x, target.y, target.z, Set.of(), horse.getYaw(), horse.getPitch());
        }

        CALLING_UNTIL.put(player.getUuid(), player.server.getTicks() + CALLING_TICKS);

        // 立刻催一次，不等下一个 10 刻——按下右键就该看到马朝这边跑
        urge(player);
        return horses.size();
    }

    /**
     * 催一次：让身边十二格以内的坐骑朝主人走过来（寻路交给原版自己算）。
     *
     * @param player 吹哨的玩家
     */
    private static void urge(ServerPlayerEntity player) {
        for (AbstractHorseEntity horse : horsesNear(player, DROP_IN_MAX + 4.0D)) {
            if (horse.squaredDistanceTo(player) <= CLOSE_ENOUGH * CLOSE_ENOUGH) {
                continue;
            }

            // 让它自己走：原版的寻路会绕开墙、上台阶、避开水与岩浆
            horse.getNavigation().startMovingTo(player, WALK_SPEED);

            // 顺带把头转过来，观感上才像「听到哨声回头了」
            horse.getLookControl().lookAt(player, 30.0F, 30.0F);
        }
    }

    /**
     * 找出玩家周围一定范围内的、认他为主的马、驴、骡。
     *
     * @param player 主人
     * @param radius 查找半径（格）
     * @return 范围内的坐骑
     */
    private static List<AbstractHorseEntity> horsesNear(ServerPlayerEntity player, double radius) {
        Box area = player.getBoundingBox().expand(radius);

        return player.getServerWorld().getEntitiesByClass(AbstractHorseEntity.class, area,
                horse -> horse.isAlive()
                        && !(horse instanceof CamelEntity)
                        && horse.isTame()
                        && player.getUuid().equals(horse.getOwnerUuid()));
    }

    /**
     * 在主人周围找一个能站马的落点。
     *
     * <p>挑的时候要求：脚下是实心地面（不是水、不是空气）、脚底那一格与它上面一格都是空气。
     * 这样马落下去不会卡在方块里、也不会掉进水里或岩浆里。</p>
     *
     * @param player 主人
     * @return 落点坐标；一圈都试过仍找不到时返回空（调用方会退而把马放到主人脚边）
     */
    private static Optional<Vec3d> landingSpot(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        for (int attempt = 0; attempt < LANDING_ATTEMPTS; attempt++) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = DROP_IN_MIN + player.getRandom().nextDouble() * (DROP_IN_MAX - DROP_IN_MIN);

            int x = MathHelper.floor(player.getX() + Math.cos(angle) * distance);
            int z = MathHelper.floor(player.getZ() + Math.sin(angle) * distance);
            int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);

            BlockPos feet = new BlockPos(x, top, z);
            BlockPos ground = feet.down();

            if (top <= world.getBottomY()) {
                continue;
            }

            if (!world.getFluidState(feet).isEmpty() || !world.getFluidState(ground).isEmpty()) {
                continue;
            }

            if (world.getBlockState(ground).isAir()) {
                continue;
            }

            if (!world.isAir(feet) || !world.isAir(feet.up())) {
                continue;
            }

            return Optional.of(new Vec3d(x + 0.5D, top, z + 0.5D));
        }

        return Optional.empty();
    }

    /**
     * 找不到安全落点时的退路：像驯兽哨那样，按人数把坐骑分角度摆在主人脚边一圈。
     *
     * <p>比落在主人身上好：十几匹马挤在同一格里会互相推挤。这一条只在「周围一圈全是水、
     * 全是墙」这类极端地形下才会用到。</p>
     *
     * @param player 主人
     * @param index  这是第几头坐骑（决定它摆在哪个角度上）
     * @param count  一共几头
     * @return 落点坐标
     */
    private static Vec3d fallbackSpot(ServerPlayerEntity player, int index, int count) {
        double angle = Math.PI * 2.0D * index / Math.max(1, count);

        return new Vec3d(player.getX() + Math.cos(angle) * FALLBACK_RING_RADIUS, player.getY(),
                player.getZ() + Math.sin(angle) * FALLBACK_RING_RADIUS);
    }
}
