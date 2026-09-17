package org.eternalrelic.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import org.eternalrelic.EternalRelic;
import org.eternalrelic.capability.carried.SoulLanternEffect;

/**
 * 引魂燃灯释放时的客户端与服务端通信。
 *
 * <p>按键是在客户端按下的，但伤害必须由服务端结算——客户端算出来的结果会被服务器纠正回去，
 * 等于白算。因此客户端只把「我要放灯」这件事告诉服务端，真正扣魂火、造成伤害、留下魂火
 * 都在服务端完成。</p>
 *
 * <p>放不出来时（没带灯、一缕魂火都没有、还在冷却），服务端会回一条快捷栏提示，
 * 免得玩家一头雾水地反复按键。</p>
 */
public final class SoulLanternNetwork {

    /** 释放请求的包名。客户端与服务端共用这一个标识。 */
    public static final Identifier CAST_PACKET =
            new Identifier(EternalRelic.MOD_ID, "cast_soul_lantern");

    private SoulLanternNetwork() {
    }

    /**
     * 由 {@link EternalRelic#onInitialize()} 调用，挂上释放请求的处理。
     */
    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(CAST_PACKET, (server, player, handler, buffer, sender) ->
                server.execute(() -> {
                    if (SoulLanternEffect.cast(player)) {
                        return;
                    }

                    player.sendMessage(Text.translatable("message.eternal_relic.soul_lantern_unavailable"), true);
                }));
    }
}
