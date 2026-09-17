package org.eternalrelic.client.screen;

import java.util.List;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.PageTurnWidget;
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
 * <p><b>它长得像一页摊开的手稿。</b>页面底图是用户手绘的，上面已经画好了遗物展示框、
 * 分隔线、剑的线稿与边角装饰，因此代码只负责往那个画好的框里摆物品、写文字。</p>
 *
 * <p>这一页要回答的问题只有一个：<b>我手里这件东西到底是什么</b>。因此内容分两段放——
 * <b>第一页是描述词</b>，先讲它是什么、从哪来；<b>第二页起才是效果</b>，逐条说清它实际会做什么。
 * 拆成两页是因为各类遗物的效果长短差得很远，全挤在一页里迟早写不下。</p>
 *
 * <p><b>翻页控件直接用了原版书籍的那一个</b>（{@code PageTurnWidget}）：悬停时它会换成
 * 「按下」那一帧，点击时播原版的翻书音效。原版书籍本身没有页面滑动的动画（只有按钮换帧加音效），
 * 所以纸页滑过去这一下是本类自己加的——文字沿翻页方向滑入、同时淡入，滑出纸张范围的部分裁掉。</p>
 */
public class RelicScreen extends Screen {

    /** 页面贴图的尺寸。与 {@code textures/gui/page.png} 一致，改图时这两个数要跟着改。 */
    private static final int PANEL_WIDTH = 146;
    private static final int PANEL_HEIGHT = 180;

    /**
     * 是否按品阶选用不同的页面贴图。
     *
     * <p>用户目前统一用同一张手绘页面，所以这里是 {@code false}；七张分档书皮
     * （{@code page_1.png} ~ {@code page_7.png}）仍留在资源目录里，改成 {@code true} 即可切回。</p>
     */
    private static final boolean USE_TIER_PAGES = false;

    /** 统一使用的那一张页面贴图。 */
    private static final Identifier SINGLE_PAGE =
            new Identifier(EternalRelic.MOD_ID, "textures/gui/page.png");

    /** 纸上可写字的那一块：左边界与宽度。左边留出的空位是给底部那条红色书签的。 */
    private static final int TEXT_LEFT = 30;
    private static final int TEXT_WIDTH = 105;

    /** 正文的行高，以及每页能放多少行。 */
    private static final int LINE_HEIGHT = 11;
    private static final int LINES_PER_PAGE = 7;

    /** 正文区从面板顶部往下多少像素开始。位置紧跟分隔线（贴图里在 y54~58）。 */
    private static final int TEXT_TOP = 76;

    /** 正文区的下边界。改行数或改起始高度时它会跟着算，不必手工同步。 */
    private static final int TEXT_BOTTOM = TEXT_TOP + LINES_PER_PAGE * LINE_HEIGHT;

    /** 遗物图标摆在贴图里那个手绘方框的正中。方框是 x24~45、y24~45，内部 20×20。 */
    private static final int ITEM_X = 27;
    private static final int ITEM_Y = 27;

    /** 遗物名摆在方框右侧（左边那圈太阳光芒伸到 x≈50 为止）。 */
    private static final int NAME_X = 51;
    private static final int NAME_Y = 28;

    /** 品阶那一行压在分隔线下方。右端空出来写当前页是哪一类（描述 / 效果）。 */
    private static final int RARITY_Y = 62;

    /** 关闭按钮的点击区域，摆在页面右上角没有装饰的空位。 */
    private static final int CLOSE_X = 122;
    private static final int CLOSE_Y = 10;

    /** 按钮点击区域是正方形，边长与画出来的记号一致。 */
    private static final int BUTTON_SIZE = 16;

    /** 叉号记号在它那块点击区域里的内缩，以及笔画长度。 */
    private static final int CLOSE_MARK_INSET = 4;
    private static final int CLOSE_MARK_SIZE = 8;

    /**
     * 两个翻页控件摆在**页面外侧**的左右两边（控件本身是原版的 23×13）。
     *
     * <p>夹着书页放，鼠标不必先挪进页面里去够——翻页是这一页上最常做的动作。
     * 放到外侧也顺带避开了手绘页面左下角那条红色书签。</p>
     */
    private static final int PAGE_BUTTON_WIDTH = 23;
    private static final int PAGE_BUTTON_HEIGHT = 13;
    private static final int PAGE_SIDE_MARGIN = 8;

    /** 页码印在页面底部正中。翻页控件挪到外侧之后，这块地方就空出来了。 */
    private static final int PAGE_INDICATOR_Y = 159;

    /** 纸页滑动动画的时长（秒）与滑动距离（像素）。 */
    private static final float PAGE_TURN_SECONDS = 0.22F;
    private static final float PAGE_SLIDE_DISTANCE = 46.0F;

    /** 滑动过程中文字的最低不透明度。不能取 0——字体渲染会把全透明的颜色当成不透明。 */
    private static final int MIN_ALPHA = 0x30;

    /**
     * 各段文字的颜色。
     *
     * <p>页面底图是米白的纸，所以文字一律用**深墨色**——靠明暗拉开对比，而不是靠加亮。
     * 早先那套浅灰/白色是给深色石纹面板配的，放到纸上会几乎看不见。</p>
     */
    private static final int NAME_COLOR = 0x2E1B08;
    private static final int BODY_COLOR = 0x33240F;
    private static final int LORE_COLOR = 0x4A3418;
    private static final int LABEL_COLOR = 0x6B4A2A;
    private static final int HINT_COLOR = 0x7A5A38;
    private static final int CLOSE_COLOR = 0x6B4A2A;
    private static final int CLOSE_HOVER_COLOR = 0xB03030;

    /** 这一页要展示的遗物。 */
    private final RelicDefinition relic;

    /** 打开本界面的那个界面（背包），关闭时回到它。 */
    private final Screen parent;

    /** 当前页码：0 是描述页，1 起是效果的各页。 */
    private int page;

    /** 面板左上角在屏幕上的位置，随窗口大小居中。 */
    private int panelX;
    private int panelY;

    /** 原版的翻页控件。它自己负责贴图、悬停换帧与翻书音效。 */
    private PageTurnWidget previousPageButton;
    private PageTurnWidget nextPageButton;

    /** 纸页滑动的进度：1 表示已经落定，小于 1 表示正在滑。 */
    private float pageTurnProgress = 1.0F;

    /** 本次翻页的方向：正数向后翻、负数向前翻。 */
    private int pageTurnDirection = 1;

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
     * 由游戏在界面打开时调用：把面板摆到屏幕正中，并请原版把两个翻页控件挂上。
     */
    @Override
    protected void init() {
        super.init();
        this.panelX = (this.width - PANEL_WIDTH) / 2;
        this.panelY = (this.height - PANEL_HEIGHT) / 2;

        // 竖直方向跟书页对齐，夹在左右两侧
        int buttonY = this.panelY + (PANEL_HEIGHT - PAGE_BUTTON_HEIGHT) / 2;

        this.previousPageButton = this.addDrawableChild(new PageTurnWidget(
                this.panelX - PAGE_BUTTON_WIDTH - PAGE_SIDE_MARGIN, buttonY, false,
                button -> turnPage(-1), true));
        this.nextPageButton = this.addDrawableChild(new PageTurnWidget(
                this.panelX + PANEL_WIDTH + PAGE_SIDE_MARGIN, buttonY, true,
                button -> turnPage(1), true));

        updatePageButtons();
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
     * 画出整页：半透明遮罩 → 页面底图 → 遗物图标 → 表头 → 当前页正文 → 页码 → 关闭记号。
     *
     * <p>两个翻页控件交给 {@code super.render} 去画，这样它们天然压在页面之上。</p>
     *
     * @param context 绘制上下文
     * @param mouseX  鼠标横坐标
     * @param mouseY  鼠标纵坐标
     * @param delta   距上一帧的时间占一刻的比例，纸页滑动靠它推进
     */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        context.drawTexture(pageTexture(), this.panelX, this.panelY, 0, 0,
                PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);

        drawRelicIcon(context);
        drawHeader(context);
        drawPageBody(context, delta);
        drawPageIndicator(context);
        drawCloseMark(context, mouseX, mouseY);

        super.render(context, mouseX, mouseY, delta);
    }

    /**
     * 把遗物图标摆进页面底图上画好的那个方框里。
     *
     * <p>方框是手绘的，代码不再另画一圈边框——两圈框叠在一起只会显得脏。
     * 图标按原尺寸放进去即可：框内正好够 16×16，跟背包格子一样的比例。</p>
     *
     * @param context 绘制上下文
     */
    private void drawRelicIcon(DrawContext context) {
        context.drawItem(new ItemStack(this.relic.item()),
                this.panelX + ITEM_X, this.panelY + ITEM_Y);
    }

    /**
     * 画表头：遗物名在方框右侧，品阶压在分隔线下方。
     *
     * <p>表头不参与翻页滑动——它是"这一页讲的是谁"，翻到哪一页都该在场。</p>
     *
     * @param context 绘制上下文
     */
    private void drawHeader(DrawContext context) {
        // 一律用粗体：原版字体的粗体是加粗一像素，在米白纸上最容易认，也不用另配字体
        context.drawText(this.textRenderer,
                this.relic.item().getName().copy().formatted(Formatting.BOLD),
                this.panelX + NAME_X, this.panelY + NAME_Y, NAME_COLOR, false);

        context.drawText(this.textRenderer,
                Text.translatable("relic_screen.eternal_relic.rarity", this.relic.rarity().displayName())
                        .formatted(Formatting.BOLD),
                this.panelX + TEXT_LEFT, this.panelY + RARITY_Y,
                this.relic.rarity().color(), false);

        // 同一行的右端标明这一页是哪一类，省得玩家猜"现在看的是描述还是效果"
        Text kind = Text.translatable(this.page == 0
                ? "relic_screen.eternal_relic.lore"
                : "relic_screen.eternal_relic.effects")
                .formatted(Formatting.BOLD);

        context.drawText(this.textRenderer, kind,
                this.panelX + TEXT_LEFT + TEXT_WIDTH - this.textRenderer.getWidth(kind),
                this.panelY + RARITY_Y, LABEL_COLOR, false);
    }

    /**
     * 推进纸页滑动，并把当前页的正文画出来。
     *
     * <p>滑动期间会把这一块裁在纸张范围内：文字该从纸面上滑进来，而不该跑到书框上去。
     * 表头与页码不参与滑动——它们是"书本身"的一部分，只有纸页上的字在动。</p>
     *
     * @param context 绘制上下文
     * @param delta   距上一帧的时间占一刻的比例
     */
    private void drawPageBody(DrawContext context, float delta) {
        advancePageTurn(delta);

        if (this.pageTurnProgress >= 1.0F) {
            drawCurrentPage(context, 0xFF);
            return;
        }

        float eased = easeOut(this.pageTurnProgress);
        float slide = this.pageTurnDirection * (1.0F - eased) * PAGE_SLIDE_DISTANCE;
        int alpha = (int) (MIN_ALPHA + (0xFF - MIN_ALPHA) * eased);

        context.enableScissor(
                this.panelX + TEXT_LEFT - 4, this.panelY + TEXT_TOP - 2,
                this.panelX + TEXT_LEFT + TEXT_WIDTH + 4, this.panelY + TEXT_BOTTOM + 2);
        context.getMatrices().push();
        context.getMatrices().translate(slide, 0.0F, 0.0F);
        drawCurrentPage(context, alpha);
        context.getMatrices().pop();
        context.disableScissor();
    }

    /**
     * 按当前页码画出描述页或效果页。
     *
     * @param context 绘制上下文
     * @param alpha   整块正文的不透明度；滑动时它会淡入
     */
    private void drawCurrentPage(DrawContext context, int alpha) {
        if (this.page == 0) {
            drawLorePage(context, alpha);
        } else {
            drawEffectPage(context, alpha);
        }
    }

    /**
     * 画第一页：描述词。
     *
     * <p>描述是讲来历的短句，逐行居中排布，读起来更像题记而不是说明书。</p>
     *
     * @param context 绘制上下文
     * @param alpha   不透明度
     */
    private void drawLorePage(DrawContext context, int alpha) {
        List<OrderedText> lines = this.textRenderer.wrapLines(
                Text.translatable(this.relic.translationKey() + ".lore")
                        .formatted(Formatting.BOLD, Formatting.ITALIC),
                TEXT_WIDTH);

        int centerX = this.panelX + PANEL_WIDTH / 2;
        int y = this.panelY + TEXT_TOP + 14;

        for (OrderedText line : lines) {
            context.drawText(this.textRenderer, line, centerX - this.textRenderer.getWidth(line) / 2, y,
                    withAlpha(LORE_COLOR, alpha), false);
            y += LINE_HEIGHT + 2;
        }
    }

    /**
     * 画效果页：把效果文字按每页行数切开，只画当前页那一段。
     *
     * <p>切开而不是缩小字号，是为了让字始终清楚；内容再长也只是多翻一页。</p>
     *
     * @param context 绘制上下文
     * @param alpha   不透明度
     */
    private void drawEffectPage(DrawContext context, int alpha) {
        List<OrderedText> lines = effectLines();

        int first = (this.page - 1) * LINES_PER_PAGE;
        int last = Math.min(first + LINES_PER_PAGE, lines.size());
        int y = this.panelY + TEXT_TOP;

        for (int i = first; i < last; i++) {
            context.drawText(this.textRenderer, lines.get(i), this.panelX + TEXT_LEFT, y,
                    withAlpha(BODY_COLOR, alpha), false);
            y += LINE_HEIGHT;
        }
    }

    /**
     * 画页码，例如「2 / 3」，印在页面底部正中。
     *
     * @param context 绘制上下文
     */
    private void drawPageIndicator(DrawContext context) {
        Text indicator = Text.translatable("relic_screen.eternal_relic.page", this.page + 1, pageCount())
                .formatted(Formatting.BOLD);

        context.drawText(this.textRenderer, indicator,
                this.panelX + PANEL_WIDTH / 2 - this.textRenderer.getWidth(indicator) / 2,
                this.panelY + PAGE_INDICATOR_Y, HINT_COLOR, false);
    }

    /**
     * 在页面右上角画一个叉号当关闭按钮。
     *
     * <p>手绘的页面底图上没有这个记号，所以由代码画：不用字体里的「×」字符，
     * 免得遇上没有那个字形的字体包时变成一个方框。</p>
     *
     * @param context 绘制上下文
     * @param mouseX  鼠标横坐标
     * @param mouseY  鼠标纵坐标
     */
    private void drawCloseMark(DrawContext context, int mouseX, int mouseY) {
        boolean hovered = isOverClose(mouseX, mouseY);
        int color = (hovered ? CLOSE_HOVER_COLOR : CLOSE_COLOR) | 0xFF000000;

        int left = this.panelX + CLOSE_X + CLOSE_MARK_INSET;
        int top = this.panelY + CLOSE_Y + CLOSE_MARK_INSET;

        for (int i = 0; i < CLOSE_MARK_SIZE; i++) {
            context.fill(left + i, top + i, left + i + 1, top + i + 1, color);
            context.fill(left + CLOSE_MARK_SIZE - 1 - i, top + i,
                    left + CLOSE_MARK_SIZE - i, top + i + 1, color);
        }
    }

    /**
     * 推进纸页滑动的进度。
     *
     * @param delta 距上一帧的时间占一刻的比例
     */
    private void advancePageTurn(float delta) {
        if (this.pageTurnProgress >= 1.0F) {
            return;
        }

        this.pageTurnProgress = Math.min(1.0F,
                this.pageTurnProgress + delta / (PAGE_TURN_SECONDS * 20.0F));
    }

    /**
     * 把滑动进度缓一下：起步快、落定慢，看起来才像纸被拨过去又贴回桌面。
     *
     * @param progress 线性进度，0~1
     * @return 缓动后的进度
     */
    private static float easeOut(float progress) {
        float inverse = 1.0F - progress;
        return 1.0F - inverse * inverse * inverse;
    }

    /**
     * 给文字颜色补上不透明度。
     *
     * @param rgb   颜色（不含透明度）
     * @param alpha 不透明度，1~255
     * @return 带上透明度的颜色
     */
    private static int withAlpha(int rgb, int alpha) {
        return (rgb & 0xFFFFFF) | alpha << 24;
    }

    /**
     * 刷新两个翻页控件的可见性：第一页没有上一页、最后一页没有下一页。
     *
     * <p>用「藏起来」而不是「变灰」——这正是原版书籍的做法，省得玩家去猜箭头为什么没反应。</p>
     */
    private void updatePageButtons() {
        if (this.previousPageButton == null || this.nextPageButton == null) {
            return;
        }

        this.previousPageButton.visible = this.page > 0;
        this.nextPageButton.visible = this.page < pageCount() - 1;
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
     * @return 当前该用哪张页面贴图
     */
    private Identifier pageTexture() {
        if (!USE_TIER_PAGES) {
            return SINGLE_PAGE;
        }

        return new Identifier(EternalRelic.MOD_ID,
                "textures/gui/page_" + this.relic.rarity().level() + ".png");
    }

    /**
     * 处理点击：关闭记号收工，其余交给原版的翻页控件。
     *
     * @param mouseX 鼠标横坐标
     * @param mouseY 鼠标纵坐标
     * @param button 按下的键
     * @return 本次点击是否已被处理
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverClose(mouseX, mouseY)) {
            this.close();
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
     * <p>页码立刻改掉、滑动动画随后跟上：这样页脚的两个箭头会马上跟着变，
     * 玩家不必等动画放完才能再翻一页。</p>
     *
     * @param delta 正数向后翻，负数向前翻
     */
    private void turnPage(int delta) {
        int next = this.page + delta;

        if (next < 0 || next >= pageCount()) {
            return;
        }

        this.page = next;
        this.pageTurnDirection = delta;
        this.pageTurnProgress = 0.0F;

        updatePageButtons();
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

    /**
     * @param mouseX 鼠标横坐标
     * @param mouseY 鼠标纵坐标
     * @return 鼠标是否停在关闭记号上
     */
    private boolean isOverClose(double mouseX, double mouseY) {
        double relX = mouseX - this.panelX;
        double relY = mouseY - this.panelY;

        return relX >= CLOSE_X && relX < CLOSE_X + BUTTON_SIZE
                && relY >= CLOSE_Y && relY < CLOSE_Y + BUTTON_SIZE;
    }
}
