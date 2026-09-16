package org.eternalrelic.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

import org.eternalrelic.client.sound.SoulFlameSound;
import org.eternalrelic.network.SoulFlameNetwork;

/**
 * 魂火区域的听感同步（客户端这一半）：接到服务端的通知后开始播放低语。
 *
 * <p>服务端只说「这里有一片魂火、还要烧多少刻」，其余全由客户端自理：循环播放多久、
 * 什么时候开始淡出、音源摆在哪，都在 {@link SoulFlameSound} 里。</p>
 */
public final class SoulFlameClientNetwork {

    private SoulFlameClientNetwork() {
    }

    /**
     * 由客户端入口调用，接住服务端发来的魂火通知。
     */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SoulFlameNetwork.FLAME_PACKET, (client, handler, buffer, sender) -> {
            double x = buffer.readDouble();
            double y = buffer.readDouble();
            double z = buffer.readDouble();
            int durationTicks = buffer.readInt();
            double radius = buffer.readDouble();

            // 声音要在客户端线程上排进去
            client.execute(() -> MinecraftClient.getInstance().getSoundManager()
                    .play(new SoulFlameSound(x, y, z, durationTicks, radius)));
        });
    }
}
