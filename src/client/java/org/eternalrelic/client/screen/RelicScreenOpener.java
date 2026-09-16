package org.eternalrelic.client.screen;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.slot.Slot;

import org.eternalrelic.mixin.client.HandledScreenAccessor;
import org.eternalrelic.registry.ModRelics;
import org.eternalrelic.relic.RelicDefinition;
import org.lwjgl.glfw.GLFW;

/**
 * 遗物界面的入口：在背包里把鼠标停在遗物上，按一下 Shift（或 V）即可打开。
 *
 * <p><b>认的是「带界面的背包」这一大类，而不是某一个界面类</b>：生存模式的背包
 * （{@code InventoryScreen}）与创造模式的物品栏（{@code CreativeInventoryScreen}）
 * 是两个不同的类，界面模组还可能再包一层。它们共同继承自
 * {@link AbstractInventoryScreen}，认这个父类才能一次覆盖全部情况——
 * 写死其中任何一个，在另一种模式下都会毫无反应。</p>
 *
 * <p><b>逐刻读按键状态而不是接按键事件</b>：背包界面自己重写了按键处理，实测按 Shift
 * 收不到事件回调。逐刻读一次状态不依赖任何事件链，谁覆盖了谁都不影响；靠前后两刻的差别
 * 识别出「刚按下」那一下，因此按住不放不会反复打开。</p>
 *
 * <p>除 Shift 之外还认一个 V 键：中文输入法会吞掉 Shift 的按下，V 不受影响。</p>
 */
public final class RelicScreenOpener {

    /** 备用触发键。Shift 可能被输入法吃掉，留一个普通字母键兜底。 */
    private static final int FALLBACK_KEY = GLFW.GLFW_KEY_V;

    /** 上一刻触发键是否按着，用来识别「刚按下」的那一下。 */
    private static boolean triggerWasDown;

    private RelicScreenOpener() {
    }

    /**
     * 由客户端入口调用，挂上逐刻的检查。
     */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(RelicScreenOpener::checkTrigger);
    }

    /**
     * 每刻检查一次：若触发键刚刚被按下、鼠标又正停在一件遗物上，就打开它的界面。
     *
     * <p>只在「从松开变为按下」的那一刻动手，因此按住不放不会一路上弹窗；
     * 从遗物界面关回背包时同理，需要先松开再按一次才会重开。</p>
     *
     * @param client 客户端实例
     */
    private static void checkTrigger(MinecraftClient client) {
        boolean triggerDown = Screen.hasShiftDown()
                || InputUtil.isKeyPressed(client.getWindow().getHandle(), FALLBACK_KEY);

        boolean justPressed = triggerDown && !triggerWasDown;
        triggerWasDown = triggerDown;

        if (!(client.currentScreen instanceof AbstractInventoryScreen<?> inventory) || !justPressed) {
            return;
        }

        RelicDefinition relic = relicUnderMouse(client, inventory);
        if (relic == null) {
            return;
        }

        client.setScreen(new RelicScreen(relic, inventory));
    }

    /**
     * 找出鼠标此刻停在哪一格。
     *
     * <p>首选原版自己记录的悬停格子（画物品提示框用的正是它），拿不到时才退回按坐标推算：
     * 鼠标给的是屏幕坐标，而格子的坐标相对面板左上角，相减后逐个比对，格子边长固定 16 像素。</p>
     *
     * @param client 客户端实例，用于读取鼠标位置
     * @param screen 正在查看的界面
     * @return 鼠标底下的那一格；没指着任何格子时返回 {@code null}
     */
    private static Slot slotUnderMouse(MinecraftClient client, HandledScreen<?> screen) {
        HandledScreenAccessor accessor = (HandledScreenAccessor) screen;

        Slot focused = accessor.getFocusedSlot();
        if (focused != null) {
            return focused;
        }

        int relX = (int) client.mouse.getX() - accessor.getScreenX();
        int relY = (int) client.mouse.getY() - accessor.getScreenY();

        for (Slot slot : screen.getScreenHandler().slots) {
            if (relX >= slot.x && relX < slot.x + 16 && relY >= slot.y && relY < slot.y + 16) {
                return slot;
            }
        }

        return null;
    }

    /**
     * 判断鼠标底下的那一格是不是遗物。
     *
     * @param client 客户端实例
     * @param screen 正在查看的界面
     * @return 该遗物的定义；指着空格、非遗物或没指着任何格子时返回 {@code null}
     */
    private static RelicDefinition relicUnderMouse(MinecraftClient client, HandledScreen<?> screen) {
        Slot slot = slotUnderMouse(client, screen);

        if (slot == null || !slot.hasStack()) {
            return null;
        }

        return ModRelics.definitionOf(slot.getStack().getItem());
    }
}
