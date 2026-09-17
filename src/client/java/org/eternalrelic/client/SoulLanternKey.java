package org.eternalrelic.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import org.eternalrelic.network.SoulLanternNetwork;
import org.lwjgl.glfw.GLFW;

/**
 * 引魂燃灯的释放按键。
 *
 * <p>默认 G 键，玩家可以在「选项 → 控制」里改——用原版的按键绑定系统注册，而不是自己读键盘，
 * 这样改键、冲突提示、以及按键分类都由游戏代管。</p>
 *
 * <p>按下时只发一个「释放」请求给服务端，实际效果在那边结算。</p>
 */
public final class SoulLanternKey {

    /** 按键绑定的标识，也是语言文件里显示名称的键。 */
    private static final String KEY_ID = "key.eternal_relic.cast_soul_lantern";

    /** 按键在设置界面里的分类。 */
    private static final String CATEGORY = "category.eternal_relic";

    private SoulLanternKey() {
    }

    /**
     * 由客户端入口调用，注册按键并挂上按下时的发送动作。
     */
    public static void register() {
        KeyBinding castKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                KEY_ID, InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 用 while 取出所有积压的按下事件，避免连按被合并掉
            while (castKey.wasPressed()) {
                ClientPlayNetworking.send(SoulLanternNetwork.CAST_PACKET, PacketByteBufs.empty());
            }
        });
    }
}
