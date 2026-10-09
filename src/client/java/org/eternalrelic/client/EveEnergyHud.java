package org.eternalrelic.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import org.eternalrelic.EternalRelic;
import org.eternalrelic.network.EveEnergyNetwork;

/**
 * <h1>EVE 能量条（屏幕左下角那一根）</h1>
 *
 * <p>把它画在<b>物品栏的左边</b>、贴着屏幕底边往上抬一点的地方：那是玩家视线之外的位置，
 * 打起来不会挡着准星与怪物。从上到下依次是：</p>
 *
 * <ol>
 *   <li><b>百分比文字</b> —— 在图标正上方居中；</li>
 *   <li><b>能量条本体</b> —— 制作者给的贴图（黑框 + 内部浅槽）；</li>
 *   <li><b>淡绿色的填充</b> —— 按百分比从左往右填满内部那道槽；</li>
 *   <li><b>「EVE」字样</b> —— 在条的正下方居中，同一种淡绿色、原版粗体。</li>
 * </ol>
 *
 * <h2>填充颜色的来源</h2>
 * <p>淡绿取自制作者那张图上方那颗小点（{@code #7DEF7F}），因此条里的填充与那颗点是同一个色。</p>
 *
 * <h2>内部槽的范围是量出来的</h2>
 * <p>{@link #SLOT_X} / {@link #SLOT_Y} / {@link #SLOT_WIDTH} / {@link #SLOT_HEIGHT} 四个数
 * 是从那张 32×32 的图上逐像素量出来的（条本体占 x 3~29、y 8~16，能填的那道槽是 x 6~26、y 10~13）。
 * <b>换图就要重新量这四个数</b>，否则绿色会盖到黑边框上。</p>
 *
 * <h2>数值从哪来</h2>
 * <p>自己不算，只收服务端发来的那个数（见 {@link EveEnergyNetwork}）——
 * 上限、回复速率、扣多少全在服务端，客户端只管画。</p>
 */
public final class EveEnergyHud {

    /** 贴图位置。 */
    private static final Identifier TEXTURE = EternalRelic.id("textures/gui/eve_bar.png");

    /** 贴图边长（原图就是 32×32）。 */
    private static final int TEXTURE_SIZE = 32;

    /** 内部可填充区的左边界（图内坐标）。 */
    private static final int SLOT_X = 6;

    /** 内部可填充区的上边界（图内坐标）。 */
    private static final int SLOT_Y = 10;

    /** 内部可填充区的宽度（图内像素）。 */
    private static final int SLOT_WIDTH = 21;

    /** 内部可填充区的高度（图内像素）。 */
    private static final int SLOT_HEIGHT = 4;

    /**
     * 图标右边缘离物品栏左边缘留多少像素。
     *
     * <p><b>34 是为了让开原版的「副手槽」</b>：副手拿着东西时，游戏会在物品栏<b>左边</b>
     * 画一个约 29 像素宽的格子。第一版只留了 6 像素，于是能量条正好压在它上面
     * （制作者 2026-10-06 发现并提出来）。</p>
     *
     * <p>⚠️ <b>别再往小调</b>：小于 30 就会重新压到副手槽上。</p>
     */
    private static final int GAP_FROM_HOTBAR = 34;

    /** 图标底边离屏幕底边多少像素。 */
    private static final int RAISE_FROM_BOTTOM = 34;

    /**
     * 百分比文字相对图标左上角的纵向偏移。
     *
     * <p>{@code 0} 表示文字<b>紧贴在条的上方</b>：条本体从图内 y 8 开始，而文字高 9 像素，
     * 正好占住 0~8 那一段空白。</p>
     *
     * <p>原先这里是 {@code −11} —— 因为旧版贴图的条上方还画着一颗绿点，文字得绕开它；
     * 制作者 2026-10-06 把绿点去掉之后，文字就挪回条边上了。<b>改这个数就能上下挪。</b></p>
     */
    private static final int PERCENT_OFFSET_Y = 0;

    /** 「EVE」字样相对图标左上角的纵向偏移（正值 = 在条的下方）。 */
    private static final int LABEL_OFFSET_Y = 18;

    /** 淡绿色 —— 与图里那颗小点同色。 */
    private static final int HIGHLIGHT_COLOR = 0xFF7DEF7F;

    /** 还不知道自己有多少能量（包还没到）时用它占位，此时整根条都不画。 */
    private static final int UNKNOWN = -1;

    /** 客户端手上的当前能量点数。 */
    private static int currentEnergy = UNKNOWN;

    private EveEnergyHud() {
    }

    /**
     * 由客户端入口调用：挂上收包与逐帧绘制。
     */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(EveEnergyNetwork.ENERGY_PACKET,
                (client, handler, buffer, sender) -> {
                    int energy = buffer.readInt();
                    client.execute(() -> currentEnergy = energy);
                });

        HudRenderCallback.EVENT.register((context, tickDelta) -> render(context));
    }

    /**
     * 画那根条。
     *
     * @param context 绘制上下文
     */
    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();

        // 按 F1 隐藏 HUD 时跟着一起藏；包还没到就别画（否则会先闪一下满格或空格）
        if (client.player == null || client.options.hudHidden || currentEnergy == UNKNOWN) {
            return;
        }

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        // 物品栏宽 182、居中摆放，因此它的左边缘在 屏幕宽/2 − 91
        int x = screenWidth / 2 - 91 - TEXTURE_SIZE - GAP_FROM_HOTBAR;
        int y = screenHeight - RAISE_FROM_BOTTOM;

        context.drawTexture(TEXTURE, x, y, 0.0F, 0.0F, TEXTURE_SIZE, TEXTURE_SIZE,
                TEXTURE_SIZE, TEXTURE_SIZE);

        int percent = Math.max(0, Math.min(100, currentEnergy));
        int filled = Math.round(SLOT_WIDTH * percent / 100.0F);

        if (filled > 0) {
            context.fill(x + SLOT_X, y + SLOT_Y, x + SLOT_X + filled, y + SLOT_Y + SLOT_HEIGHT,
                    HIGHLIGHT_COLOR);
        }

        TextRenderer textRenderer = client.textRenderer;

        Text percentText = Text.literal(percent + "%").formatted(Formatting.BOLD);
        context.drawText(textRenderer, percentText,
                x + TEXTURE_SIZE / 2 - textRenderer.getWidth(percentText) / 2,
                y + PERCENT_OFFSET_Y, HIGHLIGHT_COLOR, true);

        Text label = Text.literal("EVE").formatted(Formatting.BOLD);
        context.drawText(textRenderer, label,
                x + TEXTURE_SIZE / 2 - textRenderer.getWidth(label) / 2,
                y + LABEL_OFFSET_Y, HIGHLIGHT_COLOR, true);
    }
}
