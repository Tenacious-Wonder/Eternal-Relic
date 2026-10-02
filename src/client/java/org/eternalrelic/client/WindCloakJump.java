package org.eternalrelic.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

import org.eternalrelic.network.WindCloakNetwork;

/**
 * 风行披风的二段跳：在客户端盯着<b>原版的跳跃键</b>，人在空中又按了一下就报告服务端。
 *
 * <p><b>为什么不注册自己的按键</b>：二段跳本来就该用玩家最顺手的那个键——他跳起来之后
 * 再按一下空格，这是所有游戏里二段跳的手感。若另注册一个键（比如 R），玩家得先学会"空中按 R"，
 * 而且跳跃键与它各按各的，操作会割裂。</p>
 *
 * <p><b>为什么自己记"上一刻按着没有"</b>：原版那个按键查询只回答「此刻按着没有」，
 * 玩家在空中按住不放时它会一直为真，于是每刻都发一个包。这里靠前后两刻的差别认出
 * 「刚按下」的那一下——也就是本项目处理按键的既有做法（与遗物界面读 Shift 同一套思路）。</p>
 *
 * <p>客户端只管"人在空中就报一声"，能不解能蹬、这次离地是不是已经用过，全由服务端定
 * （见 {@code capability.attached.WindCloakEffect}）。</p>
 */
public final class WindCloakJump {

    /** 上一刻跳跃键按着没有 —— 用来认出「刚按下」的那一下。 */
    private static boolean jumpHeld;

    /** 上一刻玩家是不是站在地上 —— 用来把「起跳的那一下」摘出去。 */
    private static boolean wasOnGround;

    private WindCloakJump() {
    }

    /**
     * 由客户端入口调用，挂上逐刻读跳跃键的回调。
     */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                jumpHeld = false;
                wasOnGround = false;
                return;
            }

            boolean pressed = client.options.jumpKey.isPressed();
            boolean justPressed = pressed && !jumpHeld;
            jumpHeld = pressed;

            boolean onGround = client.player.isOnGround();

            // ★ 「起跳的那一下」必须摘出去：原版在按下跳跃的同一刻就把人送离地面了，
            // 因此走到本回调（tick 末尾）时 isOnGround 已经是假。若不看上一刻站没站在地上，
            // 玩家每次起跳都会先花掉那唯一一次二段跳机会，真正在空中再按时反而没得用
            // ——这正是第一版「跳不起来」的原因。
            if (justPressed && !onGround && !wasOnGround) {
                ClientPlayNetworking.send(WindCloakNetwork.JUMP_PACKET, PacketByteBufs.empty());
            }

            wasOnGround = onGround;
        });
    }
}
