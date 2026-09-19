package org.eternalrelic.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import org.eternalrelic.registry.ModBlocks;
import org.eternalrelic.registry.ModSounds;
import org.eternalrelic.screen.RelicStationScreenHandler;

/**
 * 遗物装卸台的方块实体 —— 它就是那个"放东西的地方"。
 *
 * <p>四个格子，装与拆共用其中的一部分：</p>
 *
 * <ul>
 *   <li>{@link #SLOT_TOOL} —— <b>工件</b>：要被附上遗物的工具 / 装备，也是拆解时放的那一件。
 *       界面上它位于正中间，那六格附着视图就是从它读出来的；</li>
 *   <li>{@link #SLOT_MATERIAL} —— 附着所需的辅料（附纹章时是蜜脾）；只在附着时用到；</li>
 *   <li>{@link #SLOT_RELIC} —— 要附上去的遗物；只在附着时用到；</li>
 *   <li>{@link #SLOT_PREVIEW} —— 台子自己摆出来的<b>成品预览</b>。它不是玩家的东西，</li>
 * </ul>
 *
 * <p><b>预览这一格有三条硬规矩，缺一条就会变成"刷物品机"</b>：</p>
 *
 * <ol>
 *   <li><b>不进存档</b>（{@link #writeNbt} 只写前三格）—— 否则摆好材料、关掉界面、再拆掉台子，
 *       那份预览就会掉出来白送给玩家；</li>
 *   <li><b>不进掉落物</b>（见 {@code RelicStationBlock.onStateReplaced}）；</li>
 *   <li><b>自动化设备一概碰不到</b> —— 本类实现 {@link SidedInventory} 且
 *       {@link #getAvailableSlots} 返回空表，于是漏斗与其它模组的管道<b>一个格子都拿不到</b>。
 *       不这么做的话，漏斗能把预览抽走，而界面一刷新又会重新摆出一份，同样是白送。</li>
 * </ol>
 *
 * <p>台子与"配方"无关：怎么装、怎么拆由界面容器与 {@code RelicAttachRule} 决定，
 * 这里只负责把东西存住、并且存进存档里。</p>
 */
public class RelicStationBlockEntity extends BlockEntity implements Inventory, SidedInventory,
        NamedScreenHandlerFactory {

    /** 工件：被附着的工具 / 装备，拆解时也放这一格。界面上位于正中间。 */
    public static final int SLOT_TOOL = 0;

    /** 附着所需的辅料。 */
    public static final int SLOT_MATERIAL = 1;

    /** 要附上去的遗物。 */
    public static final int SLOT_RELIC = 2;

    /** 台子自己摆出来的成品预览。**不是玩家的东西**，不进存档、不掉落。 */
    public static final int SLOT_PREVIEW = 3;

    /** 会写进存档的格子数 —— 正好是预览之前的那几格。 */
    public static final int PERSISTED_SLOTS = SLOT_PREVIEW;

    /** 一共几个格子。 */
    public static final int SIZE = 4;

    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);

    public RelicStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.RELIC_STATION_ENTITY, pos, state);
    }

    /**
     * 敲一下：让附近的玩家都听见。
     *
     * <p>只在服务端调用——由服务端发声，附近的人才能同时听到同一个声音。
     * 台子本身不会动（外观是普通方块模型），这一声就是它"出手了"的全部表示。</p>
     */
    public void knock() {
        if (this.world == null || this.world.isClient) {
            return;
        }

        this.world.playSound(null,
                this.pos.getX() + 0.5D, this.pos.getY() + 0.5D, this.pos.getZ() + 0.5D,
                ModSounds.STATION_HAMMER, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }

    /**
     * 这一格是不是台子自己摆的预览。
     *
     * @param slot 格子编号
     * @return 是预览格时返回 {@code true}
     */
    public static boolean isPreviewSlot(int slot) {
        return slot == SLOT_PREVIEW;
    }

    /** 台子自己在摆放/撤下预览时置位 —— 用来把"台子自己改预览"与"玩家把预览拿走"分开。 */
    private boolean writingPreviewInternally;

    /**
     * 由界面容器调用：台子自己摆一份成品预览（传空就是撤下）。
     *
     * <p><b>为什么预览要有专门的写入方法，而不是让界面直接写格子：</b>因为"预览被清空"这件事
     * 在本类里有一个特殊含义 —— <b>玩家把成品拿走了，该扣三样输入</b>（见
     * {@link #onPreviewTaken()}）。台子自己在刷新预览时也会清空它，那不是"拿走"。
     * 用这个内部标志把两者分开，就不必去猜、也不会误扣。</p>
     *
     * @param stack 要摆上去的成品；空表示撤下
     */
    public void setPreview(ItemStack stack) {
        this.writingPreviewInternally = true;
        try {
            this.items.set(SLOT_PREVIEW, stack);
            this.markDirty();
        } finally {
            this.writingPreviewInternally = false;
        }
    }

    /**
     * 把预览那一格清掉。
     *
     * <p>输入被取走、或界面关闭之后调用，免得一份没人要的预览一直摆在那里。
     * 走 {@link #setPreview}，因此不会被当成"玩家拿走了成品"。</p>
     */
    public void clearPreview() {
        if (!this.items.get(SLOT_PREVIEW).isEmpty()) {
            setPreview(ItemStack.EMPTY);
        }
    }

    /**
     * <b>预览被拿走了 —— 扣掉工件、遗物、辅料各一样，并敲一下。</b>
     *
     * <p>⚠️ <b>为什么必须挂在这里，而不是挂在界面的"拿走回调"上：</b>原版把东西从一格取走有好几条
     * 互不相同的路 —— 鼠标直接拿会叫 {@code onTakeItem}，而<b>按 Q 丢弃</b>与<b>双击收集</b>只调用
     * {@code takeStackRange}/{@code removeStack}，<b>根本不叫那个回调</b>。挂在回调上就会漏掉后两条，
     * 于是"按 Q 把预览丢出来"就能白得一份成品。挂在"这一格真的被清空了"这件事上，才是所有路径的
     * 唯一收口。</p>
     */
    private void onPreviewTaken() {
        removeStack(SLOT_TOOL, 1);
        removeStack(SLOT_RELIC, 1);
        removeStack(SLOT_MATERIAL, 1);
        setPreview(ItemStack.EMPTY);
        knock();
    }

    @Override
    public int size() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        return this.items.get(slot);
    }

    /**
     * 取走一格里的东西。
     *
     * <p><b>这是"玩家把预览拿走"的唯一收口</b>：预览格被取空时，顺带扣掉三样输入
     * （见 {@link #onPreviewTaken()}）。挂在 {@code removeStack} 上而不是界面回调上，
     * 是因为原版"按 Q 丢弃"和"双击收集"这两条路不会调用界面回调。</p>
     */
    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack removed = Inventories.splitStack(this.items, slot, amount);
        if (!removed.isEmpty()) {
            this.markDirty();

            if (isPreviewSlot(slot) && !this.writingPreviewInternally && this.items.get(slot).isEmpty()) {
                onPreviewTaken();
            }
        }

        return removed;
    }

    /**
     * 取走整格。
     *
     * <p>必须转调上面那个方法，而不是直接用 {@code Inventories.removeStack} ——
     * 后者绕开了"预览被拿走"的处理，会漏掉扣材料。</p>
     */
    @Override
    public ItemStack removeStack(int slot) {
        ItemStack stack = this.items.get(slot);
        return stack.isEmpty() ? ItemStack.EMPTY : removeStack(slot, stack.getCount());
    }

    /**
     * 写入一格。
     *
     * <p>⚠️ 这里有一条特例：<b>往预览格写空堆 = 玩家把成品换走了</b>。
     * 原版"按 1~9 换到快捷栏"那条路就是用"往格子里写一个空堆"来表示"这格清空了"，它随后虽然会
     * 叫一次界面回调，但把扣材料挂在这里更稳 —— 少一条路要记。台子自己刷新预览走的是
     * {@link #setPreview}，不会被误判成玩家拿走。</p>
     */
    @Override
    public void setStack(int slot, ItemStack stack) {
        if (isPreviewSlot(slot) && stack.isEmpty() && !this.writingPreviewInternally
                && !this.items.get(slot).isEmpty()) {
            this.items.set(slot, ItemStack.EMPTY);
            this.markDirty();
            onPreviewTaken();
            return;
        }

        this.items.set(slot, stack);
        if (stack.getCount() > this.getMaxCountPerStack()) {
            stack.setCount(this.getMaxCountPerStack());
        }

        this.markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        if (this.world == null || this.world.getBlockEntity(this.pos) != this) {
            return false;
        }

        return player.squaredDistanceTo(this.pos.getX() + 0.5D, this.pos.getY() + 0.5D, this.pos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void clear() {
        this.items.clear();
        this.markDirty();
    }

    // ==================== 禁止一切自动化设备 ====================

    /**
     * 从任何一面看过去，这个台子都<b>没有可用的格子</b>。
     *
     * <p>原版漏斗是按这张表挑格子的（{@code HopperBlockEntity.getAvailableSlots}：
     * 实现 {@link SidedInventory} 就用 {@code getAvailableSlots}，否则退回"全部格子"）。
     * 返回空表 = 漏斗抽不走、也塞不进 —— 尤其是抽不走那份预览。</p>
     *
     * @param side 哪一面
     * @return 空数组，表示一面都不可用
     */
    @Override
    public int[] getAvailableSlots(Direction side) {
        return new int[0];
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, Direction dir) {
        return false;
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return false;
    }

    // ==================== 存档：预览不写进去 ====================

    /**
     * 只把前三格写进存档。
     *
     * <p>写成原版那份格式（一个 {@code Items} 列表、每项带 {@code Slot} 标签），
     * 这样读回来仍然可以用原版的 {@link Inventories#readNbt}，不必自己维护一套格式。</p>
     */
    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);

        NbtList list = new NbtList();
        for (int slot = 0; slot < PERSISTED_SLOTS; slot++) {
            ItemStack stack = this.items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }

            NbtCompound entry = new NbtCompound();
            entry.putByte("Slot", (byte) slot);
            stack.writeNbt(entry);
            list.add(entry);
        }

        nbt.put("Items", list);
    }

    /**
     * 读档：先清掉预览格，再按原版格式读回前三格。
     *
     * <p>之所以先清，是为了让"预览不存档"这条规矩对**旧存档**也成立 ——
     * 早先版本可能把一份预览写进了存档，这里一并抹掉，不让它复活成白送的物品。</p>
     */
    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        this.items.set(SLOT_PREVIEW, ItemStack.EMPTY);
        Inventories.readNbt(nbt, this.items);
        this.items.set(SLOT_PREVIEW, ItemStack.EMPTY);
    }

    /**
     * 玩家右键台子时，为他把装卸界面打开。
     *
     * @param syncId          同步编号
     * @param playerInventory 玩家背包
     * @param player          打开界面的玩家
     * @return 装卸界面的容器
     */
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new RelicStationScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("container.eternal_relic.relic_station");
    }
}
