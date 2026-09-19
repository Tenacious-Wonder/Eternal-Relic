package org.eternalrelic.registry;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;

import org.eternalrelic.EternalRelic;
import org.eternalrelic.screen.RelicStationScreenHandler;

/**
 * 本模组的界面容器注册入口。
 *
 * <p>目前只有一个「遗物装卸台」。界面容器是"客户端与服务端各摆一遍同样的格子"，
 * 因此这里注册的是一份**排布方式**，服务端另把内容同步过去。</p>
 */
public final class ModScreens {

    /** 遗物装卸台的界面容器类型。 */
    public static final ScreenHandlerType<RelicStationScreenHandler> RELIC_STATION =
            Registry.register(Registries.SCREEN_HANDLER,
                    EternalRelic.id("relic_station"),
                    new ScreenHandlerType<>(RelicStationScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    private ModScreens() {
    }

    /**
     * 由 {@link EternalRelic#onInitialize()} 调用，触发本类静态字段初始化并完成注册。
     */
    public static void register() {
    }
}
