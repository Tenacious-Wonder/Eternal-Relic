package org.eternalrelic.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import org.eternalrelic.EternalRelic;

/**
 * 魂火区域的听感同步（服务端这一半）：落下魂火时，告诉附近的客户端「这里在烧，还要烧多久」。
 *
 * <p><b>为什么需要这一趟通信</b>：魂火区域是服务端的事实（由它负责扣血），而那段低语声要在
 * 客户端播放。客户端无从得知一片魂火何时出现、何时熄灭，所以由服务端把位置与持续时间送过去。
 * 客户端收到之后怎么播、怎么淡出，见 {@code org.eternalrelic.client.network.SoulFlameClientNetwork}。</p>
 *
 * <p>只发给可听范围内的玩家：听不见的地方不必收到，也省得远处客户端白白挂着一个音效。</p>
 */
public final class SoulFlameNetwork {

    /** 魂火落成的包名，客户端与服务端共用这一个标识。 */
    public static final Identifier FLAME_PACKET =
            new Identifier(EternalRelic.MOD_ID, "soul_flame");

    /** 送到多远之外的玩家。取的可听距离更宽一些，好让走近时它已经在响。 */
    private static final double BROADCAST_RADIUS = 48.0D;

    private SoulFlameNetwork() {
    }

    /**
     * 服务端落下一片魂火时，通知附近的客户端开始播放低语。
     *
     * @param world         所在世界
     * @param center        魂火中心
     * @param durationTicks 这片魂火还会存在多少刻
     * @param radius        魂火的半径（格）。客户端据此决定随机呻吟撒在多宽的范围里
     */
    public static void broadcastFlame(ServerWorld world, Vec3d center, int durationTicks, double radius) {
        for (ServerPlayerEntity player : PlayerLookup.around(world, center, BROADCAST_RADIUS)) {
            // 每个玩家一份独立的缓冲：写好的包发出去就归对方所有了，不能重复使用
            PacketByteBuf buffer = PacketByteBufs.create();
            buffer.writeDouble(center.x);
            buffer.writeDouble(center.y);
            buffer.writeDouble(center.z);
            buffer.writeInt(durationTicks);
            buffer.writeDouble(radius);

            ServerPlayNetworking.send(player, FLAME_PACKET, buffer);
        }
    }
}
