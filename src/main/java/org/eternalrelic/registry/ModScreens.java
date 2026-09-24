package org.eternalrelic.registry;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;

import org.eternalrelic.EternalRelic;
import org.eternalrelic.screen.ChestplateStationScreenHandler;
import org.eternalrelic.screen.RelicStationScreenHandler;

/**
 * 本模组的界面容器注册入口。
 *
 * <p>界面容器是"客户端与服务端各摆一遍同样的格子"，因此这里注册的是一份**排布方式**，
 * 服务端另把内容同步过去。</p>
 *
 * <p><b>胸甲台用的是「扩展」那一款</b>：它需要在打开界面时额外把一个方块位置发给客户端——
 * 客户端要凭这个位置取出台上的那件胸甲，才能把六个配件槽里的东西画出来。
 * 遗物装卸台不需要（它什么都不存），所以那边用原版那一款就够了。</p>
 */
public final class ModScreens {

    /** 遗物装卸台的界面容器类型。 */
    public static final ScreenHandlerType<RelicStationScreenHandler> RELIC_STATION =
            Registry.register(Registries.SCREEN_HANDLER,
                    EternalRelic.id("relic_station"),
                    new ScreenHandlerType<>(RelicStationScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    /**
     * 胸甲台的界面容器类型。
     *
     * <p>打开时下发的是台子的方块位置，客户端据此取出方块实体。</p>
     */
    public static final ExtendedScreenHandlerType<ChestplateStationScreenHandler> CHESTPLATE_STATION =
            Registry.register(Registries.SCREEN_HANDLER,
                    EternalRelic.id("chestplate_station"),
                    new ExtendedScreenHandlerType<>((syncId, playerInventory, buf) ->
                            new ChestplateStationScreenHandler(syncId, playerInventory, buf.readBlockPos())));

    private ModScreens() {
    }

    /**
     * 由 {@link EternalRelic#onInitialize()} 调用，触发本类静态字段初始化并完成注册。
     */
    public static void register() {
    }
}
