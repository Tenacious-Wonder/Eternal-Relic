package org.eternalrelic.relic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * 「遗物附着」这件事的规矩与账本 —— 哪些遗物能附、需要什么辅料、几件装备上的份量怎么算，
 * 以及「这件装备上现在附了哪些遗物」。
 *
 * <p>登记的内容放在 {@code registry/AttachableRelics}；本类只负责按登记办事，
 * 以及把「附了什么」写进目标物品自己的数据里。</p>
 *
 * <p><b>附着关系记在目标物品身上</b>，和魂火记在灯上、守护的碎裂状态记在环上是同一个思路：
 * 跟着物品走。放进箱子、跨国度、死亡重生、给别的玩家，附着关系都不会丢。</p>
 */
public final class RelicAttachment {

    /** 目标物品上记录「附了哪些遗物」的标签名，存一串物品编号。 */
    private static final String ATTACHMENTS_KEY = "EternalRelicAttachments";

    /**
     * 一件装备最多能附几枚遗物。
     *
     * <p>这个数字来自遗物装卸台界面上画着的那 6 个格子——界面上放得下几枚，实际就只允许几枚。
     * 两条附着途径（锻造台与装卸台）共用这一个上限，因此不会出现「锻造台能钉第 7 枚、
     * 装卸台却显示不出来」这种对不上的情况。</p>
     */
    public static final int MAX_ATTACHMENTS = 6;

    /**
     * 附着上来的那一份，与其它来源之间怎么累加。
     *
     * <p>两个轴各自独立，所以列成四种组合而不是两个布尔参数——</p>
     *
     * <ul>
     *   <li><b>多件装备各附一枚时</b>：是每件各算一份，还是加起来只算一份；</li>
     *   <li><b>背包里另有一枚时</b>：附着份要不要叠在它之上。</li>
     * </ul>
     *
     * <p>两个布尔挨着写在登记处谁也看不懂哪个是哪个，所以给每种组合起个名字。</p>
     */
    public enum Stacking {

        /** 附着份只算一份，也不与背包里的那一份叠加。 */
        NONE(false, false),

        /** 附着份只算一份，但叠在背包里那一份之上。 */
        WITH_CARRIED(true, false),

        /** 多件装备各附一枚时按件数累加；不与背包里的那一份叠加。 */
        ACROSS_ITEMS(false, true),

        /** 两样都算。 */
        BOTH(true, true);

        private final boolean withCarried;
        private final boolean acrossItems;

        private Stacking(boolean withCarried, boolean acrossItems) {
            this.withCarried = withCarried;
            this.acrossItems = acrossItems;
        }

        /**
         * @return 附着份是否叠在背包里那一份之上
         */
        public boolean withCarried() {
            return this.withCarried;
        }

        /**
         * @return 多件装备各附一枚时是否按件数累加
         */
        public boolean acrossItems() {
            return this.acrossItems;
        }
    }

    /**
     * 一件可附遗物的登记资料。
     *
     * @param targets  能附到哪几类目标上；不能为空
     * @param material 附着时第一格要放的辅料
     * @param stacking 附着份怎么与其它来源累加
     * @param category 作为「装备配件」时属于哪一类；纹章之类不是配件，这里为 {@code null}
     */
    public record Spec(Set<AttachTarget> targets, Item material, Stacking stacking, FittingCategory category) {

        /**
         * @param target 目标类别
         * @return 这件遗物是否允许附到该类目标上
         */
        public boolean allows(AttachTarget target) {
            return this.targets.contains(target);
        }
    }

    /** 全部已登记的可附遗物，按登记顺序。 */
    private static final Map<Item, Spec> SPECS = new LinkedHashMap<>();

    private RelicAttachment() {
    }

    /**
     * 登记一件可以附着的遗物。
     *
     * <p>一件遗物可以同时登记进多张名单——把想允许的类别都写进 {@code targets} 即可，
     * 不必分几处各写一遍，也就不会出现几份名单互相不一致。</p>
     *
     * <p>登记填漏时**直接抛错**，让它在启动时就暴露：这类登记只有开发者会写，
     * 静默跳过只会变成「进了游戏才发现某件遗物附不上去」。全部填对时它什么也不做，
     * 不往日志里留常驻输出。</p>
     *
     * @param relic    遗物本身
     * @param material 附着时第一格要放的辅料
     * @param stacking 附着份怎么与其它来源累加
     * @param targets  允许附着的目标类别，至少写一个
     */
    public static void register(Item relic, Item material, Stacking stacking, AttachTarget... targets) {
        register(relic, null, material, stacking, targets);
    }

    /**
     * 登记一件「装备配件」—— 与普通可附遗物的区别只在于<b>它属于某一类</b>，
     * 而同一类配件在一件装备上只能有一件（见 {@link #categoryAllows}）。
     *
     * <p>分档变体（皮革 / 鳞片 / 龟壳）要登记成同一类，它们之间才会互斥；
     * 左右肩甲要分成两类，玩家才能一边挂一只。</p>
     *
     * <p>登记填漏时**直接抛错**，让它在启动时就暴露：这类登记只有开发者会写，
     * 静默跳过只会变成"进了游戏才发现某件配件能重复装"。</p>
     *
     * @param fitting  配件本身
     * @param category 它属于哪一类；不能为 {@code null}
     * @param material 附着时第一格要放的辅料
     * @param stacking 附着份怎么与其它来源累加
     * @param targets  允许附着的目标类别，至少写一个
     */
    public static void registerFitting(Item fitting, FittingCategory category, Item material, Stacking stacking,
            AttachTarget... targets) {
        if (category == null) {
            throw new IllegalArgumentException(
                    "装备配件「" + Registries.ITEM.getId(fitting) + "」没有写明属于哪一类");
        }

        register(fitting, category, material, stacking, targets);
    }

    private static void register(Item relic, FittingCategory category, Item material, Stacking stacking,
            AttachTarget... targets) {
        String name = Registries.ITEM.getId(relic).toString();

        if (targets.length == 0) {
            throw new IllegalArgumentException("可附遗物「" + name + "」没有写明能附到哪类目标上");
        }

        if (material == null || material == Items.AIR) {
            throw new IllegalArgumentException("可附遗物「" + name + "」没有写明辅料");
        }

        EnumSet<AttachTarget> set = EnumSet.noneOf(AttachTarget.class);
        Collections.addAll(set, targets);
        SPECS.put(relic, new Spec(Collections.unmodifiableSet(set), material, stacking, category));
    }

    /**
     * @param item 待查询的物品
     * @return 这件物品的附着登记；不可附时返回 {@code null}
     */
    public static Spec specOf(Item item) {
        return SPECS.get(item);
    }

    /**
     * 这件物品作为「装备配件」属于哪一类。
     *
     * <p>不是配件的（纹章之类）与压根不可附的都返回 {@code null}——调用方据此判断
     * "要不要受同类只能一件的限制"。</p>
     *
     * @param item 待查询的物品
     * @return 它的配件类别；不属于任何一类时返回 {@code null}
     */
    public static FittingCategory categoryOf(Item item) {
        Spec spec = SPECS.get(item);
        return spec == null ? null : spec.category();
    }

    /**
     * 这件装备上还装得下这一类配件吗。
     *
     * <p><b>这是"同类只能一件"的判定处</b>：一件胸甲上不能同时挂着两套肩甲，
     * 也不能同时挂着两只左肩甲；但左肩甲与右肩甲是两类，可以各挂一只。</p>
     *
     * <p>不是配件的东西（纹章）一律放行——它们本来就可以叠着钉。</p>
     *
     * @param target 待检查的装备
     * @param relic  想装上去的那一件
     * @return 允许装上去时返回 {@code true}
     */
    public static boolean categoryAllows(ItemStack target, Item relic) {
        FittingCategory category = categoryOf(relic);

        if (category == null) {
            return true;
        }

        for (Item attached : attachedTo(target)) {
            if (categoryOf(attached) == category) {
                return false;
            }
        }

        return true;
    }

    /**
     * @return 全部已登记的可附遗物，按登记顺序
     */
    public static Map<Item, Spec> all() {
        return Collections.unmodifiableMap(SPECS);
    }

    /**
     * 判断一件目标物品能否接受某件遗物。
     *
     * <p>三件事都要成立：这件遗物登记过、它允许附到目标这一类上、而且**这枚遗物还没附在这件装备上**
     * ——同一件装备上重复附同一枚没有意义，所以直接不接受。</p>
     *
     * @param target 要被附着的装备 / 武器 / 工具
     * @param relic  那件遗物
     * @return 是否可以附上去
     */
    public static boolean accepts(ItemStack target, Item relic) {
        Spec spec = SPECS.get(relic);
        if (spec == null || target.isEmpty() || isAttached(target, relic)) {
            return false;
        }

        if (attachedTo(target).size() >= MAX_ATTACHMENTS) {
            return false;
        }

        // 同一类配件一件装备上只能有一件（肩甲·左 与 肩甲·右 是两类，互不影响）
        if (!categoryAllows(target, relic)) {
            return false;
        }

        for (AttachTarget candidate : spec.targets()) {
            if (candidate.covers(target.getItem())) {
                return true;
            }
        }

        return false;
    }

    /**
     * 收出玩家此刻**通过附着而生效**的遗物及份数。
     *
     * <p>只看「正穿着」与「正拿着」的格子：四个防具格，加上主手与副手。
     * 放在背包里不算——附着的意义就在于「穿上它、拿起它才管用」。</p>
     *
     * <p>返回的是**份数**而不是集合，是因为有遗物要求「四件装备各附一枚就各算一份」；
     * 只算一次的遗物由调用方忽略这个数字即可。</p>
     *
     * @param player 目标玩家
     * @return 遗物 → 附着的份数；一件都没有时为空表
     */
    public static Map<Item, Integer> activeRelicCounts(PlayerEntity player) {
        Map<Item, Integer> counts = new HashMap<>();

        for (ItemStack stack : activeStacks(player)) {
            if (stack.isEmpty()) {
                continue;
            }

            for (Item relic : attachedTo(stack)) {
                counts.merge(relic, 1, Integer::sum);
            }
        }

        return counts;
    }

    /**
     * @param player 目标玩家
     * @return 此刻通过附着生效的遗物（不关心份数）；一件都没有则为空集
     */
    public static Set<Item> activeRelics(PlayerEntity player) {
        return activeRelicCounts(player).keySet();
    }

    /**
     * 这件遗物此刻是否通过「附着在装备上」而生效。
     *
     * @param player 目标玩家
     * @param relic  那件遗物
     * @return 是否有某件正穿着或正拿着的装备上附了它
     */
    public static boolean activeOn(PlayerEntity player, Item relic) {
        return activeRelicCounts(player).containsKey(relic);
    }

    /**
     * 玩家身上「正在生效」的物品格。
     *
     * <p>四个防具格照单全收；**手上只认不是防具的东西**——防具必须穿在身上才算数，
     * 拿在手里不算穿戴。盾牌并不继承防具类，因此握在副手照旧算数。</p>
     *
     * @param player 目标玩家
     * @return 正在生效的物品堆
     */
    private static List<ItemStack> activeStacks(PlayerEntity player) {
        List<ItemStack> stacks = new ArrayList<>(player.getInventory().armor);

        for (ItemStack held : List.of(player.getMainHandStack(), player.getOffHandStack())) {
            if (!held.isEmpty() && !(held.getItem() instanceof ArmorItem)) {
                stacks.add(held);
            }
        }

        return stacks;
    }

    /**
     * 读出这件物品上附着的全部遗物。
     *
     * <p>数据里存的是物品编号，读的时候会核对一遍「这个编号现在还算不算可附遗物」，
     * 因此登记被撤掉之后，旧物品上的残留数据不会变成幽灵效果。</p>
     *
     * @param stack 待查看的物品
     * @return 上面附着的遗物；没有则为空表
     */
    public static List<Item> attachedTo(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(ATTACHMENTS_KEY, NbtElement.LIST_TYPE)) {
            return List.of();
        }

        NbtList list = nbt.getList(ATTACHMENTS_KEY, NbtElement.STRING_TYPE);
        List<Item> attached = new ArrayList<>(list.size());

        for (int i = 0; i < list.size(); i++) {
            Identifier id = Identifier.tryParse(list.getString(i));
            if (id == null) {
                continue;
            }

            Item item = Registries.ITEM.get(id);
            if (item != null && SPECS.containsKey(item)) {
                attached.add(item);
            }
        }

        return attached;
    }

    /**
     * @param stack 待查看的物品
     * @param relic 那件遗物
     * @return 这枚遗物是否已经附在这件物品上
     */
    public static boolean isAttached(ItemStack stack, Item relic) {
        return attachedTo(stack).contains(relic);
    }

    /**
     * 把一枚遗物附到目标物品上，就地改动这份物品堆的数据。
     *
     * @param stack 要被附着的装备 / 武器 / 工具
     * @param relic 要附上去的遗物
     */
    public static void attach(ItemStack stack, Item relic) {
        NbtCompound nbt = stack.getOrCreateNbt();

        NbtList list = nbt.contains(ATTACHMENTS_KEY, NbtElement.LIST_TYPE)
                ? nbt.getList(ATTACHMENTS_KEY, NbtElement.STRING_TYPE)
                : new NbtList();

        list.add(NbtString.of(Registries.ITEM.getId(relic).toString()));
        nbt.put(ATTACHMENTS_KEY, list);
    }

    /**
     * 把一枚遗物从目标物品上拆下来，就地改动这份物品堆的数据。
     *
     * <p>只摘掉这一枚，其余的原样留着；没附过这枚时什么也不做。</p>
     *
     * @param stack 要动手术的装备 / 武器 / 工具
     * @param relic 要拆下来的遗物
     * @return 是否真的拆下来了一枚
     */
    public static boolean detach(ItemStack stack, Item relic) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(ATTACHMENTS_KEY, NbtElement.LIST_TYPE)) {
            return false;
        }

        NbtList list = nbt.getList(ATTACHMENTS_KEY, NbtElement.STRING_TYPE);
        String wanted = Registries.ITEM.getId(relic).toString();

        for (int i = 0; i < list.size(); i++) {
            if (wanted.equals(list.getString(i))) {
                list.remove(i);
                return true;
            }
        }

        return false;
    }
}
