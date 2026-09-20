package org.eternalrelic.client.screen;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.slot.Slot;

import org.eternalrelic.client.mixin.HandledScreenAccessor;
import org.eternalrelic.registry.ModRelics;
import org.eternalrelic.relic.RelicDefinition;
import org.lwjgl.glfw.GLFW;

/**
 * 遗物界面的入口：把鼠标停在任意容器界面里的遗物上，按一下 Shift（或 V）即可打开。
 *
 * <p><b>认的是「带槽位的容器界面」这一大类，而不是某几个具体界面</b>：只要界面里有格子、
 * 鼠标能压在一件物品上，这件事就有得可做 —— 背包、创造模式物品栏、箱子、熔炉、工作台、
 * 装卸台……它们共同继承自 {@link HandledScreen}（原版把"容器界面"的共性与
 * "鼠标此刻压着哪一格"的记录都放在这个父类里），认它就一次覆盖全部。</p>
 *
 * <p>早先只认背包那一支（{@code AbstractInventoryScreen}），换个界面看遗物就毫无反应；
 * 而"鼠标压着的是哪一格"本来就只有 {@link HandledScreen} 知道，收窄到背包没有换来任何好处。</p>
 *
 * <p><b>逐刻读按键状态而不是接按键事件</b>：背包界面自己重写了按键处理，实测按 Shift
 * 收不到事件回调。逐刻读一次状态不依赖任何事件链，谁覆盖了谁都不影响；靠前后两刻的差别
 * 识别出「刚按下」那一下，因此按住不放不会反复打开。</p>
 *
 * <p>除 Shift 之外还认一个 V 键：中文输入法会吞掉 Shift 的按下，V 不受影响。</p>
 *
 * <p><b>界面上有输入框正在收字时一概不触发</b>：这两个键读的都是最原始的键盘状态，不看输入焦点，
 * 因此带搜索框或改名框的界面（创造模式物品栏、铁砧……）里打字时会误触发，详见
 * {@link #checkTrigger(MinecraftClient)}。</p>
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
     * <p><b>有输入框正在收字时不响应</b>：这里读的是最原始的键盘状态，不知道此刻谁拿着输入焦点。
     * 于是带输入框的界面会出事——创造模式物品栏的搜索框、铁砧的改名框，在里面打一个含 v 的词，
     * 而鼠标又恰好悬停在过滤出来的遗物格上时，会当场弹出遗物界面，把这一下按键连字一起吞掉。
     * 输入框自己知道有没有焦点，问它最准。判断交由 {@link #isTypingInTextField(Screen)}。</p>
     *
     * @param client 客户端实例
     */
    private static void checkTrigger(MinecraftClient client) {
        boolean triggerDown = Screen.hasShiftDown()
                || InputUtil.isKeyPressed(client.getWindow().getHandle(), FALLBACK_KEY);

        boolean justPressed = triggerDown && !triggerWasDown;
        triggerWasDown = triggerDown;

        if (!(client.currentScreen instanceof HandledScreen<?> container) || !justPressed) {
            return;
        }

        if (isTypingInTextField(container)) {
            return;
        }

        RelicDefinition relic = relicUnderMouse(container);
        if (relic == null) {
            return;
        }

        client.setScreen(new RelicScreen(relic, container));
    }

    /**
     * 判断这个界面上是不是有输入框正在收字。
     *
     * <p><b>为什么是逐个翻子控件，而不是只看界面记录的焦点</b>：本类改用的 {@code Screen#children()}
     * 会把界面上挂着的控件都列出来，输入框有没有焦点由它自己回答（{@code TextFieldWidget#isFocused()}）。
     * 只看界面自己记账的那个焦点（{@code getFocused()}）会漏——创造模式的搜索框是切到搜索那一栏时
     * 直接给自己 {@code setFocused(true)} 的，没有经过界面那一层的记账，那边看到的仍是空的。</p>
     *
     * @param screen 正在查看的界面
     * @return 有输入框正拿着焦点时返回 {@code true}
     */
    private static boolean isTypingInTextField(Screen screen) {
        for (Element child : screen.children()) {
            if (child instanceof TextFieldWidget field && field.isFocused()) {
                return true;
            }
        }

        return false;
    }

    /**
     * 找出鼠标此刻停在哪一格。
     *
     * <p>只认原版自己记录的那一格（画格子高亮、弹物品提示框用的正是它）。原版每帧都会重算它，
     * 算的时候已经带上了创造模式物品栏滚动之后的位移，所以它永远是准的。</p>
     *
     * <p>早先这里还留了一手「拿不到就自己按面板坐标减 16 像素网格去比对」的兜底，那是多余的：
     * 原版算不出来的时候，自己算只会更不准——滚动过的格子在面板坐标系里早就不在原来的位置上了，
     * 于是可能指到旁边一格，甚至指到一件鼠标根本没压着的遗物。兜底只可能制造误判，索性删掉。</p>
     *
     * @param screen 正在查看的界面
     * @return 鼠标底下的那一格；没指着任何格子时返回 {@code null}
     */
    private static Slot slotUnderMouse(HandledScreen<?> screen) {
        return ((HandledScreenAccessor) screen).getFocusedSlot();
    }

    /**
     * 判断鼠标底下的那一格是不是遗物。
     *
     * @param screen 正在查看的界面
     * @return 该遗物的定义；指着空格、非遗物或没指着任何格子时返回 {@code null}
     */
    private static RelicDefinition relicUnderMouse(HandledScreen<?> screen) {
        Slot slot = slotUnderMouse(screen);

        if (slot == null || !slot.hasStack()) {
            return null;
        }

        return ModRelics.definitionOf(slot.getStack().getItem());
    }
}
