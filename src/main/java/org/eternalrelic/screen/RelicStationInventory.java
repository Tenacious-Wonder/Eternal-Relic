package org.eternalrelic.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

/**
 * 遗物装卸台界面里的四格内容 —— 台子的「数据面」，随界面而生、随界面而灭。
 *
 * <p><b>它为什么不是方块实体</b>：装卸台像原版工作台那样，方块只当开关用 —— 右键开出界面，
 * 东西都放在这个对象里，关掉界面就还给玩家。这样一来，<b>两个玩家同时开同一个台子时各用各的</b>；
 * 从前内容存在方块实体上，两个人会看见同一份格子、互相打乱。代价是内容不落盘。</p>
 *
 * <p><b>四格的分工</b>：</p>
 *
 * <ul>
 *   <li>{@link #SLOT_TOOL} —— <b>工件</b>：要被附上遗物的工具 / 装备。界面上位于<b>最左侧</b>，
 *       是<b>"装"这一路的唯一入口</b>；</li>
 *   <li>{@link #SLOT_MATERIAL} —— 附着所需的辅料（附纹章时是蜜脾）；只在附着时用到；</li>
 *   <li>{@link #SLOT_RELIC} —— 要附上去的遗物；只在附着时用到；</li>
 *   <li>{@link #SLOT_RESULT} —— <b>成品栏</b>：正中被六格围着的那一格，它有两副面孔。</li>
 * </ul>
 *
 * <p><b>成品栏的两副面孔，由"工件格空不空"区分</b>：</p>
 *
 * <ul>
 *   <li><b>工件格里有东西（"装"）</b>：这一格是台子摆出来的<b>成品预览</b>，玩家塞不进东西；
 *       拿走它 = 拿走成品，<b>扣掉工件 / 遗物 / 辅料各一样</b>；</li>
 *   <li><b>工件格空着（"拆"）</b>：这一格归玩家，放一件<b>身上已附遗物的成品</b>进去，
 *       右边六格就列出它身上的遗物，从六格拿一枚即拆下；拿走这一格里的东西只是把自己那件收回去，
 *       <b>什么也不欠</b>。</li>
 * </ul>
 *
 * <p><b>于是"成品栏里现在放的是谁的"完全由工件格决定，不必另记一个标志</b>：
 * 装模式下界面那边拒绝玩家往成品栏放东西（{@code canInsert} 要求工件格为空），
 * 所以那格里只可能是台子摆的；反过来，工件格一空，本类就在同一刻把台子摆的预览收回
 * （见 {@link #removeStack(int, int)}），于是格里只可能是玩家的。</p>
 *
 * <p><b>收口只有一处，但必须先看清是哪一副面孔</b>：原版取走东西有好几条路
 * （鼠标直接拿、<b>按 Q 丢弃</b>、<b>双击收集</b>、Shift+点击、按 1~9），其中有的
 * <b>根本不叫界面回调</b>，所以"这一格真的被清空了"是所有路径的唯一收口
 * （见 {@link #onCraftedTaken()}）。</p>
 *
 * <p>从前还有一条"预览不进存档、不进掉落物、不给漏斗碰"，那是冲着方块实体去的；
 * 现在内容只活在这一次界面会话里，那条<b>自然成立</b>，不必再单独维护。</p>
 *
 * <p>敲击声不在这里放：本类<b>不认识世界与坐标</b>，需要敲的时候只按一下构造时收到的通知。</p>
 *
 * <p><b>两份内容，只有一份说了算</b>：服务端手上那份是权威的（构造时 {@code authoritative} 为
 * {@code true}）——扣材料、敲一下都只在它身上发生；客户端手上那份只是<b>照服务端下发的数据摆样子</b>，
 * 收到"成品栏被清空"时<b>绝不许跟着扣</b>：撤下预览（工件被拿走、界面关了）服务端并不会扣，
 * 客户端若自作主张扣一次，界面上就凭空少掉工件 / 遗物 / 辅料，而服务端没有任何理由把它发回来。</p>
 */
public class RelicStationInventory implements Inventory {

    /** 工件：被附着的工具 / 装备。界面上位于最左侧，也是"装"这一路的入口。 */
    public static final int SLOT_TOOL = 0;

    /** 附着所需的辅料。 */
    public static final int SLOT_MATERIAL = 1;

    /** 要附上去的遗物。 */
    public static final int SLOT_RELIC = 2;

    /**
     * 成品栏：正中被六格围着的那一格。
     *
     * <p>工件格里有东西时它是台子摆的成品预览，工件格空着时它是玩家放待拆成品的地方。</p>
     */
    public static final int SLOT_RESULT = 3;

    /** 界面上属于"输入"的格子数（工件 / 辅料 / 遗物）—— 成品栏另算。 */
    public static final int INPUT_SLOTS = SLOT_RESULT;

    /** 一共几个格子。 */
    public static final int SIZE = 4;

    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);

    /** 该敲一下时按的通知：由界面在台子那一格的位置播声音。 */
    private final Runnable hammer;

    /**
     * 这一份内容是不是"说了算的那一份"。
     *
     * <p>服务端为 {@code true}；客户端为 {@code false}，它只按服务端下发的数据摆样子，
     * 不执行"拿走成品就扣三样输入"那套动作（理由见类文档）。</p>
     */
    private final boolean authoritative;

    /**
     * @param hammer        该敲一下时按的通知；由界面负责发声，本类只负责按
     * @param authoritative 这一份内容是否说了算（服务端 {@code true}，客户端 {@code false}）
     */
    public RelicStationInventory(Runnable hammer, boolean authoritative) {
        this.hammer = hammer;
        this.authoritative = authoritative;
    }

    /**
     * 这一格是不是成品栏。
     *
     * @param slot 格子编号
     * @return 是成品栏时返回 {@code true}
     */
    public static boolean isResultSlot(int slot) {
        return slot == SLOT_RESULT;
    }

    /**
     * 由界面容器调用：台子自己摆一份成品预览（传空就是撤下）。
     *
     * <p><b>⚠️ 这里直接写格子，绝不改调 {@link #setStack}。</b>{@code setStack} 把"往成品栏写空堆"
     * 解读为"玩家把成品换走了"（该扣三样输入），而台子自己刷新预览时也会清空它 —— 那不是拿走。
     * 直接写格子就把这两件事从根上分开了，不必再靠一个"正在内部写入"的开关去分辨：
     * 那个开关摆在那里也永远转不起来，因为读它的只有 {@code setStack}/{@code removeStack}，
     * 而这两个方法在 {@code setResult} 执行期间不会被调到。</p>
     *
     * <p>另外，<b>只有"装"的时候才该调它</b>：工件格空着时成品栏归玩家，写进去会把玩家的东西抹掉。</p>
     *
     * @param stack 要摆上去的成品；空表示撤下
     */
    public void setResult(ItemStack stack) {
        this.items.set(SLOT_RESULT, stack);
    }

    /**
     * 把台子自己摆的成品预览收回来（界面关闭时用）。
     *
     * <p>至于"工件格变空"引起的收回，<b>不走这里</b> —— 那是在格子被取空的那一刻就地处理的
     * （见 {@link #removeStack(int, int)} 与 {@link #setStack(int, ItemStack)}）。
     * 不挂在"每次点击之后重算预览"那条路上，是因为重算分不清格里那份是台子摆的还是玩家刚放进去的，
     * 会在"拆"模式下把玩家的成品抹掉。</p>
     */
    public void clearResult() {
        if (!this.items.get(SLOT_RESULT).isEmpty()) {
            setResult(ItemStack.EMPTY);
        }
    }

    /**
     * <b>台子摆的那份成品被拿走了 —— 扣掉工件、遗物、辅料各一样，并敲一下。</b>
     *
     * <p>⚠️ <b>先看工件格在不在，再决定扣不扣。</b>成品栏有两副面孔（见类文档）：工件格里有东西时
     * 它才是台子摆的成品预览；工件格空着时它是玩家放待拆成品的地方，把那件东西拿走只是收回去，
     * <b>什么也不欠</b>。少了这道判断，玩家把自己的旧装备放进去再拿出来就会被白扣三样材料。</p>
     *
     * <p>⚠️ <b>为什么必须挂在"这一格真的被清空了"上，而不是挂在界面的"拿走回调"上：</b>
     * 原版把东西从一格取走有好几条互不相同的路 —— 鼠标直接拿会叫 {@code onTakeItem}，
     * 而<b>按 Q 丢弃</b>与<b>双击收集</b>只调用 {@code takeStackRange}/{@code removeStack}，
     * <b>根本不叫那个回调</b>。挂在回调上就会漏掉后两条，于是"按 Q 把成品丢出来"就能白得一份。
     * 挂在"这一格真的被清空了"这件事上，才是所有路径的唯一收口。</p>
     */
    private void onCraftedTaken() {
        if (this.items.get(SLOT_TOOL).isEmpty()) {
            return;
        }

        removeStack(SLOT_TOOL, 1);
        removeStack(SLOT_RELIC, 1);
        removeStack(SLOT_MATERIAL, 1);
        setResult(ItemStack.EMPTY);

        this.hammer.run();
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
     * <p><b>这是"成品被拿走"的唯一收口</b>：在说得算的那一份上，成品栏被取空时会顺带
     * 结算一次（见 {@link #onCraftedTaken()}）；客户端那一份只是照做取走，不结算。</p>
     */
    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack removed = Inventories.splitStack(this.items, slot, amount);
        if (!this.authoritative || removed.isEmpty()) {
            return removed;
        }

        if (isResultSlot(slot) && this.items.get(slot).isEmpty()) {
            onCraftedTaken();
        } else if (slot == SLOT_TOOL && this.items.get(slot).isEmpty()) {
            // 工件格空了 = "装"这件事不再成立，台子摆的那份预览必须**当场**收回：
            // 工件格一空，六格就转去照成品栏，格里那份预览若还留着，玩家一点就能当自己的东西拿走。
            // 此刻它必然是台子摆的 —— 装模式下玩家塞不进成品栏（见界面那边的 canInsert）。
            setResult(ItemStack.EMPTY);
        }

        return removed;
    }

    /**
     * 取走整格。
     *
     * <p>必须转调上面那个方法，而不是直接清空 —— 否则会绕开"成品被拿走"的结算，漏掉扣材料。</p>
     */
    @Override
    public ItemStack removeStack(int slot) {
        ItemStack stack = this.items.get(slot);
        return stack.isEmpty() ? ItemStack.EMPTY : removeStack(slot, stack.getCount());
    }

    /**
     * 写入一格。
     *
     * <p>⚠️ 这里有一条特例：<b>往成品栏写空堆 = 玩家把成品换走了</b>。
     * 原版"按 1~9 换到快捷栏"那条路就是用"往格子里写一个空堆"来表示"这格清空了"，它随后虽然会
     * 叫一次界面回调，但把结算挂在这里更稳 —— 少一条路要记。台子自己刷新预览走的是
     * {@link #setResult}，不会被误判成玩家拿走；客户端那一份则根本不结算（见类文档）。</p>
     */
    @Override
    public void setStack(int slot, ItemStack stack) {
        if (this.authoritative && isResultSlot(slot) && stack.isEmpty()
                && !this.items.get(slot).isEmpty()) {
            this.items.set(slot, ItemStack.EMPTY);
            onCraftedTaken();
            return;
        }

        this.items.set(slot, stack);
        if (stack.getCount() > this.getMaxCountPerStack()) {
            stack.setCount(this.getMaxCountPerStack());
        }

        if (this.authoritative && slot == SLOT_TOOL && stack.isEmpty()) {
            // 同上：原版"往格子里写一个空堆"也表示清空，这一条路同样要把预览收回
            setResult(ItemStack.EMPTY);
        }
    }

    /**
     * 什么也不做。
     *
     * <p>界面每刻自己比对格子内容并把变化发给客户端，这些格子也没有要落盘的东西，
     * 因此"脏了"这个信号在本类里没有接收方。</p>
     */
    @Override
    public void markDirty() {
    }

    /**
     * @return 恒为 {@code true} —— 距离与"台子还在不在"由界面容器把关（见
     *         {@link RelicStationScreenHandler#canUse(net.minecraft.entity.player.PlayerEntity)}）
     */
    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return true;
    }

    @Override
    public void clear() {
        this.items.clear();
    }
}
