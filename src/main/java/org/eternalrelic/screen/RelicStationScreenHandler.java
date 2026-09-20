package org.eternalrelic.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import org.eternalrelic.registry.ModBlocks;
import org.eternalrelic.registry.ModScreens;
import org.eternalrelic.registry.ModSounds;
import org.eternalrelic.relic.RelicAttachment;
import org.eternalrelic.relic.RelicAttachRule;

/**
 * 遗物装卸台的界面容器 —— 全项目最需要小心的一处。
 *
 * <p><b>台子本身什么也不存</b>（照原版工作台的样子）：四格内容由本类自持
 * （见 {@link RelicStationInventory}），随这一次界面会话而生、关掉界面就退还玩家，
 * 方块只是一个开关。于是<b>两个玩家同时开同一个台子时各用各的</b>，也不必再操心
 * "拆掉台子时里面的东西怎么办"。</p>
 *
 * <p>界面上四格与右边六格的位置：</p>
 *
 * <pre>
 *   工件(15,15)                          ┌─ 六格围着"成品栏"排一圈 ─┐
 *   遗物(15,52)   辅料(41,52)  →  成品栏(65,35)
 * </pre>
 *
 * <h2>装与拆是两个方向、两个入口</h2>
 *
 * <ul>
 *   <li><b>装</b>：把工件放进<b>最左侧</b>那一格，配上辅料与遗物 —— 成品栏里摆出成品，拿走它即完成；</li>
 *   <li><b>拆</b>：把<b>已附遗物的成品</b>放进<b>成品栏</b>，把工件格腾空 —— 右边六格列出它身上的遗物，
 *       从六格拿一枚即拆下。</li>
 * </ul>
 *
 * <p><b>两个方向靠"工件格空不空"分开，而且互斥</b>：工件格里有东西（下称"装模式"）时，
 * 成品栏只许台子写；工件格空着（下称"拆模式"）时成品栏归玩家，而工件格反过来锁住 ——
 * 不让再往里放东西，否则台子会转去摆预览，把玩家那件待拆的成品覆盖掉。要换方向，先把对应那格清空。</p>
 *
 * <h2>⚠️ 为什么这个类写得这么啰嗦 —— 全是踩过的坑</h2>
 *
 * <p>第一版把"取走"完全交给了游戏框架，结果留下一条<b>能无限刷物品</b>的漏洞，而且方向不止一个。
 * 根因是：六格是一个<b>只读视图</b>，而框架把"东西被取走"分成好几条路，其中有的根本不会调用
 * 视图的取出方法、只是把 {@code getStack()} 返回的临时对象改一改，或者传一个空堆进
 * {@code setStack} 表示"这格清空了"。视图不认这些，于是出现"玩家拿到了东西、工件上的记录还在"。</p>
 *
 * <p>因此现在采取<b>最保守的写法</b>：</p>
 *
 * <ol>
 *   <li><b>六格被排除在一切"通用搬运"之外。</b>从玩家背包 Shift+点击时，搬运范围只到前三格
 *       （{@code insertItem(stack, 0, 3, false)}）——<b>不包含六格</b>。这一条是关键：
 *       原版搬运里有一段"合并同款物品"，它<b>不检查能不能放</b>，只比对物品是否相同就把玩家手里
 *       那叠东西清零、并去改一个临时对象；落到只读视图上就是<b>玩家的东西凭空消失</b>。
 *       六格的取出走下面的专用通路，不经过通用搬运。</li>
 *   <li><b>每一格能放什么，一律问 {@link RelicAttachRule}</b> —— 与锻造台那条路共用同一套裁决，
 *       免得两条入口的判定分叉。</li>
 *   <li><b>"这一格被清空"要不要扣材料，先看是哪个模式。</b>成品栏在两副面孔下都可能是"被清空"：
 *       装模式下清空 = 玩家拿走了成品，<b>该扣三样输入</b>；拆模式下清空 = 玩家把自己的成品收回去了，
 *       <b>什么也不欠</b>。判据是工件格空不空，结算写在
 *       {@link RelicStationInventory} 里（见 {@code onCraftedTaken}）。</li>
 *   <li><b>"装模式下成品栏里必然是台子摆的"这条前提，靠 {@code canInsert} 撑着。</b>
 *       装模式下这一格 {@code canInsert} 恒假，于是格里出现的东西<b>必然是台子摆的</b>，
 *       不需要靠"内容比对"去猜（第一版正是靠猜，结果两个方向都会错：既能白拿预览，
 *       也会把玩家自己放进去的装备误判成台子摆的、白扣掉三样材料）。</li>
 *   <li><b>拿走成品要扣什么，挂在"成品栏被清空"那一刻</b>（在 {@link RelicStationInventory} 里），
 *       而不是挂在某个界面回调上 —— 原版取走东西有好几条路（鼠标直接拿、<b>按 Q 丢弃</b>、
 *       <b>双击收集</b>、Shift+点击、按 1~9），其中有的<b>根本不叫回调</b>，
 *       挂在回调上必然漏掉一条、留下白拿的口子。</li>
 * </ol>
 *
 * <p><b>关掉界面时台子里玩家自己的东西会退还</b>（原版工作台的做法）：内容不落盘，
 * 不退就等于凭空吞掉玩家的装备。台子自己摆的那份成品预览则直接作废 —— 它只是摆出来给人看的副本。</p>
 */
public class RelicStationScreenHandler extends ScreenHandler {

    /** 台子这边一共有几格：四格工作区 + 六格附着视图。 */
    public static final int STATION_SLOTS = RelicStationInventory.SIZE + RelicAttachment.MAX_ATTACHMENTS;

    /** 六格附着视图的位置，围着成品栏排成一圈（与界面美术一致）。 */
    private static final int[][] ATTACHMENT_POSITIONS = {
            { 77, 8 }, { 127, 6 }, { 147, 20 }, { 151, 41 }, { 143, 61 }, { 88, 60 }
    };

    // ---- 四个工作格的位置。**按制作者原本的设计摆**：最左侧是"装"的入口，正中是成品栏 ----
    //
    // ⚠️ 改这几个坐标是安全的（只影响"格子画在哪"），但**不要顺手改格子的含义**：
    // 六格附着视图照着的是成品栏（SLOT_RESULT）里那件待拆的成品，而它是否有内容又取决于
    // 工件格（SLOT_TOOL）空不空。把这两格对调，等于把"装"和"拆"两个入口对调（详见类文档）。

    private static final int TOOL_X = 15;
    private static final int TOOL_Y = 15;
    private static final int MATERIAL_X = 41;
    private static final int MATERIAL_Y = 52;
    private static final int RELIC_X = 15;
    private static final int RELIC_Y = 52;
    private static final int RESULT_X = 65;
    private static final int RESULT_Y = 35;

    /** 四格内容：随这次界面会话而生，界面一关就随之作废。 */
    private final RelicStationInventory contents;

    /** 六格的来源：成品栏里那件待拆成品的实时视图。 */
    private final Inventory attachments;

    /** 玩家背包 —— 退还输入、播放敲击声都要经过它。 */
    private final PlayerInventory playerInventory;

    /**
     * 打开界面的那个台子的位置。
     *
     * <p>客户端为 {@code null}：客户端只按同步过来的内容摆样子，不需要知道台子在哪
     * （敲击声由服务端在台子那儿发声，客户端自己不放）。</p>
     */
    private final BlockPos origin;

    /**
     * 客户端用的构造函数 —— 由 {@link ModScreens#RELIC_STATION} 的界面工厂调用。
     *
     * @param syncId          同步编号
     * @param playerInventory 玩家背包
     */
    public RelicStationScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, null);
    }

    /**
     * 服务端用的构造函数 —— 由 {@code RelicStationBlock} 打开界面时调用。
     *
     * @param syncId          同步编号
     * @param playerInventory 玩家背包
     * @param origin          台子的位置；客户端传 {@code null}
     */
    public RelicStationScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos origin) {
        super(ModScreens.RELIC_STATION, syncId);

        this.playerInventory = playerInventory;
        this.origin = origin;

        // 客户端手上那份只是照服务端下发的数据摆样子，因此不算"说了算的那一份"：
        // 它不许执行"拿走成品就扣三样输入"那套动作（详见 RelicStationInventory 类文档）
        boolean authoritative = !playerInventory.player.getWorld().isClient;

        this.contents = new RelicStationInventory(this::knock, authoritative);
        this.attachments = new RelicStationAttachments(this.contents, this::knock);

        addToolSlot();
        addMaterialSlot();
        addRelicSlot();
        addResultSlot();
        addAttachmentSlots();
        addPlayerSlots(playerInventory);
    }

    // ==================== 装还是拆 ====================

    /**
     * 此刻是不是在"装"。
     *
     * <p>判据只有一个：<b>最左侧的工件格里有没有东西</b>。有 = 装（成品栏是台子摆的预览），
     * 空 = 拆（成品栏归玩家）。整个界面的分支都挂在这一条上，动它之前请先读类文档。</p>
     *
     * @return 工件格非空时返回 {@code true}
     */
    private boolean isAttaching() {
        return !this.contents.getStack(RelicStationInventory.SLOT_TOOL).isEmpty();
    }

    /**
     * 成品栏里此刻是不是放着<b>玩家自己的</b>东西。
     *
     * <p>"装"模式下玩家塞不进成品栏，所以格里非空只可能发生在"拆"模式 —— 这时工件格要反过来
     * 锁住，免得玩家再往里放东西、让台子转去摆预览把它覆盖掉。</p>
     *
     * @return 工件格空着而成品栏非空时返回 {@code true}
     */
    private boolean resultHoldsPlayerItem() {
        return !isAttaching() && !this.contents.getStack(RelicStationInventory.SLOT_RESULT).isEmpty();
    }

    // ==================== 摆格子 ====================

    /**
     * 工件格 —— 界面最左侧那一格，也是"装"这一路的唯一入口。
     *
     * <p><b>从这里放进去的东西只用来附着，拆不了</b>：六格附着视图根本不看它
     * （见 {@link RelicStationAttachments}）。要拆，得把成品放进成品栏。
     *
     * <p>收"能被附的"与"身上已经附了东西的"两种：后者是为了让人<b>能继续往一件已附遗物的装备上
     * 再加遗物</b>；把旧遗物拆下来则走成品栏那条路。</p>
     *
     * <p><b>成品栏里放着玩家的待拆成品时，这一格拒绝放入</b>：那种情况下界面正在"拆"，
     * 再往里放东西会让台子转去摆预览、把玩家那件成品覆盖掉。要装就先把成品栏清空。</p>
     */
    private void addToolSlot() {
        this.addSlot(new Slot(this.contents, RelicStationInventory.SLOT_TOOL, TOOL_X, TOOL_Y) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return RelicAttachRule.canBeWorkedOn(stack) && !resultHoldsPlayerItem();
            }
        });
    }

    /** 辅料格：只收某件遗物指定的那种辅料。 */
    private void addMaterialSlot() {
        this.addSlot(new Slot(this.contents, RelicStationInventory.SLOT_MATERIAL, MATERIAL_X, MATERIAL_Y) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return RelicAttachRule.isMaterialItem(stack.getItem());
            }
        });
    }

    /** 遗物格：只收登记在可附白名单里的遗物。 */
    private void addRelicSlot() {
        this.addSlot(new Slot(this.contents, RelicStationInventory.SLOT_RELIC, RELIC_X, RELIC_Y) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return RelicAttachRule.isRelicItem(stack.getItem());
            }
        });
    }

    /**
     * 成品栏 —— 正中被六格围着的那一格，两副面孔（见类文档）。
     *
     * <p><b>"装"的时候只许台子写</b>：工件格里有东西时 {@code canInsert} 恒假，于是格里出现的
     * 东西<b>必然是台子摆的成品预览</b>，"拿走它"永远等于"拿走成品"，不需要任何猜测。
     * 第一版没有这道闸，只能靠"内容是否一样"去反推，结果既能被人白拿预览，
     * 也会把玩家自己放进去的装备误判成台子摆的、白扣掉三样材料。</p>
     *
     * <p><b>"拆"的时候收玩家的成品</b>：工件格空着时这一格归玩家，只收<b>身上已附遗物的东西</b>
     * （收别的没有意义 —— 六格会是空的），放进去六格就列出它身上的遗物。</p>
     *
     * <p><b>拿走时要扣的三样输入，不在这里扣</b> —— 扣在 {@link RelicStationInventory}
     * "成品栏被清空"那一刻，而且只在"装"的时候扣，理由见类文档第 3 条。</p>
     */
    private void addResultSlot() {
        this.addSlot(new Slot(this.contents, RelicStationInventory.SLOT_RESULT, RESULT_X, RESULT_Y) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return !isAttaching() && RelicAttachRule.hasAttachments(stack);
            }
        });
    }

    /**
     * 右边六格 —— 成品栏里那件待拆成品身上已附遗物的实时视图。
     *
     * <p>取出由视图自己负责（见 {@link RelicStationAttachments}），这里只摆位置。</p>
     */
    private void addAttachmentSlots() {
        for (int i = 0; i < ATTACHMENT_POSITIONS.length; i++) {
            this.addSlot(new Slot(this.attachments, i, ATTACHMENT_POSITIONS[i][0], ATTACHMENT_POSITIONS[i][1]));
        }
    }

    /**
     * 玩家背包那 36 格。
     *
     * @param playerInventory 玩家背包
     */
    private void addPlayerSlots(PlayerInventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }

        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }
    }

    // ==================== 成品预览 ====================

    /**
     * 每次点过格子之后重算一次预览。
     *
     * <p>原版容器并不会自动收到"库存变了"的通知——那要自己接回调。这里换个更省事的挂法：
     * 玩家的每一次取放都要经过 {@code onSlotClick}，所以在它之后算一遍就够了，
     * 而且顺序正好：先应用玩家的操作，再据此更新预览。</p>
     *
     * <p>只在服务端算：预览要跟着往下发给客户端，客户端自己算出来的那份没人要，
     * 反而会与服务端下发的权威内容打架。</p>
     */
    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        super.onSlotClick(slotIndex, button, actionType, player);

        if (!player.getWorld().isClient) {
            updateResult();
        }
    }

    /**
     * 按当前三格算出成品，摆到成品栏里 —— <b>这一步只显示，不扣任何东西</b>。
     *
     * <p><b>⚠️ 工件格空着时一概不碰成品栏，连清都不清。</b>那种情况下界面正在"拆"，成品栏里是
     * 玩家自己放进来待拆的成品 —— 照旧"内容不一样就覆盖"会把它抹掉（且不退还）；
     * 而"顺手清一清总没错"同样会把它抹掉，因为重算这一步<b>分不清格里那份是谁的</b>。</p>
     *
     * <p>台子摆的那份预览改由库存类在<b>工件格被取空的那一刻</b>就地收回
     * （见 {@link RelicStationInventory} 的 {@code removeStack} / {@code setStack}）：
     * 那一刻格里必然是台子摆的（"装"模式下玩家塞不进成品栏），既判断得准、也收得及时 ——
     * 慢一步就成了一份白送的成品，因为工件格一空，六格立刻转去照成品栏。</p>
     */
    private void updateResult() {
        if (!isAttaching()) {
            return;
        }

        ItemStack produced = RelicAttachRule.previewOf(
                this.contents.getStack(RelicStationInventory.SLOT_MATERIAL),
                this.contents.getStack(RelicStationInventory.SLOT_TOOL),
                this.contents.getStack(RelicStationInventory.SLOT_RELIC));

        ItemStack current = this.contents.getStack(RelicStationInventory.SLOT_RESULT);

        // 内容没变就不动它：频繁 setStack 会让这一格每刻都闪。
        // 注意走的是 setResult 而不是 setStack —— 那条路会被当成"玩家把成品拿走了"。
        if (ItemStack.areEqual(current, produced)) {
            return;
        }

        this.contents.setResult(produced);
    }

    /**
     * 界面关掉时，把台子里玩家自己的东西全还给他。
     *
     * <p><b>为什么要还</b>：东西不再存在方块里了，不还就等于凭空吞掉玩家的装备。
     * 这正是原版工作台的规矩（它也一样，走的时候把合成格里的东西退回）。</p>
     *
     * <p><b>台子摆的那份预览不还</b>：它是照着输入摆出来的一份副本，本来就不是玩家的东西。</p>
     *
     * <p>只在服务端做：客户端也会收到这次关闭，若两边各退一次，玩家会先看到背包里多一份。</p>
     */
    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);

        if (player.getWorld().isClient) {
            return;
        }

        returnInputs(player);
    }

    /**
     * 退还台子里属于玩家的东西：前三格，加上成品栏（当那里面是玩家的成品时）。
     *
     * <p><b>⚠️ 成品栏必须先处理，再退前三格。</b>"是不是在装"看的就是工件格，而退前三格会把工件
     * 一起退走 —— 顺序反过来的话，台子摆的那份预览就会被当成玩家的东西一并送出去，白送一份成品。</p>
     *
     * @param player 关闭界面的玩家
     */
    private void returnInputs(PlayerEntity player) {
        if (isAttaching()) {
            this.contents.clearResult();
        } else {
            giveBack(player, this.contents.removeStack(RelicStationInventory.SLOT_RESULT));
        }

        for (int slot = 0; slot < RelicStationInventory.INPUT_SLOTS; slot++) {
            giveBack(player, this.contents.removeStack(slot));
        }
    }

    /**
     * 把一份东西塞回玩家背包；塞不下的丢在他脚边。
     *
     * @param player 收东西的玩家
     * @param stack  要还回去的东西；空则什么也不做
     */
    private static void giveBack(PlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
    }

    // ==================== 敲击声 ====================

    /**
     * 在台子那一格的位置敲一下 —— 拆下一枚遗物、拿走成品时各响一次。
     *
     * <p>由服务端发声，附近的人才能同时听到同一个声音。客户端不发声（{@code origin} 本来就是 null），
     * 而且客户端拆解只是本地预测，真拆是服务端做的，响也该由它响。</p>
     */
    private void knock() {
        if (this.origin == null) {
            return;
        }

        World world = this.playerInventory.player.getWorld();
        if (world.isClient) {
            return;
        }

        world.playSound(null,
                this.origin.getX() + 0.5D, this.origin.getY() + 0.5D, this.origin.getZ() + 0.5D,
                ModSounds.STATION_HAMMER, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }

    // ==================== Shift+点击的搬运 ====================

    /**
     * Shift+点击时把东西在台子与背包之间挪。
     *
     * <p><b>六格必须单独处理，而且必须在"塞到玩家背包"这条路上被排除在外。</b>
     * 理由见类文档顶部第 1 条：原版那段"合并同款"的逻辑不检查能不能放，
     * 落在只读视图上会把玩家手里的东西清零。所以这里：</p>
     *
     * <ul>
     *   <li>点的是六格 → 走视图自己的取出（等于拆下），再塞进背包；塞不下就丢在玩家脚边，
     *       绝不留一半；</li>
     *   <li>点的是成品栏 → 把这一格的东西交给玩家（"装"的时候顺带扣掉三样输入，
     *       <b>不等框架叫回调</b>，因为 Shift+点击这条路上框架不保证会叫）；</li>
     *   <li>点的是前三格 → 挪回背包；</li>
     *   <li>点的是背包 → 只往<b>前三格</b>里放，<b>范围不含六格与成品栏</b>。</li>
     * </ul>
     *
     * @param player 操作的玩家
     * @param index  被点击的格子
     * @return 没能搬走、留在原格的东西（搬空了返回空）
     */
    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }

        if (isAttachmentSlot(index)) {
            return quickMoveAttachment(player, index);
        }

        if (index == RelicStationInventory.SLOT_RESULT) {
            return quickMoveResult(player);
        }

        Slot slot = this.slots.get(index);
        if (!slot.hasStack()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();

        if (index < STATION_SLOTS) {
            // 从前三格挪回背包
            if (!this.insertItem(stack, STATION_SLOTS, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 从背包挪上台子：**只到前三格为止**，六格与成品栏不在范围内。
            // 这条边界是防"吞东西"的关键，改动它之前请先读类文档顶部第 1 条。
            if (!this.insertItem(stack, 0, RelicStationInventory.INPUT_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }

        return original;
    }

    /**
     * 这一格是不是右边那六格之一。
     *
     * @param index 格子编号
     * @return 是六格之一时返回 {@code true}
     */
    private static boolean isAttachmentSlot(int index) {
        return index >= RelicStationInventory.SIZE && index < STATION_SLOTS;
    }

    /**
     * Shift+点击六格：拆下一枚、塞进背包。
     *
     * <p>拆下来的遗物如果背包塞不下，就丢在玩家脚边——<b>绝不返回"没搬走"的非空堆</b>。
     * 原版的 Shift+点击是一个循环（"只要源格还是原来那件就再来一次"），而六格拆掉一枚之后
     * 后面一枚会顶上来；如果这里返回非空堆而又没真的搬走，那个循环可能一直转下去。
     * 因此要么搬进背包、要么丢出去，然后一律返回空。</p>
     *
     * @param player 玩家
     * @param index  六格里的第几格
     * @return 恒为空
     */
    private ItemStack quickMoveAttachment(PlayerEntity player, int index) {
        ItemStack detached = this.attachments.removeStack(index - RelicStationInventory.SIZE, 1);
        if (detached.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (!this.insertItem(detached, STATION_SLOTS, this.slots.size(), true) && !detached.isEmpty()) {
            player.dropItem(detached, false);
        }

        return ItemStack.EMPTY;
    }

    /**
     * Shift+点击成品栏：把这一格里的东西交给玩家。
     *
     * <p>不依赖框架在 Shift+点击时会调用这一格的任何回调（那条路不保证会叫），这里自己把东西
     * 塞进背包（塞不下就丢在玩家脚边），然后<b>真正把它从格子里取走</b>：
     * "装"的时候该扣的三样输入由库存类在"成品栏被清空"那一刻结算，这里不用再管；
     * "拆"的时候那本来就是玩家自己的成品，拿走什么也不欠。</p>
     *
     * @param player 玩家
     * @return 恒为空
     */
    private ItemStack quickMoveResult(PlayerEntity player) {
        ItemStack result = this.contents.getStack(RelicStationInventory.SLOT_RESULT);
        if (result.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack moving = result.copy();
        if (!this.insertItem(moving, STATION_SLOTS, this.slots.size(), true) && !moving.isEmpty()) {
            player.dropItem(moving, false);
        }

        // 取走成品栏 = 这一格被清空：库存类据此结算（在"装"模式下扣掉工件 / 遗物 / 辅料各一样并敲一声）
        this.contents.removeStack(RelicStationInventory.SLOT_RESULT, 1);
        return ItemStack.EMPTY;
    }

    /**
     * 界面还能不能用：台子还在原地、而且玩家没走远。
     *
     * <p>照原版工作台的尺度 —— 距离超过 8 格（平方 64）就作废。台子被拆掉时这一条也会失效，
     * 服务端随即关闭界面，而关闭会把东西退还玩家（见 {@link #onClosed}），东西不会丢。</p>
     *
     * <p>客户端手上没有台子位置，直接放行：能不能用由服务端说了算，客户端不为此提前关界面。</p>
     *
     * @param player 玩家
     * @return 台子还在且玩家在 8 格之内时返回 {@code true}
     */
    @Override
    public boolean canUse(PlayerEntity player) {
        if (this.origin == null) {
            return true;
        }

        World world = player.getWorld();
        return world.getBlockState(this.origin).isOf(ModBlocks.RELIC_STATION)
                && player.squaredDistanceTo(Vec3d.ofCenter(this.origin)) <= 64.0D;
    }
}
