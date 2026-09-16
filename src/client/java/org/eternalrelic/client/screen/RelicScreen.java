package org.eternalrelic.client.screen;

import java.util.List;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import org.eternalrelic.EternalRelic;
import org.eternalrelic.relic.RelicDefinition;
import org.lwjgl.glfw.GLFW;

/**
 * 遗物界面 —— 在背包里把鼠标停在遗物上、按一下 Shift 时打开，集中展示它的身份与来历。
 *
 * <p>这一页要回答的问题只有一个：<b>我手里这件东西到底是什么</b>。因此内容分两段来放——
 * <b>第一页是描述词</b>，先讲它是什么、从哪来；<b>第二页起才是效果</b>，逐条说清它实际会做什么。
 * 拆成两页是因为各类遗物的效果长短差得很远，全挤在一页里迟早写不下。</p>
 *
 * <p>面板样式跟着品阶走：每一档都有自己的一张面板贴图，边框与装饰的华丽程度随档位递增，
 * 玩家不必读完文字，扫一眼框色就知道这件遗物的分量。</p>
 *
 * <p>翻页用面板左下、右下两个箭头按钮，也可以用键盘左右方向键与 A / D。界面不暂停游戏，
 * 关闭后回到打开它的那个背包界面，玩家可以接着翻下一件。</p>
 */
public class RelicScreen extends Screen {

    /** 面板贴图的尺寸，与 {@code textures/gui/panel_<档位>.png} 一致。 */
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 200;

    /** 遗物贴图展示位在面板内的位置与大小；贴图按 {@link #SLOT_SCALE} 倍放大后居中放进这里。 */
    private static final int SLOT_X = 16;
    private static final int SLOT_Y = 14;
    private static final int SLOT_SIZE = 40;
    private static final float SLOT_SCALE = 2.0F;

    /** 正文区的左边界、可用宽度与行距。 */
    private static final int TEXT_LEFT = 14;
    private static final int TEXT_WIDTH = PANEL_WIDTH - 28;
    private static final int LINE_HEIGHT = 11;

    /** 正文区从面板顶部往下多少像素开始，以及每页能放多少行。 */
    private static final int TEXT_TOP = 74;
    private static final int LINES_PER_PAGE = 8;

    /** 关闭按钮的点击区域，与面板贴图上画着 × 的位置对应。 */
    private static final int CLOSE_X = PANEL_WIDTH - 26;
    private static final int CLOSE_Y = 12;

    /** 两个翻页按钮的点击区域，与面板贴图左下、右下的箭头对应。 */
    private static final int PAGE_LEFT_X = 14;
    private static final int PAGE_RIGHT_X = PANEL_WIDTH - 30;
    private static final int PAGE_BUTTON_Y = PANEL_HEIGHT - 30;

    /** 按钮点击区域是正方形，边长与贴图上画的一致。 */
    private static final int BUTTON_SIZE = 16;

    /** 各段文字的颜色。 */
    private static final int NAME_COLOR = 0xFFFFFF;
    private static final int BODY_COLOR = 0xD0D0D0;
    private static final int LORE_COLOR = 0xC8C8C8;
    private static final int HINT_COLOR = 0x8A8A8A;

    /** 这一页要展示的遗物。 */
    private final RelicDefinition relic;

    /** 打开本界面的那个界面（背包），关闭时回到它。 */
    private final Screen parent;

    /** 当前页码：0 是描述页，1 起是效果的各页。 */
    private int page;

    /** 面板左上角在屏幕上的位置，随窗口大小居中。 */
    private int panelX;
    private int panelY;

    /**
     * 打开某一页，展示一件遗物的身份与来历。
     *
     * <p>{@code parent} 是关闭后要回到的那个界面，通常就是玩家的背包——
     * 「关掉遗物界面回到背包」这件事全靠它。</p>
     *
     * @param relic  这一页要展示的遗物
     * @param parent 关闭后返回的界面
     */
    public RelicScreen(RelicDefinition relic, Screen parent) {
        super(Text.translatable(relic.translationKey()));
        this.relic = relic;
        this.parent = parent;
    }

    /**
     * 由游戏在界面打开时调用，把面板摆到屏幕正中。
     */
    @Override
    protected void init() {
        super.init();
        this.panelX = (this.width - PANEL_WIDTH) / 2;
        this.panelY = (this.height - PANEL_HEIGHT) / 2;
    }

    /**
     * 让世界照常运行，不要冻住。
     *
     * <p>父类的默认行为是「暂停」——单人模式下打开界面会把世界停住。这一页是从背包里翻开的
     * 资料页，跟背包一样不该让人停下来等：玩家往往只是扫一眼效果就接着走，中途被冻一下会很怪。
     * 原版背包界面（{@code HandledScreen}）同样是不暂停的。</p>
     *
     * @return 始终为 {@code false}
     */
    @Override
    public boolean shouldPause() {
        return false;
    }

    /**
     * 画出整页：半透明遮罩 → 面板 → 遗物贴图 → 表头 → 当前页正文 → 页码。
     *
     * @param context 绘制上下文
     */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        context.drawTexture(panelTexture(), this.panelX, this.panelY, 0, 0,
                PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);

        drawRelicIcon(context);
        drawHeader(context);

        if (this.page == 0) {
            drawLorePage(context);
        } else {
            drawEffectPage(context);
        }

        drawPageIndicator(context);

        super.render(context, mouseX, mouseY, delta);
    }

    /**
     * 把遗物贴图放大后摆进展示位。
     *
     * <p>物品本体只有 16×16，直接放进 40×40 的框里会显得又小又空，
     * 因此按两倍放大并在框内居中，让它成为这一页的视觉重心。</p>
     *
     * @param context 绘制上下文
     */
    private void drawRelicIcon(DrawContext context) {
        float scaled = 16.0F * SLOT_SCALE;
        float offset = (SLOT_SIZE - scaled) / 2.0F;

        context.getMatrices().push();
        context.getMatrices().translate(this.panelX + SLOT_X + offset, this.panelY + SLOT_Y + offset, 0.0F);
        context.getMatrices().scale(SLOT_SCALE, SLOT_SCALE, 1.0F);
        context.drawItem(new ItemStack(this.relic.item()), 0, 0);
        context.getMatrices().pop();
    }

    /**
     * 画表头：遗物名称与品阶。两页都保留，翻页时这层信息始终在场。
     *
     * @param context 绘制上下文
     */
    private void drawHeader(DrawContext context) {
        int textX = this.panelX + SLOT_X + SLOT_SIZE + 10;
        int textY = this.panelY + SLOT_Y + 4;

        context.drawText(this.textRenderer, this.relic.item().getName(), textX, textY, NAME_COLOR, true);
        context.drawText(this.textRenderer,
                Text.translatable("relic_screen.eternal_relic.rarity", this.relic.rarity().displayName())
                        .formatted(Formatting.ITALIC),
                textX, textY + LINE_HEIGHT, this.relic.rarity().color(), true);
    }

    /**
     * 画第一页：描述词。
     *
     * <p>描述是讲来历的短句，逐行居中排布，读起来更像题记而不是说明书。</p>
     *
     * @param context 绘制上下文
     */
    private void drawLorePage(DrawContext context) {
        List<OrderedText> lines = this.textRenderer.wrapLines(
                Text.translatable(this.relic.translationKey() + ".lore"), TEXT_WIDTH);

        int centerX = this.panelX + PANEL_WIDTH / 2;
        int y = this.panelY + TEXT_TOP + 18;

        for (OrderedText line : lines) {
            context.drawText(this.textRenderer, line, centerX - this.textRenderer.getWidth(line) / 2, y,
                    LORE_COLOR, false);
            y += LINE_HEIGHT + 2;
        }
    }

    /**
     * 画效果页：把效果文字按每页行数切开，只画当前页那一段。
     *
     * <p>切开而不是缩小字号，是为了让字始终清楚；内容再长也只是多翻一页。</p>
     *
     * @param context 绘制上下文
     */
    private void drawEffectPage(DrawContext context) {
        List<OrderedText> lines = effectLines();

        int first = (this.page - 1) * LINES_PER_PAGE;
        int last = Math.min(first + LINES_PER_PAGE, lines.size());
        int y = this.panelY + TEXT_TOP;

        context.drawText(this.textRenderer, Text.translatable("relic_screen.eternal_relic.effects"),
                this.panelX + TEXT_LEFT, y, NAME_COLOR, true);
        y += LINE_HEIGHT + 4;

        for (int i = first; i < last; i++) {
            context.drawText(this.textRenderer, lines.get(i), this.panelX + TEXT_LEFT, y, BODY_COLOR, false);
            y += LINE_HEIGHT;
        }
    }

    /**
     * 画底部的页码，例如「2 / 3」。
     *
     * @param context 绘制上下文
     */
    private void drawPageIndicator(DrawContext context) {
        Text indicator = Text.translatable("relic_screen.eternal_relic.page",
                this.page + 1, pageCount());

        context.drawText(this.textRenderer, indicator,
                this.panelX + PANEL_WIDTH / 2 - this.textRenderer.getWidth(indicator) / 2,
                this.panelY + PAGE_BUTTON_Y + 4, HINT_COLOR, false);
    }

    /**
     * 把效果说明按正文宽度折行，供分页使用。
     *
     * @return 折行后的效果文字
     */
    private List<OrderedText> effectLines() {
        return this.textRenderer.wrapLines(Text.translatable(this.relic.descriptionKey()), TEXT_WIDTH);
    }

    /**
     * @return 效果部分需要几页（至少一页）
     */
    private int effectPageCount() {
        int lines = effectLines().size();
        return Math.max(1, (lines + LINES_PER_PAGE - 1) / LINES_PER_PAGE);
    }

    /**
     * @return 总页数：一页描述 + 若干页效果
     */
    private int pageCount() {
        return 1 + effectPageCount();
    }

    /**
     * @return 当前品阶对应的面板贴图
     */
    private Identifier panelTexture() {
        return new Identifier(EternalRelic.MOD_ID,
                "textures/gui/panel_" + this.relic.rarity().level() + ".png");
    }

    /**
     * 处理点击：关闭按钮收工，左右箭头翻页。
     *
     * @param mouseX 鼠标横坐标
     * @param mouseY 鼠标纵坐标
     * @param button 按下的键
     * @return 本次点击是否已被处理
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        double relX = mouseX - this.panelX;
        double relY = mouseY - this.panelY;

        if (inside(relX, relY, CLOSE_X, CLOSE_Y)) {
            this.close();
            return true;
        }

        if (inside(relX, relY, PAGE_LEFT_X, PAGE_BUTTON_Y)) {
            turnPage(-1);
            return true;
        }

        if (inside(relX, relY, PAGE_RIGHT_X, PAGE_BUTTON_Y)) {
            turnPage(1);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 方向键与 A / D 同样可以翻页，习惯键盘的玩家不必去够鼠标。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A) {
            turnPage(-1);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D) {
            turnPage(1);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 翻到相邻的一页；已经在头尾时什么也不做，不会绕回另一端。
     *
     * @param delta 正数向后翻，负数向前翻
     */
    private void turnPage(int delta) {
        int next = this.page + delta;

        if (next >= 0 && next < pageCount()) {
            this.page = next;
        }
    }

    /**
     * @param relX 相对面板左边缘的横坐标
     * @param relY 相对面板上边缘的纵坐标
     * @param boxX 目标区域的左上角横坐标
     * @param boxY 目标区域的左上角纵坐标
     * @return 该点是否落在按钮范围内
     */
    private boolean inside(double relX, double relY, int boxX, int boxY) {
        return relX >= boxX && relX < boxX + BUTTON_SIZE
                && relY >= boxY && relY < boxY + BUTTON_SIZE;
    }

    /**
     * 关闭这一页，回到打开它的背包界面，方便玩家接着看下一件。
     */
    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
}
