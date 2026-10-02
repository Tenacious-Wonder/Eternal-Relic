package org.eternalrelic.client;

import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.nbt.NbtCompound;

import org.eternalrelic.EternalRelic;
import org.eternalrelic.item.DaiksonOxygenOrbItem;
import org.eternalrelic.registry.ModItems;

/**
 * 戴克森制氧球的两种样子：没启动时是那颗球，启动之后换成「展开」的那张，
 * 五分钟一到自己变回球。
 *
 * <p><b>状态记在物品自己身上，不另立名单</b>：启动时服务端往这件物品的数据里写一个
 * 「生效到什么时候」（世界时间），这里只是把那句话与当前时间比一比。
 * 因此：沙盒里 / 展示框里 / 别人手里的那颗球也各自显示自己的状态，
 * 退出重进、跨维度都不需要搬运，服务端也不必每刻去数谁还在用。</p>
 *
 * <p><b>为什么到点能自己变回来</b>：判定用的是「现在到点了没有」，而不是一个"用完了"的标志位。
 * 于是没有任何一处需要定时去清理——时间一过，下一次画它的时候就自然是球了。</p>
 *
 * <p>这是本模组第一件<b>会换样子</b>的物品。手感上它很重要：玩家扫一眼物品栏就知道
 * 自己还在不在那五分钟里，不必去看状态栏的图标。</p>
 */
public final class DaiksonOxygenOrbModel {

    private DaiksonOxygenOrbModel() {
    }

    /**
     * 由客户端入口调用，把「启动中」这件事接到模型的切换上。
     */
    public static void register() {
        ModelPredicateProviderRegistry.register(ModItems.DAIKSON_OXYGEN_ORB,
                EternalRelic.id("active"),
                (stack, world, entity, seed) -> {
                    NbtCompound nbt = stack.getNbt();
                    if (nbt == null || world == null || !nbt.contains(DaiksonOxygenOrbItem.ACTIVE_UNTIL_KEY)) {
                        return 0.0F;
                    }

                    return world.getTime() < nbt.getLong(DaiksonOxygenOrbItem.ACTIVE_UNTIL_KEY) ? 1.0F : 0.0F;
                });
    }
}
