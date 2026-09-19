package org.eternalrelic.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import org.eternalrelic.block.RelicStationAttachments;
import org.eternalrelic.block.RelicStationBlockEntity;
import org.eternalrelic.registry.ModScreens;
import org.eternalrelic.relic.RelicAttachment;
import org.eternalrelic.relic.RelicAttachRule;

/**
 * 遗物装卸台的界面容器 —— 全项目最需要小心的一处。
 *
 * <p>界面上四格是"干活的地方"，右边六格是<b>工件身上已附遗物的实时视图</b>：</p>
 *
 * <pre>
 *   装备(15,15)                        ┌─ 六格围着"装备"那格排一圈 ─┐
 *   遗物(15,52)  辅料(41,52)  →  成品(65,35)
 * </pre>
 *
 * <p>装备在<b>左上角</b>、成品在<b>正中</b>（按制作者画界面时的设计）。要拆遗物就把装备放进左上那格，
 * 从六格里把遗物拖出来。六格读的正是左上那件装备。</p>
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
 *   <li><b>预览格只许台子写。</b>{@code canInsert} 恒假，于是这一格里出现的东西<b>必然是台子摆的</b>，
 *       不需要再靠"内容比对"去猜（第一版正是靠猜，结果两个方向都会错：既能白拿，也会白扣材料）。</li>
 *   <li><b>"拿走成品要扣什么"挂在"预览格被清空"那一刻</b>（在 {@code RelicStationBlockEntity} 里），
 *       而不是挂在某个界面回调上 —— 原版取走东西有好几条路（鼠标直接拿、<b>按 Q 丢弃</b>、
 *       <b>双击收集</b>、Shift+点击、按 1~9），其中有的<b>根本不叫回调</b>，
 *       挂在回调上必然漏掉一条、留下白拿的口子。</li>
 * </ol>
 */
public class RelicStationScreenHandler extends ScreenHandler {

    /** 台子这边一共有几格：四格工作区 + 六格附着视图。 */
    public static final int STATION_SLOTS = 4 + RelicAttachment.MAX_ATTACHMENTS;

    /** 六格附着视图的位置，围着工件排成一圈（与界面美术一致）。 */
    private static final int[][] ATTACHMENT_POSITIONS = {
            { 77, 8 }, { 127, 6 }, { 147, 20 }, { 151, 41 }, { 143, 61 }, { 88, 60 }
    };

    // ---- 四个工作格的位置。**按制作者原本的设计摆**：左上放装备、中下放辅料、左下放遗物、正中是成品 ----
    //
    // ⚠️ 改这几个坐标是安全的（只影响"格子画在哪"），但**不要顺手改格子的含义**：
    // 六格附着视图必须读"装备"那一格（SLOT_TOOL）——它是玩家真的能放东西进去的真实物品格，
    // 读成品那一格会让预览一出现就能被人白拿（详见类文档与设计决策 39）。

    private static final int TOOL_X = 15;
    private static final int TOOL_Y = 15;
    private static final int MATERIAL_X = 41;
    private static final int MATERIAL_Y = 52;
    private static final int RELIC_X = 15;
    private static final int RELIC_Y = 52;
    private static final int PREVIEW_X = 65;
    private static final int PREVIEW_Y = 35;

    /** 服务端才有；客户端为 {@code null}（客户端只按同步过来的内容摆样子）。 */
    private final RelicStationBlockEntity station;

    /** 四个工作格的存放处：服务端就是方块实体本身，客户端是一份可写的镜像。 */
    private final Inventory backing;

    /** 六格的来源：服务端是工件身上那份记录的实时视图，客户端是一份可写的镜像。 */
    private final Inventory attachments;

    /**
     * 服务端用的构造函数。
     *
     * @param syncId          同步编号
     * @param playerInventory 玩家背包
     * @param station         台子的方块实体
     */
    public RelicStationScreenHandler(int syncId, PlayerInventory playerInventory, RelicStationBlockEntity station) {
        this(syncId, playerInventory, station, new RelicStationAttachments(station));
    }

    /**
     * 客户端用的构造函数。
     *
     * <p>客户端拿不到方块实体，也不需要——它只要有一份<b>可写</b>的镜像，让服务端同步过来的内容
     * 能落进去就行。六格在服务端是"只读视图"，在客户端必须换成普通库存：视图的 {@code setStack}
     * 会忽略写入，同步过来的内容就落不进去，玩家会看到六个空格子。</p>
     *
     * @param syncId          同步编号
     * @param playerInventory 玩家背包
     */
    public RelicStationScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, null, new SimpleInventory(RelicAttachment.MAX_ATTACHMENTS));
    }

    /**
     * 两边共用的摆格子流程 —— 服务端与客户端走的是同一段代码，只是存放处不同。
     *
     * @param syncId          同步编号
     * @param playerInventory 玩家背包
     * @param station         台子方块实体；客户端传 {@code null}
     * @param attachments     六格的来源
     */
    private RelicStationScreenHandler(int syncId, PlayerInventory playerInventory,
            RelicStationBlockEntity station, Inventory attachments) {
        super(ModScreens.RELIC_STATION, syncId);

        this.station = station;
        this.backing = station != null ? station : new SimpleInventory(RelicStationBlockEntity.SIZE);
        this.attachments = attachments;

        addToolSlot();
        addMaterialSlot();
        addRelicSlot();
        addPreviewSlot();
        addAttachmentSlots();
        addPlayerSlots(playerInventory);
    }

    // ==================== 摆格子 ====================

    /**
     * 工件格 —— 界面正中间那一格，六格附着视图就是从它读出来的。
     *
     * <p>收"能被附的"与"身上已经附了东西的"两种：后者是为了让人<b>总能把拆下来的遗物拿回去</b>——
     * 万一以后可附名单改了，一件已经附了遗物的装备也必须还能放进台子拆开。</p>
     */
    private void addToolSlot() {
        this.addSlot(new Slot(this.backing, RelicStationBlockEntity.SLOT_TOOL, TOOL_X, TOOL_Y) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return RelicAttachRule.canBeWorkedOn(stack);
            }
        });
    }

    /** 辅料格：只收某件遗物指定的那种辅料。 */
    private void addMaterialSlot() {
        this.addSlot(new Slot(this.backing, RelicStationBlockEntity.SLOT_MATERIAL, MATERIAL_X, MATERIAL_Y) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return RelicAttachRule.isMaterialItem(stack.getItem());
            }
        });
    }

    /** 遗物格：只收登记在可附白名单里的遗物。 */
    private void addRelicSlot() {
        this.addSlot(new Slot(this.backing, RelicStationBlockEntity.SLOT_RELIC, RELIC_X, RELIC_Y) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return RelicAttachRule.isRelicItem(stack.getItem());
            }
        });
    }

    /**
     * 成品预览格 —— <b>只许台子写，玩家放不进任何东西</b>。
     *
     * <p>这一条是整个界面能站得住脚的前提：只要玩家塞不进来，这一格里出现的东西就<b>必然是台子
     * 自己摆的</b>，于是"拿走它"永远等于"拿走成品"，不需要任何猜测。第一版没有这道闸，
     * 只能靠"内容是否一样"去反推，结果既能被人白拿预览，也会把玩家自己放进去拆解的装备误判成
     * 台子摆的、白扣掉三样材料。</p>
     *
     * <p><b>拿走时要扣的三样输入，不在这里扣</b> —— 扣在 {@code RelicStationBlockEntity} 里
     * "预览格被清空"那一刻。原因是原版把东西取走有好几条路（鼠标直接拿、按 Q 丢弃、双击收集、
     * Shift+点击、按 1~9），<b>其中有的根本不会调用这一格的任何界面回调</b>，
     * 把扣材料挂在这里必漏。详见方块实体里的说明。</p>
     */
    private void addPreviewSlot() {
        this.addSlot(new Slot(this.backing, RelicStationBlockEntity.SLOT_PREVIEW, PREVIEW_X, PREVIEW_Y) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return false;
            }
        });
    }

    /**
     * 右边六格 —— 工件身上已附遗物的实时视图。
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

    // ==================== 预览 ====================

    /**
     * 每次点过格子之后重算一次预览。
     *
     * <p>原版容器并不会自动收到"库存变了"的通知——那要自己接回调。这里换个更省事的挂法：
     * 玩家的每一次取放都要经过 {@link #onSlotClick}，所以在它之后算一遍就够了，
     * 而且顺序正好：先应用玩家的操作，再据此更新预览。</p>
     */
    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        super.onSlotClick(slotIndex, button, actionType, player);
        updateResult();
    }

    /**
     * 按当前三格算出成品，摆到预览格里 —— <b>这一步只显示，不扣任何东西</b>。
     *
     * <p>不需要记"这份是不是我摆的"：预览格 {@code canInsert} 恒假，里面除了台子摆的东西
     * 不可能是别的。所以每次直接按当前输入重算、整体覆盖即可，哪怕玩家把输入换来换去也不会错。</p>
     */
    private void updateResult() {
        if (this.station == null || this.station.getWorld() == null || this.station.getWorld().isClient) {
            return;
        }

        ItemStack produced = RelicAttachRule.previewOf(
                this.station.getStack(RelicStationBlockEntity.SLOT_MATERIAL),
                this.station.getStack(RelicStationBlockEntity.SLOT_TOOL),
                this.station.getStack(RelicStationBlockEntity.SLOT_RELIC));

        ItemStack current = this.station.getStack(RelicStationBlockEntity.SLOT_PREVIEW);

        // 内容没变就不动它：频繁 setStack 会让这一格每刻都闪。
        // 注意走的是 setPreview 而不是 setStack —— 那条路会被当成"玩家把成品拿走了"。
        if (ItemStack.areEqual(current, produced)) {
            return;
        }

        this.station.setPreview(produced);
    }

    /**
     * 界面关掉时把预览撤掉。
     *
     * <p>输入还留在台子里（下次打开会自动重算出一份新的预览），但没被拿走的那份预览不该留着——
     * 它不进存档、也不掉落，留着只会让人以为台子里"本来就有东西"。</p>
     */
    @Override
    public void onClosed(PlayerEntity player) {
        if (this.station != null && this.station.getWorld() != null && !this.station.getWorld().isClient) {
            this.station.clearPreview();
        }

        super.onClosed(player);
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
     *   <li>点的是预览格 → 自己把成品给玩家并扣掉三样输入（<b>不等框架叫回调</b>，
     *       因为 Shift+点击这条路上框架不保证会叫）；</li>
     *   <li>点的是前三格 → 挪回背包；</li>
     *   <li>点的是背包 → 只往<b>前三格</b>里放，<b>范围不含六格与预览格</b>。</li>
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

        if (this.station != null && isAttachmentSlot(index)) {
            return quickMoveAttachment(player, index);
        }

        if (this.station != null && index == RelicStationBlockEntity.SLOT_PREVIEW) {
            return quickMovePreview(player, index);
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
            // 从背包挪上台子：**只到前三格为止**，六格与预览格不在范围内。
            // 这条边界是防"吞东西"的关键，改动它之前请先读类文档顶部第 1 条。
            if (!this.insertItem(stack, 0, RelicStationBlockEntity.PERSISTED_SLOTS, false)) {
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
        return index >= RelicStationBlockEntity.SIZE && index < STATION_SLOTS;
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
        ItemStack detached = this.attachments.removeStack(index - RelicStationBlockEntity.SIZE, 1);
        if (detached.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (!this.insertItem(detached, STATION_SLOTS, this.slots.size(), true) && !detached.isEmpty()) {
            player.dropItem(detached, false);
        }

        return ItemStack.EMPTY;
    }

    /**
     * Shift+点击预览格：把成品给玩家。
     *
     * <p>不依赖框架在 Shift+点击时会调用预览格的任何回调（那条路不保证会叫），这里自己把成品
     * 塞进背包（塞不下就丢在玩家脚边），然后<b>真正把预览从格子里取走</b> ——
     * 扣三样输入这件事由方块实体在"预览格被清空"那一刻完成，这里不用再管。</p>
     *
     * @param player 玩家
     * @param index  预览格编号
     * @return 恒为空
     */
    private ItemStack quickMovePreview(PlayerEntity player, int index) {
        ItemStack preview = this.station.getStack(RelicStationBlockEntity.SLOT_PREVIEW);
        if (preview.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack moving = preview.copy();
        if (!this.insertItem(moving, STATION_SLOTS, this.slots.size(), true) && !moving.isEmpty()) {
            player.dropItem(moving, false);
        }

        // 取走预览 = 拿走成品：方块实体在这一下里扣掉工件 / 遗物 / 辅料各一样并敲一声
        this.station.removeStack(RelicStationBlockEntity.SLOT_PREVIEW, 1);
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.station == null || this.station.canPlayerUse(player);
    }
}
