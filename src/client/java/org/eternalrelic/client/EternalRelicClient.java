package org.eternalrelic.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.RenderLayer;

import org.eternalrelic.client.network.SoulFlameClientNetwork;
import org.eternalrelic.client.particle.ModParticleFactories;
import org.eternalrelic.client.render.FlatAndThreeDItemRenderer;
import org.eternalrelic.client.screen.RelicScreenOpener;
import org.eternalrelic.client.screen.RelicStationScreen;
import org.eternalrelic.registry.ModBlocks;
import org.eternalrelic.registry.ModScreens;

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
        SoulLanternGlow.register();
        SoulFlameClientNetwork.register();
        MaterialTooltip.register();

        // 装卸台的界面：把容器类型与画它的界面绑起来
        HandledScreens.register(ModScreens.RELIC_STATION, RelicStationScreen::new);

        // 台子的贴图里有镂空的地方（不属于它的像素是透明的），必须显式声明用「镂空」渲染层。
        // 方块默认那一层不接受透明度，会把透明像素画成黑色实心方块。
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.RELIC_STATION, RenderLayer.getCutout());

        // 引魂燃灯与两把锤子：物品栏显示制作者画的平面图标，手上与地上显示立体模型
        FlatAndThreeDItemRenderer.register();
    }
}
