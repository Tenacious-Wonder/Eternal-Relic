package org.eternalrelic.capability.attached;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import org.eternalrelic.registry.ModItems;
import org.eternalrelic.relic.RelicAttachment;

/**
 * 「二段跳」能力：缝在胸甲上的风行披风，让玩家在空中还能再蹬一次。
 *
 * <p><b>只在缝着的时候管用。</b>它看的是玩家<b>正穿着的那件胸甲</b>上有没有附着这枚披风
 * （见 {@link RelicAttachment#isAttached}）：胸甲脱了、披风拆了、或者把它放在背包里，
 * 都蹬不起来——这与它在遗物表里登记的「只认附着份」是同一条口径。</p>
 *
 * <p><b>为什么判定与服务端各占一半</b>：1.20.1 的「跳跃」这件事<b>不单独上报服务端</b>
 * （骑乘跳跃除外），服务端手里的位置包只能看出「玩家离地了」，看不出「他按了跳」。
 * 因此按键由客户端读、只把「我要蹬一下」这一件事告诉服务端，而<b>能不能蹬、蹬多高、
 * 这次离地是不是已经用过了</b>全部由服务端说了算——客户端算出来的位移会被服务器纠正回去，
 * 等于白算（与引魂燃灯同一套分工）。</p>
 *
 * <p><b>一次离地只能用一次</b>：落地就重新计数（见 {@link #USED_SINCE_GROUND} 的清理）。
 * 这条记在服务器内存里、不做持久化——它本来就只活在一次跳跃的时间里，
 * 玩家下线、服务器重启都等于「重新站在地上」，没有需要搬运的东西。</p>
 *
 * <p><b>几种情况故意不接</b>：站在地上（那一下归原版）、在水里（原版那是上浮）、
 * 爬梯子、骑着东西、正在滑翔、以及创造模式飞行——这些场合「再蹬一下」要么与原版行为打架，
 * 要么根本说不通。</p>
 */
public final class WindCloakEffect {

    /**
     * 二段跳给的向上初速。
     *
     * <p>原版跳跃用的是 {@code 0.42}，约合 <b>1.25 格</b>高；制作者 2026-10-06 要求
     * 「蹬起来要有两格」，因此这里取 <b>{@code 0.53}</b>。</p>
     *
     * <p>这个数不是随手写的：**跳起的高度与初速的平方成正比**，
     * 于是 {@code 0.42 × √(2 ÷ 1.25) ≈ 0.53}。想再调时照这个式子算，别凭感觉加。</p>
     */
    private static final double JUMP_VELOCITY = 0.53D;

    /** 这次离地已经用过二段跳的玩家。落地即从这张表里移走。 */
    private static final Set<UUID> USED_SINCE_GROUND = new HashSet<>();

    /** 蹬起来时脚下炸开的粒子数量。 */
    private static final int JUMP_PARTICLE_COUNT = 10;

    private WindCloakEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，挂上「落地就重置」的核对。
     */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.isOnGround()) {
                    USED_SINCE_GROUND.remove(player.getUuid());
                }
            }
        });
    }

    /**
     * 受理一次「我要蹬一下」的请求。
     *
     * <p>由 {@link org.eternalrelic.network.WindCloakNetwork} 在服务端线程上调用。
     * 客户端的条件判断只是省几个包，真正的把关全在这里——客户端说自己在空中，服务端要自己看一眼。</p>
     *
     * @param player 请求二段跳的玩家
     */
    public static void doubleJump(ServerPlayerEntity player) {
        if (!wearsWindCloak(player)) {
            return;
        }

        // 还站在地上：那一下是原版的跳跃，不用我们插手
        if (player.isOnGround()) {
            return;
        }

        // 这些场合「再蹬一下」与原版行为打架，或者根本说不通
        if (player.isTouchingWater() || player.isClimbing() || player.hasVehicle()
                || player.isFallFlying() || player.getAbilities().flying) {
            return;
        }

        // 这次离地已经蹬过了（add 返回假说明表里本来就有）
        if (!USED_SINCE_GROUND.add(player.getUuid())) {
            return;
        }

        Vec3d velocity = player.getVelocity();
        player.setVelocity(velocity.x, JUMP_VELOCITY, velocity.z);
        player.velocityModified = true;

        // 服务端改速度不会自动同步给客户端，必须自己发这一包，否则玩家只能"凭空升起"
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));

        // 蹬这一下等于重新起跳：此前累计的下坠距离不再算数，
        // 否则从高处跳下来再蹬一下，落地照样摔掉一大截血
        player.fallDistance = 0.0F;

        spawnJumpParticles(player);
    }

    /**
     * 这名玩家此刻是否「正穿着一件缝了风行披风的胸甲」。
     *
     * @param player 目标玩家
     * @return 是否缝着
     */
    private static boolean wearsWindCloak(ServerPlayerEntity player) {
        ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
        return !chest.isEmpty() && RelicAttachment.isAttached(chest, ModItems.WIND_CLOAK);
    }

    /**
     * 在玩家脚下炸开一小圈白云 —— 让「踩了一下空气」这件事看得见。
     *
     * @param player 蹬起来的玩家
     */
    private static void spawnJumpParticles(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        world.spawnParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY(), player.getZ(),
                JUMP_PARTICLE_COUNT, 0.25D, 0.05D, 0.25D, 0.02D);
    }
}
