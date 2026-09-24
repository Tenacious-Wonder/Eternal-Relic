package org.eternalrelic.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import org.eternalrelic.EternalRelic;
import org.eternalrelic.screen.ChestplateStationScreenHandler;

/**
 * 胸甲台的画面 —— 把制作者画好的那张底图铺上，再把箭头按钮摆上去。
 *
 * <p><b>格子画在哪、收什么，全不在这个类里</b>：那些由
 * {@link ChestplateStationScreenHandler} 决定，原版会照着它摆好的位置把格子画出来。
 * 这个类只管三件事——铺底图、画箭头、把箭头被点的那一下转告给服务端。</p>
 *
 * <p><b>箭头为什么要自己画</b>：它是一个"按一下就把原料装上去"的按钮，不是拿来放东西的格子。
 * 原版没有这种控件，所以这里用一个只有图案的自定义控件，悬停时换成制作者画的浅蓝那一版。</p>
 *
 * <p><b>箭头被点之后走的是原版那条路</b>（{@code clickButton}）：客户端只发一个"按了 0 号按钮"
 * 的包，真正扣原料、改胸甲都在服务端做。这样即便有人改客户端，也变不出配件来。</p>
 */
public class ChestplateStationScreen extends HandledScreen<ChestplateStationScreenHandler> {

    /** 制作者画的界面底图（含六个配件槽、配件格、配料格与玩家背包）。 */
    private static final Identifier TEXTURE = EternalRelic.id("textures/gui/chestplate_station.png");

    /** 箭头平时的那一版。 */
    private static final Identifier ARROW = EternalRelic.id("textures/gui/station_arrow.png");

    /** 鼠标悬停时换成的那一版（浅蓝）。 */
    private static final Identifier ARROW_HOVER = EternalRelic.id("textures/gui/station_arrow_hover.png");

    /** 界面尺寸：与底图一致，改图时这两个数要跟着改。 */
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 222;

    /** 箭头摆在界面里的位置与大小（贴着配件格正上方）。 */
    private static final int ARROW_X = 79;
    private static final int ARROW_Y = 71;
    private static final int ARROW_WIDTH = 16;
    private static final int ARROW_HEIGHT = 18;

    /** 玩家背包那三行的标题该写在哪。 */
    private static final int INVENTORY_TITLE_Y = 129;

    public ChestplateStationScreen(ChestplateStationScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);

        this.backgroundWidth = PANEL_WIDTH;
        this.backgroundHeight = PANEL_HEIGHT;
        this.playerInventoryTitleY = INVENTORY_TITLE_Y;

        // 这张底图的顶部画的是装饰边框、没有留标题的位置，所以把标题挪到画面之外
        this.titleY = -100;
    }

    @Override
    protected void init() {
        super.init();

        this.addDrawableChild(new ApplyArrowWidget(
                this.x + ARROW_X, this.y + ARROW_Y, ARROW_WIDTH, ARROW_HEIGHT));
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(TEXTURE, this.x, this.y, 0, 0, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    /**
     * 箭头按钮 —— 按一下就把配料格与配件格的东西装到台上的胸甲上。
     *
     * <p>它自己不碰任何东西，只把"按了"这件事发给服务端；成不成、扣什么，都由那边说了算。</p>
     */
    private class ApplyArrowWidget extends ClickableWidget {

        ApplyArrowWidget(int x, int y, int width, int height) {
            super(x, y, width, height, Text.translatable("container.eternal_relic.chestplate_station"));
        }

        @Override
        protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
            Identifier texture = this.isHovered() ? ARROW_HOVER : ARROW;
            context.drawTexture(texture, this.getX(), this.getY(), 0, 0,
                    this.getWidth(), this.getHeight(), this.getWidth(), this.getHeight());
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            // 读屏软件念出来的那句话：这个按钮是干什么的
            builder.put(NarrationPart.TITLE, this.getMessage());
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            if (ChestplateStationScreen.this.client != null
                    && ChestplateStationScreen.this.client.interactionManager != null) {
                ChestplateStationScreen.this.client.interactionManager.clickButton(
                        ChestplateStationScreen.this.handler.syncId, ChestplateStationScreenHandler.BUTTON_APPLY);
            }
        }
    }
}
