package org.eternalrelic.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import org.eternalrelic.EternalRelic;
import org.eternalrelic.screen.RelicStationScreenHandler;

/**
 * 遗物装卸台的界面。
 *
 * <p>底图就是制作者画的那张：画布 256×256，**界面内容只占左上角 176×166**，
 * 因此绘制时要连贴图尺寸一起告诉游戏，否则会把整张画布当成界面铺开。
 * 图上的格子、箭头、加号都已经画在底图里，这里不再补画任何装饰——只放物品与玩家拖动的内容。</p>
 */
@Environment(EnvType.CLIENT)
public class RelicStationScreen extends HandledScreen<RelicStationScreenHandler> {

    /** 制作者画的界面底图。 */
    private static final Identifier TEXTURE =
            new Identifier(EternalRelic.MOD_ID, "textures/gui/relic_station.png");

    /** 底图的真实画布尺寸。界面内容只占左上 176×166，右边与下面是空白。 */
    private static final int TEXTURE_SIZE = 256;

    public RelicStationScreen(RelicStationScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 166;

        // 图上没有"物品栏"字样，把原版那行标题挪到画不到的地方
        this.playerInventoryTitleY = 10000;
    }

    /**
     * 画出整页：背景遮罩 → 底图与格子里的物品 → 鼠标底下那件物品的介绍框。
     *
     * <p><b>⚠️ 头一行与末一行必须自己写，原版不会替我们做。</b>
     * 这是 1.20.1 的规矩：{@code HandledScreen} 只管把界面本体画出来，
     * 「背后的世界要不要变暗」（{@link #renderBackground}）与「悬停的物品要不要弹介绍框」
     * （{@link #drawMouseoverTooltip}）都由子类在 {@code render} 里点一下 —— 原版每个容器界面
     * （箱子、熔炉、工作台……）都写着这两行。</p>
     *
     * <p>漏掉首行的后果是打开界面时背后的世界亮得像没开界面；漏掉末行就是
     * <b>鼠标停在任何物品上都不弹介绍框</b>。</p>
     *
     * @param context 绘制上下文
     * @param mouseX  鼠标横坐标
     * @param mouseY  鼠标纵坐标
     * @param delta   距上一帧的时间占一刻的比例（本界面没有动画，用不到）
     */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;
        context.drawTexture(TEXTURE, x, y, 0.0F, 0.0F, this.backgroundWidth, this.backgroundHeight, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // 标题与"物品栏"都画在底图上了（或者说图上本来就没有），这里保持干净
    }
}
