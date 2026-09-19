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
