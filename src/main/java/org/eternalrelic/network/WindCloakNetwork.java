package org.eternalrelic.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;

import org.eternalrelic.EternalRelic;
import org.eternalrelic.capability.attached.WindCloakEffect;

/**
 * 风行披风二段跳时的客户端与服务端通信。
 *
 * <p>1.20.1 的跳跃不单独上报服务端，因此「他按了跳」这件事只有客户端知道；
 * 而位移必须由服务端下发才算数。所以这个包只做一件极小的事：
 * 把「我要再蹬一下」从客户端送到服务端，其余判断与出手全在
 * {@link WindCloakEffect#doubleJump} 里。</p>
 *
 * <p>包里没有任何数据——按下跳跃键时玩家是谁、站在哪儿，服务端自己都查得到。</p>
 */
public final class WindCloakNetwork {

    /** 二段跳请求的包名。客户端与服务端共用这一个标识。 */
    public static final Identifier JUMP_PACKET = EternalRelic.id("wind_cloak_jump");

    private WindCloakNetwork() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，挂上二段跳请求的处理。
     */
    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(JUMP_PACKET, (server, player, handler, buffer, sender) ->
                server.execute(() -> WindCloakEffect.doubleJump(player)));
    }
}
