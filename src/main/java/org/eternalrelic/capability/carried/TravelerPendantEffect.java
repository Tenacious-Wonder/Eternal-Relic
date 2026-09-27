package org.eternalrelic.capability.carried;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import org.eternalrelic.registry.ModItems;

/**
 * 「骑乘加速」能力：带着旅人吊坠时，玩家骑的那只坐骑跑得更快。
 *
 * <p><b>它与既有各类能力最大的不同，是「好处给了谁」。</b>此前所有遗物要么把好处给玩家自己，
 * 要么给钉住的那件物品，这一件给的是<b>玩家正骑着的那只坐骑</b>——骑乘时移动速度由坐骑自己的
 * 移动速度属性决定，与骑手的移动速度毫无关系，所以加在玩家身上是一点用都没有的。</p>
 *
 * <p><b>加速用的是「临时」属性加成，而不是会存档的那种。</b>两者的区别只在实体被写进存档时：
 * 临时加成不写。这样一来，玩家骑着马直接退出游戏、服务器被强杀，马身上都不会留下一条洗不掉的
 * 加速——重新进游戏时它又是一匹普通的马。若换成会存档的那种，就得专门去追查「哪几匹马被谁
 * 加速过」，漏一处就永久留下一匹跑得莫名其妙的马。</p>
 *
 * <p>即便如此，「下马」仍然要显式把加成摘掉：临时加成不写存档，但<b>只要那匹马还在世上</b>
 * （同一局游戏里被卸载又加载、或者被别人骑走），它就一直带着。因此这里记着
 * 「这名玩家上一次给哪只坐骑挂过」，一旦换了坐骑或下了马，立刻把旧的那只摘干净。</p>
 *
 * <p><b>带多枚不叠加</b>：只问「带没带」，不问带了几枚。</p>
 */
public final class TravelerPendantEffect {

    /** 每隔多少刻核对一次骑乘状态。5 刻约为 0.25 秒，与 {@link CarriedRelicEffect} 同一个节奏。 */
    private static final int CHECK_INTERVAL_TICKS = 5;

    /** 坐骑移动速度的提升比例。{@code 0.15} 表示「在最终速度上再快一成半」。 */
    private static final double SPEED_BONUS = 0.15D;

    /**
     * 这条加速在坐骑身上的固定标识。
     *
     * <p>标识必须固定：核对是反复进行的，靠它才能认出「这条加速是不是我们挂的那一条」，
     * 从而做到挂一次就够、不会每 5 刻叠一层。</p>
     */
    private static final UUID MODIFIER_ID =
            UUID.nameUUIDFromBytes("eternal_relic:traveler_pendant/mount_speed".getBytes(StandardCharsets.UTF_8));

    /** 这条加速在坐骑属性面板里显示的名字。 */
    private static final String MODIFIER_NAME = "eternal_relic:traveler_pendant";

    /** 记录每位玩家当前加速着的坐骑：玩家编号 → 那只坐骑。用来在换乘或下马时把旧的摘掉。 */
    private static final Map<UUID, LivingEntity> BOOSTED_MOUNTS = new HashMap<>();

    private TravelerPendantEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，挂上核对骑乘状态的回调。
     */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % CHECK_INTERVAL_TICKS != 0) {
                return;
            }

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                applyFor(player);
            }
        });

        // 玩家带着一只加速着的坐骑掉线时，那只坐骑会留在世上继续跑 —— 在这里摘干净。
        // （就算这一步没赶上，加速也不会进存档：见类文档里「临时加成」那一段。）
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                releaseMountOf(handler.getPlayer()));
    }

    /**
     * 核对一名玩家的骑乘状态，把坐骑身上的加速调整到与当前情况一致。
     *
     * <p>两种情况都要处理：<b>换乘或下马</b>时把上一只坐骑摘干净；<b>正骑着且带着吊坠</b>时
     * 给当前这只挂上（已经挂着就什么都不做）。</p>
     *
     * @param player 目标玩家
     */
    private static void applyFor(ServerPlayerEntity player) {
        LivingEntity mount = player.getVehicle() instanceof LivingEntity living ? living : null;

        // 玩家不算坐骑：正常玩法里骑不上别人，只有 /ride 这类指令才做得到。
        boolean shouldBoost = mount != null && !(mount instanceof PlayerEntity)
                && CarriedStacks.inEffect(player, ModItems.TRAVELER_PENDANT);
        LivingEntity previous = BOOSTED_MOUNTS.get(player.getUuid());

        // 三种情况都要把上一只摘干净：换了坐骑（previous != mount）、下了马（此时 mount 为 null）、
        // 以及**还骑着、但这份加成已经不该给**（遗物被收进箱子或转手给了别人）。
        // 最后那一种最容易漏：坐骑没换、人还在马上，只是遗物不在了 —— 漏掉的话那匹马会一直快下去。
        if (previous != null && (previous != mount || !shouldBoost)) {
            releaseModifier(previous);
            BOOSTED_MOUNTS.remove(player.getUuid());
        }

        if (shouldBoost) {
            applyModifier(mount);
            BOOSTED_MOUNTS.put(player.getUuid(), mount);
        }
    }

    /**
     * 摘掉一名玩家留下的加速，并忘掉这只坐骑。
     *
     * @param player 目标玩家
     */
    private static void releaseMountOf(ServerPlayerEntity player) {
        LivingEntity mount = BOOSTED_MOUNTS.remove(player.getUuid());
        if (mount != null) {
            releaseModifier(mount);
        }
    }

    /**
     * 给一只坐骑挂上加速。已经挂着时什么都不做——反复挂会在属性面板上堆出一长串同样的记录。
     *
     * @param mount 目标坐骑
     */
    private static void applyModifier(LivingEntity mount) {
        EntityAttributeInstance speed = mount.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed == null || speed.getModifier(MODIFIER_ID) != null) {
            return;
        }

        speed.addTemporaryModifier(new EntityAttributeModifier(
                MODIFIER_ID,
                MODIFIER_NAME,
                SPEED_BONUS,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    /**
     * 摘掉一只坐骑身上的加速。没挂过时什么都不会发生，因此可以放心调用。
     *
     * @param mount 目标坐骑
     */
    private static void releaseModifier(LivingEntity mount) {
        EntityAttributeInstance speed = mount.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(MODIFIER_ID);
        }
    }
}
