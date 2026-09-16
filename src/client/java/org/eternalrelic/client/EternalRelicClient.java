package org.eternalrelic.client;

import net.fabricmc.api.ClientModInitializer;

import org.eternalrelic.client.network.SoulFlameClientNetwork;
import org.eternalrelic.client.particle.ModParticleFactories;
import org.eternalrelic.client.screen.RelicScreenOpener;

/**
 * 客户端入口：只处理画面相关的内容。
 */
public class EternalRelicClient implements ClientModInitializer {

    /**
     * 由游戏在客户端加载阶段调用一次，登记本模组的画面相关内容。
     */
    @Override
    public void onInitializeClient() {
        ModParticleFactories.register();
        RelicScreenOpener.register();
        SoulLanternKey.register();
        SoulFlameClientNetwork.register();
        MaterialTooltip.register();
    }
}
