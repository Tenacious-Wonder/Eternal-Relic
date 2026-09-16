package org.eternalrelic.mixin.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 让外部读得到界面正在记录的「鼠标停在哪个格子」以及面板自身的位置。
 *
 * <p>原版靠这个字段决定给哪一格画高亮、弹物品提示框，因此它比外部自己按坐标推算更可靠——
 * 界面模组调整过布局时尤其如此。面板坐标则作为兜底方案使用。</p>
 */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {

    /**
     * @return 鼠标当前悬停的格子；没指着任何格子时为 {@code null}
     */
    @Accessor("focusedSlot")
    Slot getFocusedSlot();

    /**
     * @return 面板左上角在屏幕上的横坐标
     */
    @Accessor("x")
    int getScreenX();

    /**
     * @return 面板左上角在屏幕上的纵坐标
     */
    @Accessor("y")
    int getScreenY();
}
