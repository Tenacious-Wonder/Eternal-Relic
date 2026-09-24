package org.eternalrelic.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import org.eternalrelic.block.entity.ChestplateStationBlockEntity;
import org.eternalrelic.registry.ModBlocks;
import org.eternalrelic.registry.ModScreens;
import org.eternalrelic.registry.ModSounds;
import org.eternalrelic.relic.RelicAttachment;
import org.eternalrelic.relic.RelicAttachRule;

/**
 * 胸甲台的界面容器 —— 摆格子、接住箭头那一下。
 *
 * <p><b>界面上一共九格</b>，位置与制作者画的那张底图一一对应：</p>
 *
 * <pre>
 *        配件槽  配件槽            正中是「台上的胸甲」
 *        配件槽  配件槽            两侧各三个配件槽，围着它排开
 *        配件槽  配件槽
 *
 *               [ ↑ 箭头 ]
 *               配件格
 *               配料格
 * </pre>
 *
 * <p><b>「装」只有一个入口：点箭头。</b>玩家把配件放进配件格、辅料放进配料格，点一下箭头，
 * 三样（台上的胸甲 + 配件 + 辅料）就凑成一次装配。装配的判定与成品<b>不在这里</b>，
 * 而是问 {@link RelicAttachRule}——与锻造台、遗物装卸台共用同一套裁决，
 * 免得两条路的判定慢慢分叉。</p>
 *
 * <p><b>「拆」也只有一个入口：从配件槽里拿走一枚。</b>那六格是台上胸甲的实时视图
 * （见 {@link ChestplateStationAttachments}），拿走即拆下。</p>
 *
 * <p><b>⚠️ 六格必须排除在一切"通用搬运"之外</b>（见 {@link #quickMove}）：原版搬运逻辑里有一段
 * "合并同款物品"，它<b>不检查能不能放</b>，只比对物品是否相同就把玩家手里那叠东西清零、
 * 去改一个临时对象——落到只读视图上就是玩家东西凭空消失。遗物装卸台那边已经踩过这个坑。</p>
 *
 * <p><b>台子本身什么也不存</b>（除了台上那件胸甲）：配件格与配料格是随这次界面会话而生的原料盘，
 * 关掉界面就退还玩家（见 {@link #onClosed}）。</p>
 */
public class ChestplateStationScreenHandler extends ScreenHandler {

    /** 点箭头：把配料格与配件格的东西装到台上的胸甲上。 */
    public static final int BUTTON_APPLY = 0;

    /** 台子这边一共有几格：装备格 + 六格配件槽 + 配件格 + 配料格。 */
    public static final int STATION_SLOTS = 1 + RelicAttachment.MAX_ATTACHMENTS + ChestplateStationInventory.SIZE;

    // ---- 槽位坐标。**按制作者那张界面底图上画的位置摆**，改这几个数只影响"格子画在哪" ----

    /**
     * 槽位坐标的统一偏移。
     *
     * <p>底图上画的格子填充范围是：配件槽 x 31~46 与 x 129~144、装备格与两个原料格 x 80~95；
     * 而物品图标是按槽位左上角画的，与底图上画的格子对不齐。这里统一补回来，
     * 以后要微调也只动这一个数——这个值是制作者看着实际画面定的。</p>
     */
    private static final int SLOT_SHIFT = 1;

    /** 台上那件胸甲摆在哪（正中被六个配件槽围着的那一格）。 */
    private static final int CHESTPLATE_X = 79 + SLOT_SHIFT;
    private static final int CHESTPLATE_Y = 53 + SLOT_SHIFT;

    /** 六个配件槽：左列三个、右列三个，自上而下。 */
    private static final int[][] ATTACHMENT_POSITIONS = {
            { 30 + SLOT_SHIFT, 22 + SLOT_SHIFT }, { 30 + SLOT_SHIFT, 53 + SLOT_SHIFT },
            { 30 + SLOT_SHIFT, 82 + SLOT_SHIFT },
            { 128 + SLOT_SHIFT, 22 + SLOT_SHIFT }, { 128 + SLOT_SHIFT, 53 + SLOT_SHIFT },
            { 128 + SLOT_SHIFT, 82 + SLOT_SHIFT }
    };

    /** 配件格（要装上去的那一件）。 */
    private static final int FITTING_X = 79 + SLOT_SHIFT;
    private static final int FITTING_Y = 90 + SLOT_SHIFT;

    /** 配料格（装它要用的辅料）。 */
    private static final int MATERIAL_X = 79 + SLOT_SHIFT;
    private static final int MATERIAL_Y = 116 + SLOT_SHIFT;

    /** 界面里的两个原料格：随这次会话而生，关掉界面就退还玩家。 */
    private final ChestplateStationInventory contents;

    /** 六个配件槽的来源：台上那件胸甲的实时视图。 */
    private final Inventory attachments;

    /** 台上那件胸甲 —— 装配与拆卸动的就是它。 */
    private final ChestplateStationBlockEntity station;

    /** 玩家背包 —— 退还原料、播放敲击声都要经过它。 */
    private final PlayerInventory playerInventory;

    /** 台子的位置；界面远端时可能为 {@code null}。 */
    private final BlockPos origin;

    /**
     * 服务端与客户端共用的构造函数 —— 两端都从世界里取出那个方块实体，
     * 区别只在位置是怎么来的（服务端由方块给出，客户端由打开界面时下发的那份数据给出）。
     *
     * @param syncId          同步编号
     * @param playerInventory 玩家背包
     * @param pos             台子的位置
     */
    public ChestplateStationScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos) {
        super(ModScreens.CHESTPLATE_STATION, syncId);

        this.playerInventory = playerInventory;
        this.origin = pos;

        World world = playerInventory.player.getWorld();
        this.station = world.getBlockEntity(pos) instanceof ChestplateStationBlockEntity found ? found : null;

        this.contents = new ChestplateStationInventory();
        this.attachments = new ChestplateStationAttachments(this.station, this::knock);

        addChestplateSlot();
        addAttachmentSlots();
        addFittingSlot();
        addMaterialSlot();
        addPlayerSlots(playerInventory);
    }

    // ==================== 摆格子 ====================

    /**
     * 装备格 —— 正中被六个配件槽围着的那一格，显示台上那件胸甲。
     *
     * <p><b>能拿走，不能放。</b>从这里把胸甲拖走，等于把它从台子上取下来——与对着台子按住 Shift
     * 右键是同一件事，两条路都收口到方块实体那一处。但<b>往这一格里放东西会被拒绝</b>：
     * 放装备请右键台子；留这条口子会让"台上现在放着什么"出现两个说法。</p>
     */
    private void addChestplateSlot() {
        this.addSlot(new Slot(new DisplayedChestplate(this.station), 0, CHESTPLATE_X, CHESTPLATE_Y) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return false;
            }
        });
    }

    /**
     * 六个配件槽 —— 台上那件胸甲已装配件的实时视图，拿走一枚即拆下。
     *
     * <p>取出由视图自己负责（见 {@link ChestplateStationAttachments}），这里只摆位置。</p>
     */
    private void addAttachmentSlots() {
        for (int i = 0; i < ATTACHMENT_POSITIONS.length; i++) {
            this.addSlot(new Slot(this.attachments, i, ATTACHMENT_POSITIONS[i][0], ATTACHMENT_POSITIONS[i][1]));
        }
    }

    /**
     * 配件格 —— 只收"能装到台上那件胸甲上的东西"。
     *
     * <p>判断直接问 {@link RelicAttachRule#canAttach}：它一并管了"登记过没有、是不是同一类目标、
     * 有没有重复、有没有超过六枚上限"，因此这里不必再抄一遍规矩。</p>
     */
    private void addFittingSlot() {
        this.addSlot(new Slot(this.contents, ChestplateStationInventory.SLOT_FITTING, FITTING_X, FITTING_Y) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return RelicAttachRule.canAttach(chestplate(), stack);
            }
        });
    }

    /** 配料格 —— 只收某件配件指定的那种辅料，与遗物装卸台同一条规矩。 */
    private void addMaterialSlot() {
        this.addSlot(new Slot(this.contents, ChestplateStationInventory.SLOT_MATERIAL, MATERIAL_X, MATERIAL_Y) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return RelicAttachRule.isMaterialItem(stack.getItem());
            }
        });
    }

    /**
     * 玩家背包那 36 格，位置与界面底图上画的一致。
     *
     * @param playerInventory 玩家背包
     */
    private void addPlayerSlots(PlayerInventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        7 + SLOT_SHIFT + column * 18, 139 + SLOT_SHIFT + row * 18));
            }
        }

        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column,
                    7 + SLOT_SHIFT + column * 18, 197 + SLOT_SHIFT));
        }
    }

    // ==================== 箭头那一下 ====================

    /**
     * 箭头被点了一下。
     *
     * <p>客户端点箭头只是发一个包过来（见 {@code client.screen.ChestplateStationScreen}），
     * 原版把它转发到服务端才调到这里，因此"扣原料、改胸甲"都发生在说了算的那一边。</p>
     *
     * @return 是否受理了这次点击；不认识的按钮交回父类
     */
    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id == BUTTON_APPLY) {
            applyFitting();
            return true;
        }

        return super.onButtonClick(player, id);
    }

    /**
     * 把配料格与配件格的东西装到台上的胸甲上 —— <b>这一步才真正扣原料</b>。
     *
     * <p>判定与成品都问 {@link RelicAttachRule}：它给出的是"胸甲的一份副本，只是数据里多记了一枚配件"，
     * 因此原胸甲上的附魔、耐久、自定义名字都跟着走。凑不齐或规则不允许时它返回空，这里就什么也不做
     * ——原料原样留在格子里，玩家可以继续调整。</p>
     */
    private void applyFitting() {
        ItemStack produced = RelicAttachRule.previewOf(
                this.contents.getStack(ChestplateStationInventory.SLOT_MATERIAL),
                chestplate(),
                this.contents.getStack(ChestplateStationInventory.SLOT_FITTING));

        if (produced.isEmpty() || this.station == null) {
            return;
        }

        this.station.setChestplate(produced);
        this.contents.removeStack(ChestplateStationInventory.SLOT_FITTING, 1);
        this.contents.removeStack(ChestplateStationInventory.SLOT_MATERIAL, 1);

        knock();
    }

    /**
     * @return 台上那件胸甲；台上空着（或界面远端拿不到方块实体）时返回空堆
     */
    private ItemStack chestplate() {
        return this.station == null ? ItemStack.EMPTY : this.station.getChestplate();
    }

    // ==================== 关界面时退还原料 ====================

    /**
     * 界面关掉时，把两个原料格里属于玩家的东西还给他。
     *
     * <p><b>为什么要还</b>：这两格只活在这一次会话里，不还就等于凭空吞掉玩家的配件与辅料——
     * 这正是原版工作台的规矩。台上那件胸甲不在此列：它在方块实体里，本来就还在台子上。</p>
     *
     * <p>只在服务端做：客户端也会收到这次关闭，若两边各退一次，玩家会先看到背包里多一份。</p>
     */
    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);

        if (player.getWorld().isClient) {
            return;
        }

        for (int slot = 0; slot < ChestplateStationInventory.SIZE; slot++) {
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
     * 在台子那一格的位置敲一下 —— 装上或拆下一枚配件时各响一次。
     *
     * <p>由服务端发声，附近的人才能同时听到同一个声音。</p>
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
     * <p><b>六格必须单独处理，而且必须从"塞到玩家背包"那条路上被排除在外。</b>
     * 理由见类文档：原版那段"合并同款"不检查能不能放，落在只读视图上会把玩家手里的东西清零。因此：</p>
     *
     * <ul>
     *   <li>点的是六格 → 走视图自己的取出（等于拆下），再塞进背包；塞不下就丢在玩家脚边，绝不留一半；</li>
     *   <li>点的是装备格 → 拿不走（那一格只读），直接返回；</li>
     *   <li>点的是两个原料格 → 挪回背包；</li>
     *   <li>点的是背包 → 只往那两个原料格放，<b>范围不含六格与装备格</b>。</li>
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

        Slot slot = this.slots.get(index);
        if (!slot.hasStack()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();

        if (index < STATION_SLOTS) {
            // 从台子这边挪回背包；正中的胸甲也走这条路——挪走就等于把它从台子上取下来
            if (!slot.canTakeItems(player)) {
                return ItemStack.EMPTY;
            }

            if (!this.insertItem(stack, STATION_SLOTS, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 从背包挪上台子：**只到配件格与配料格为止**，六格与装备格不在范围内。
            // 这条边界是防"吞东西"的关键，改动它之前请先读类文档。
            if (!this.insertItem(stack, 0, STATION_SLOTS, false)) {
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
     * 这一格是不是六个配件槽之一。
     *
     * @param index 格子编号
     * @return 是六格之一时返回 {@code true}
     */
    private static boolean isAttachmentSlot(int index) {
        return index >= 1 && index <= RelicAttachment.MAX_ATTACHMENTS;
    }

    /**
     * Shift+点击配件槽：拆下一枚、塞进背包。
     *
     * <p>拆下来的配件如果背包塞不下，就丢在玩家脚边——<b>绝不返回"没搬走"的非空堆</b>。
     * 原版的 Shift+点击是一个循环（"只要源格还是原来那件就再来一次"），而六格拆掉一枚之后
     * 后面一枚会顶上来；如果这里返回非空堆而又没真的搬走，那个循环可能一直转下去。</p>
     *
     * @param player 玩家
     * @param index  六格里的第几格
     * @return 恒为空
     */
    private ItemStack quickMoveAttachment(PlayerEntity player, int index) {
        ItemStack detached = this.attachments.removeStack(index - 1, 1);
        if (detached.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (!this.insertItem(detached, STATION_SLOTS, this.slots.size(), true) && !detached.isEmpty()) {
            player.dropItem(detached, false);
        }

        return ItemStack.EMPTY;
    }

    /**
     * 界面还能不能用：台子还在原地、而且玩家没走远。
     *
     * <p>照原版工作台的尺度 —— 距离超过 8 格（平方 64）就作废。台子被拆掉时这一条也会失效，
     * 服务端随即关闭界面，而关闭会把原料退还玩家（见 {@link #onClosed}），东西不会丢。</p>
     */
    @Override
    public boolean canUse(PlayerEntity player) {
        if (this.origin == null) {
            return true;
        }

        World world = player.getWorld();
        return world.getBlockState(this.origin).isOf(ModBlocks.CHESTPLATE_STATION)
                && player.squaredDistanceTo(Vec3d.ofCenter(this.origin)) <= 64.0D;
    }

    /**
     * 装备格的内容 —— 台上那件胸甲的只读视图。
     *
     * <p>它与六个配件槽一样是"视图"而不是"库房"：读的就是方块实体里那一份，因此台上换了胸甲，
     * 界面里跟着就换。写入一律忽略、也拿不走——要取下胸甲请对着台子按住 Shift 右键。</p>
     */
    private static final class DisplayedChestplate implements Inventory {

        /** 台上那件胸甲的来源；界面远端拿不到方块实体时为 {@code null}。 */
        private final ChestplateStationBlockEntity station;

        private DisplayedChestplate(ChestplateStationBlockEntity station) {
            this.station = station;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return getStack(0).isEmpty();
        }

        @Override
        public ItemStack getStack(int slot) {
            return this.station == null ? ItemStack.EMPTY : this.station.getChestplate();
        }

        @Override
        public ItemStack removeStack(int slot, int amount) {
            return this.station == null ? ItemStack.EMPTY : this.station.takeChestplate();
        }

        @Override
        public ItemStack removeStack(int slot) {
            return removeStack(slot, 1);
        }

        @Override
        public void setStack(int slot, ItemStack stack) {
        }

        @Override
        public boolean isValid(int slot, ItemStack stack) {
            return false;
        }

        @Override
        public void markDirty() {
        }

        @Override
        public boolean canPlayerUse(PlayerEntity player) {
            return true;
        }

        @Override
        public void clear() {
        }
    }
}
