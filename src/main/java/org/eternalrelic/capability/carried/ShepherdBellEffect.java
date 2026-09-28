package org.eternalrelic.capability.carried;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Box;

import org.eternalrelic.registry.ModItems;
import org.eternalrelic.registry.ModSounds;

/**
 * 「唤羊」能力：摇响牧羊人铃铛之后，四周的羊会朝摇铃的人走过来。
 *
 * <p><b>它是本模组第一件「让别的生物自己动起来」的遗物。</b>此前所有遗物要么往玩家身上加东西
 * （属性、金心、状态效果），要么往物品上加东西（保全、补附魔），这一件改的是<b>羊的行走目标</b>：
 * 铃一响，范围内的羊就自己调头朝你走。</p>
 *
 * <p><b>怎么让羊过来</b>：不是把羊拽过来（那样会穿墙、会叠在一起），而是每半秒给每只羊
 * <b>重新下达一次「走向那名玩家」的寻路指令</b>。寻路是原版自己的本事，羊因此会正常绕开障碍、
 * 走上台阶、避开岩浆——玩家看到的是「羊自己过来」，而不是「羊被吸过来」。走到身边两格以内
 * 就不再催它，让它在脚边停下。</p>
 *
 * <p><b>计时记的是「结束时刻」而不是「还剩多久」</b>：世界时间一路向前，记结束时刻就不会因为
 * 跨维度、重登、服务器卡顿而算错（与引魂燃灯的冷却同一套做法）。</p>
 *
 * <p><b>冷却交给游戏的「物品冷却」</b>（与末影珍珠、紫颂果同一条路子）：摇响之后由游戏在铃铛
 * 图标上画出那圈逐渐消退的遮罩，冷却没走完时<b>右键完全不会有反应</b>——不挥手、不响铃、
 * 也不弹任何字。这样既省掉了自己的冷却表，玩家扫一眼图标就知道还要等多久，联机同步也由游戏代管。</p>
 *
 * <p><b>只有「吸引」这一张表活在服务器内存里</b>，下线即清空，是刻意的选择——铃声没有实体，
 * 玩家不在线时本来就没人被吸引，为它写进存档反而是负担。</p>
 */
public final class ShepherdBellEffect {

    /** 铃声传得到多远（格）。范围内的羊都会被招呼。 */
    private static final double RADIUS = 40.0D;

    /** 羊被吸引的时长。400 刻 = 20 秒。 */
    private static final int DURATION_TICKS = 400;

    /** 摇过之后铃铛冷多久。600 刻 = 30 秒；交给游戏的物品冷却去计。 */
    private static final int COOLDOWN_TICKS = 600;

    /**
     * 每隔多少刻重新给羊下达一次寻路指令。
     *
     * <p>10 刻 = 半秒。太稀会让羊走走停停，太密则纯属浪费——玩家半秒挪不出多远，
     * 羊自己的寻路也在照常推进。</p>
     */
    private static final int LURE_INTERVAL_TICKS = 10;

    /** 羊朝玩家走的速度倍数。1.0 就是它平时散步的速度，不必催它跑。 */
    private static final double LURE_SPEED = 1.0D;

    /** 羊走到这么近就不再催它（格）。免得它们挤在玩家身上推来推去。 */
    private static final double CLOSE_ENOUGH = 2.0D;

    /** 每位玩家「吸引持续到哪一刻」：玩家编号 → 结束时的世界刻数。 */
    private static final Map<UUID, Integer> LURING_UNTIL = new HashMap<>();

    private ShepherdBellEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，
     * 挂上「推进吸引」与「下线清理」两件事。
     */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int now = server.getTicks();

            // 先丢掉已经过期的那几条：表里只留还有效的，免得它随游戏时长无限长下去
            LURING_UNTIL.entrySet().removeIf(entry -> now >= entry.getValue());

            if (LURING_UNTIL.isEmpty() || now % LURE_INTERVAL_TICKS != 0) {
                return;
            }

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (LURING_UNTIL.containsKey(player.getUuid())) {
                    lureSheep(player);
                }
            }
        });

        // 玩家下线时把记录一并清掉：他再上线时，上一轮的铃声早该停了
        // （冷却不在这里管——它记在玩家自己的物品冷却上，由游戏带着走）
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                LURING_UNTIL.remove(handler.getPlayer().getUuid()));
    }

    /**
     * 摇一次铃，并让铃铛进入冷却。
     *
     * <p><b>冷却刻意不在这里判</b>：冷却期间游戏根本不会把这次右键送到这一层来
     * （客户端与服务端各自在门口就拦下了，见类文档），所以这里只管「摇响之后该做什么」。</p>
     *
     * @param player 摇铃的玩家
     */
    public static void ring(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        int now = serverPlayer.server.getTicks();
        LURING_UNTIL.put(player.getUuid(), now + DURATION_TICKS);

        // 进冷却：图标上随即出现一圈逐渐消退的遮罩，走完之前右键不会有任何反应——
        // 与末影珍珠、紫颂果是同一套，由游戏自己计时，连联机同步都不必我们操心
        player.getItemCooldownManager().set(ModItems.SHEPHERD_BELL, COOLDOWN_TICKS);

        // 铃声要让四周都听见——走世界广播，而不是只发给摇铃的人。
        // （服务端直接对玩家调 playSound 是不出声的，这一点踩过坑）
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.SHEPHERD_BELL, SoundCategory.PLAYERS, 1.0F, 1.0F);

        // 立刻招呼一次，不等下一个 10 刻——玩家按下右键就该当场看到羊回头
        lureSheep(serverPlayer);
    }

    /**
     * 招呼一名玩家四周的羊朝他走过来。
     *
     * <p>已经在身边两格以内的羊<b>跳过</b>：它的寻路本来就会把自己推到玩家身上，
     * 一群羊互相挤反而不好看。</p>
     *
     * @param player 摇铃的玩家
     */
    private static void lureSheep(ServerPlayerEntity player) {
        Box area = player.getBoundingBox().expand(RADIUS);
        List<SheepEntity> sheep = player.getWorld()
                .getEntitiesByClass(SheepEntity.class, area, SheepEntity::isAlive);

        for (SheepEntity one : sheep) {
            if (one.squaredDistanceTo(player) <= CLOSE_ENOUGH * CLOSE_ENOUGH) {
                continue;
            }

            // 让它自己走过去：原版的寻路会绕开墙与岩浆，比硬推着它走自然得多
            one.getNavigation().startMovingTo(player, LURE_SPEED);

            // 顺带把头转过来看着摇铃的人，观感上才像「被铃声叫住了」
            one.getLookControl().lookAt(player, 30.0F, 30.0F);
        }
    }
}
