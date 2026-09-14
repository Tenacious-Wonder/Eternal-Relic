package org.eternalrelic.capability.worn;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.world.RaycastContext;

import org.eternalrelic.relic.NightwatchEye;

/**
 * 守夜之瞳的视觉：装入眼睛之后，在暗处看清周围、照见活物。
 *
 * <p>左眼给出夜视，右眼给玩家看得见的活物描边，两者共用一个前提——<b>玩家所处位置够暗</b>。
 * 亮度用游戏里「玩家感知到的亮度」来判断：火把一类的方块光与天光取较亮者，天光又已按昼夜
 * 扣减过，因此白天的户外不算暗处，夜晚的户外与洞穴里都算。</p>
 *
 * <h2>夜视为什么要一次给足 20 秒</h2>
 * <p>游戏对夜视有一条硬性分界：<b>剩余时间低于 200 刻（10 秒）时，画面亮度会按正弦波来回摆动，
 * 屏幕上的效果图标也开始闪</b>（见 {@code GameRenderer#getNightVisionStrength} 与
 * {@code InGameHud} 的效果图标绘制）。一旦跌到这条线以下，玩家看到的就是一闪一闪的夜视。</p>
 *
 * <p>因此这里一次给 {@link #NIGHT_VISION_TICKS 400 刻}，并每
 * {@link #NIGHT_VISION_REFRESH_TICKS 80 刻}续一次：续期之间最多消耗 80 刻，剩余时间始终在
 * 320 刻以上，离 200 刻的分界线留有充裕余量，即便服务器短暂卡顿也不会跌破。
 * <b>调短这个数值会直接导致夜视闪烁，不要再改小。</b></p>
 *
 * <p>由于一次给的时长较长，走出暗处时必须主动收回，否则画面会白亮十几二十秒。收回只针对
 * 「本能力亲手挂上去的那一份」——玩家自己喝的夜视药水记在
 * {@link #NIGHT_VISION_GRANTED} 之外，不会被误撤。</p>
 */
public final class NightwatchEyeVision {

    /** 每隔多少刻核对一次亮度与周围的活物。10 刻约为 0.5 秒，走动时察觉不到延迟。 */
    private static final int CHECK_INTERVAL_TICKS = 10;

    /** 每隔多少刻给夜视续一次时长。80 刻为 4 秒，续期之间玩家察觉不到。 */
    private static final int NIGHT_VISION_REFRESH_TICKS = 80;

    /**
     * 一次给足的夜视时长，400 刻为 20 秒。
     *
     * <p>必须显著高于 200 刻这条分界线，理由见类文档。与续期间隔相减后仍需留在 200 刻以上。</p>
     */
    private static final int NIGHT_VISION_TICKS = 400;

    /** 判定为「暗处」的亮度上限：所处位置亮度低于此值时，守夜之瞳才开始起作用。 */
    private static final int DARK_LIGHT_LEVEL = 7;

    /** 每次刷新时给描边预留的持续刻数。略长于核对间隔，免得描边忽明忽暗。 */
    private static final int OUTLINE_TICKS = 20;

    /** 右眼能照见的距离（米）。 */
    private static final double SIGHT_RANGE = 30.0D;

    /** 身上那份夜视是由本能力挂上去的玩家编号。用于走出暗处时只收回自己给的那一份。 */
    private static final Set<UUID> NIGHT_VISION_GRANTED = new HashSet<>();

    private NightwatchEyeVision() {
    }

    /**
     * 由 {@link org.eternalrelic.EternalRelic#onInitialize()} 调用，挂上逐刻核对视觉的回调。
     */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % CHECK_INTERVAL_TICKS != 0) {
                return;
            }

            // 夜视的续期比核对稀疏：核对间隔能整除续期间隔，两者不会错开
            boolean refreshNightVision = server.getTicks() % NIGHT_VISION_REFRESH_TICKS == 0;

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                applyFor(player, refreshNightVision);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                NIGHT_VISION_GRANTED.remove(handler.getPlayer().getUuid()));
    }

    /**
     * 按玩家装入的眼睛与所处亮度，决定他这一刻该看到什么。
     *
     * @param player            目标玩家
     * @param refreshNightVision 本轮是否轮到给夜视续期
     */
    private static void applyFor(ServerPlayerEntity player, boolean refreshNightVision) {
        // 亮度这一轮只查一次：两只眼共用同一个"这里够不够暗"的判断
        boolean inDark = isInDark(player);

        if (inDark && WornRelicEffect.isWorn(player, NightwatchEye.LEFT)) {
            keepNightVision(player, refreshNightVision);
        } else {
            // 不在暗处、或已经没戴左眼：把夜视收回来
            dropNightVision(player);
        }

        if (inDark && WornRelicEffect.isWorn(player, NightwatchEye.RIGHT)) {
            outlineVisibleCreatures(player);
        }
    }

    /**
     * 判断玩家此刻是否身处暗处。
     *
     * @param player 目标玩家
     * @return 所处位置的亮度低于阈值时返回 {@code true}
     */
    private static boolean isInDark(ServerPlayerEntity player) {
        return player.getWorld().getLightLevel(player.getBlockPos()) < DARK_LIGHT_LEVEL;
    }

    /**
     * 让玩家保持夜视。
     *
     * <p>效果只在初次进入暗处与轮到续期时才重新挂上，其余时刻放着不动——反复改写会让客户端
     * 反复收到效果更新，画面跟着一跳一跳。</p>
     *
     * <p>若玩家自己已经拥有更久的夜视（例如刚喝过夜视药水），这里不去碰它，同时把「这份是我
     * 给的」这笔记录划掉，免得之后走出暗处时误把人家的药水效果撤掉。</p>
     *
     * @param player             目标玩家
     * @param refreshNightVision 本轮是否轮到续期
     */
    private static void keepNightVision(ServerPlayerEntity player, boolean refreshNightVision) {
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (current != null && current.getDuration() > NIGHT_VISION_TICKS) {
            NIGHT_VISION_GRANTED.remove(player.getUuid());
            return;
        }

        NIGHT_VISION_GRANTED.add(player.getUuid());

        if (current == null || refreshNightVision) {
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.NIGHT_VISION, NIGHT_VISION_TICKS, 0, false, false, true));
        }
    }

    /**
     * 收回本能力挂上去的那份夜视。
     *
     * <p>只认 {@link #NIGHT_VISION_GRANTED} 里记着的人，因此玩家自己喝的夜视药水不受影响。</p>
     *
     * @param player 目标玩家
     */
    private static void dropNightVision(ServerPlayerEntity player) {
        if (NIGHT_VISION_GRANTED.remove(player.getUuid())) {
            player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
    }

    /**
     * 给玩家看得见的活物描边。
     *
     * <p>游戏原生的「发光」效果是能穿墙看见的，而需求要的是「不能透视」。所以这里先判断玩家与
     * 目标之间有没有方块挡着，只给真正看得见的那些描边——躲在墙后的活物不会亮起来。</p>
     *
     * @param player 目标玩家
     */
    private static void outlineVisibleCreatures(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Box range = player.getBoundingBox().expand(SIGHT_RANGE);

        for (LivingEntity creature : world.getEntitiesByClass(LivingEntity.class, range, entity -> entity != player)) {
            if (player.squaredDistanceTo(creature) > SIGHT_RANGE * SIGHT_RANGE) {
                continue;
            }

            if (!hasLineOfSight(player, creature)) {
                continue;
            }

            markGlowing(creature);
        }
    }

    /**
     * 判断玩家与目标之间是否没有方块遮挡。
     *
     * @param player   观察者
     * @param creature 目标活物
     * @return 从玩家眼睛到目标眼睛的射线没有撞到方块时返回 {@code true}
     */
    private static boolean hasLineOfSight(ServerPlayerEntity player, LivingEntity creature) {
        BlockHitResult hit = player.getWorld().raycast(new RaycastContext(
                player.getEyePos(),
                creature.getEyePos(),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player));

        return hit.getType() == HitResult.Type.MISS;
    }

    /**
     * 给一只活物挂上描边。
     *
     * <p>描边只看「有没有这个效果」，不像夜视那样受剩余时间影响，因此给短时长、勤刷新即可，
     * 既不会闪，离开视野后也能很快褪掉。图标一栏关掉，免得被照到的玩家屏幕上多出一个闪动的图标。</p>
     *
     * <p>与夜视同理：若它本身已经带着更久的发光（例如被光灵箭射中），不去打扰。</p>
     *
     * @param creature 目标活物
     */
    private static void markGlowing(LivingEntity creature) {
        StatusEffectInstance current = creature.getStatusEffect(StatusEffects.GLOWING);
        if (current != null && current.getDuration() > OUTLINE_TICKS) {
            return;
        }

        creature.addStatusEffect(new StatusEffectInstance(
                StatusEffects.GLOWING, OUTLINE_TICKS, 0, false, false, false));
    }
}
