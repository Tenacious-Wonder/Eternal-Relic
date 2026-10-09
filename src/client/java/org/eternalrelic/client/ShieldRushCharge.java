package org.eternalrelic.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;

import org.eternalrelic.network.ShieldRushNetwork;

/**
 * 「破阵」的蓄力：在客户端盯着<b>左键</b>，举着盾按住它就开始蓄，松手就报给服务端。
 *
 * <p><b>为什么能借用左键</b>：原版在「正在使用物品」时会把左键整个屏蔽掉
 * （{@code MinecraftClient} 里那句 {@code !player.isUsingItem()}），
 * 因此举着盾按住左键<b>本来是空的一下</b> —— 拿它当蓄力键，既不会误伤也不会挖掉方块，
 * 更不必去拦原版的任何行为。</p>
 *
 * <p><b>为什么蓄力途中还要一直报</b>：服务端只知道"他报了什么"，不知道玩家此刻按着没有。
 * 每两刻报一次「还在蓄、蓄到第几刻」，服务端才能把聚风的粒子与声音做出来。</p>
 *
 * <p><b>两个提前退出的口子</b>：中途<b>松开右键</b>（把盾放下）就取消，不冲出去；
 * 人没了、世界没了也会清干净，免得下次上线还留着上一次的蓄力。</p>
 *
 * <p>客户端只管"我蓄了多久"，<b>能不解能冲、冲多远、撞多疼全由服务端定</b>
 * （见 {@code skill/ShieldRushEffect}）。</p>
 */
public final class ShieldRushCharge {

    /**
     * 蓄力最多数到多少刻。
     *
     * <p>服务端 <b>30 刻（1.5 秒）</b>就蓄满了，这里留一半余量只为把"已经蓄满"的表现做足，
     * 同时也防着玩家一直按着不放、数字无限长下去。</p>
     */
    private static final int MAX_CHARGE_TICKS = 45;

    /** 每几刻向服务端报一次。2 刻 = 0.1 秒，足够把聚风做得连贯。 */
    private static final int REPORT_INTERVAL_TICKS = 2;

    /** 已经蓄了几刻。 */
    private static int chargeTicks;

    /** 此刻是不是正在蓄力。 */
    private static boolean charging;

    private ShieldRushCharge() {
    }

    /**
     * 由客户端入口调用，挂上逐刻读左键的回调。
     */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                reset();
                return;
            }

            boolean shieldUp = client.player.isUsingItem()
                    && client.player.getActiveItem().isOf(Items.SHIELD);
            boolean attackHeld = client.options.attackKey.isPressed();

            // 蓄到一半把盾放下了：这一次作废，不冲
            if (charging && !shieldUp) {
                reset();
                return;
            }

            if (shieldUp && attackHeld) {
                if (!charging) {
                    charging = true;
                    chargeTicks = 0;
                }

                if (chargeTicks < MAX_CHARGE_TICKS) {
                    chargeTicks++;
                }

                if (chargeTicks % REPORT_INTERVAL_TICKS == 0) {
                    report(chargeTicks, false);
                }

                return;
            }

            // 松手（或放下盾）的那一下：这一次真的冲出去
            if (charging) {
                report(chargeTicks, true);
                reset();
            }
        });
    }

    /**
     * 把「蓄了多久、松没松手」报给服务端。
     *
     * @param ticks    已蓄力的刻数
     * @param released 是否已经松手
     */
    private static void report(int ticks, boolean released) {
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeInt(ticks);
        buffer.writeBoolean(released);

        ClientPlayNetworking.send(ShieldRushNetwork.RUSH_PACKET, buffer);
    }

    /** 把蓄力状态清干净。 */
    private static void reset() {
        charging = false;
        chargeTicks = 0;
    }
}
