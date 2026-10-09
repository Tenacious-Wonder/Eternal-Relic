package org.eternalrelic.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;

import org.eternalrelic.EternalRelic;
import org.eternalrelic.skill.ShieldRushEffect;

/**
 * 「破阵」冲刺时客户端与服务端之间的那条通路。
 *
 * <p><b>为什么要这条通路</b>：举盾时按住左键这件事只有客户端知道（原版根本不上报，
 * 而且举盾时那次左键在原版里什么也不做），而位移与伤害必须由服务端说了算。
 * 因此这里把「我蓄了多久」送过去，其余判断与出手全在 {@link ShieldRushEffect} 里。</p>
 *
 * <p><b>两类报告</b>：蓄力途中每两刻报一次（服务端据此撒聚风的粒子与声音），
 * 松手时再报一次带「放开了」的标记（那一次才真的冲出去）。</p>
 */
public final class ShieldRushNetwork {

    /** 冲刺报告的包名。客户端与服务端共用这一个标识。 */
    public static final Identifier RUSH_PACKET = EternalRelic.id("shield_rush");

    private ShieldRushNetwork() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，挂上冲刺报告的处理。
     *
     * <p>包里的两个数<b>先读出来再切到服务端线程</b>：缓冲区只在这次回调期间有效。</p>
     */
    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(RUSH_PACKET, (server, player, handler, buffer, sender) -> {
            int chargeTicks = buffer.readInt();
            boolean released = buffer.readBoolean();

            server.execute(() -> ShieldRushEffect.onClientReport(player, chargeTicks, released));
        });
    }
}
