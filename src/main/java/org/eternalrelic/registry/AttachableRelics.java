package org.eternalrelic.registry;

import net.minecraft.item.Items;

import org.eternalrelic.relic.AttachTarget;
import org.eternalrelic.relic.FittingCategory;
import org.eternalrelic.relic.RelicAttachment;

/**
 * <b>可附遗物表</b> —— 哪些遗物能附着到装备 / 武器 / 工具上，各需要什么辅料。
 *
 * <p>这是一份**由制作者选定的白名单**：一件遗物<b>没有</b>登记在这里，就是不能附，
 * 这是正常状态，不是漏写。想让某件遗物能附，在这里加一条即可。</p>
 *
 * <p>一条登记同时写明四件事，缺一样都会在启动时**立刻报错**（而不是进了游戏才发现）：</p>
 *
 * <ol>
 *   <li><b>哪件遗物</b> —— {@link ModItems} 里的物品；</li>
 *   <li><b>能附到哪几类目标上</b> —— 装备 / 武器 / 工具可以同时写多个；</li>
 *   <li><b>需要什么辅料</b> —— 锻造台第一格要放的东西，例如蜡制的纹章要蜜脾；</li>
 *   <li><b>背包里另有一份时算不算叠加</b> —— 见 {@link RelicAttachment.Spec}。</li>
 * </ol>
 *
 * <h2>两套登记方法，区别只在"它算不算装备配件"</h2>
 *
 * <ul>
 *   <li><b>{@link RelicAttachment#registerFitting 登记为装备配件}</b>：要额外写明<b>属于哪一类</b>
 *       （见 {@link FittingCategory}）。规矩是<b>同一类配件，一件装备上只能有一件</b>——
 *       一件胸甲上不能同时挂着两套肩甲，也不能同时挂着两只左肩甲。</li>
 *   <li><b>{@code register(...)} 登记为普通遗物</b>：纹章一类走这条。它们不属于任何类别，
 *       因此可以叠着钉——这本来就是纹章该有的样子。</li>
 * </ul>
 *
 * <p><b>分档变体要填同一类</b>：皮革 / 鳞片 / 龟壳三种肩甲是同一样东西的三个档次，
 * 所以它们共用 {@link FittingCategory#SHOULDER_LEFT} 之类的同一个类别，彼此互斥；
 * 而<b>左与右分成两类</b>，玩家才能一边挂一只。</p>
 *
 * <p><b>为什么辅料是「逐件指定」而不是统一一种</b>：辅料会被消耗，本身就是一道成本闸门，
 * 而不同遗物该配什么材料由它的来历决定——蜡制的纹章配蜜脾，玩家一看就懂为什么。
 * 想按类统一也很容易：把几枚纹章都填成同一种辅料即可。</p>
 */
public final class AttachableRelics {

    private AttachableRelics() {
    }

    /**
     * 由 {@link ModItems#register()} 调用，触发本类静态内容初始化。
     *
     * <p>必须排在遗物表登记之后——这里引用的都是 {@link ModItems} 里的物品。</p>
     */
    static void register() {
        // ==================== 制作者选定的白名单 ====================

        // 勇气纹章：蜡制的，所以配蜜脾；只钉防具。
        // 纹章不是"装备配件"，不填类别——它本来就可以和别的纹章一起钉
        RelicAttachment.register(
                ModItems.COURAGE_EMBLEM,
                Items.HONEYCOMB,
                RelicAttachment.Stacking.NONE,
                AttachTarget.ARMOR);

        // 斑驳的铜甲片：铜的，配铜锭；只钉防具。它是"甲片"这一类：
        // 一件防具上只能有一片，因此与坚铁甲片互斥（两种甲片本来就是同一样东西的两个档次）
        RelicAttachment.registerFitting(
                ModItems.MOTTLED_COPPER_PLATE,
                FittingCategory.PLATE,
                Items.COPPER_INGOT,
                RelicAttachment.Stacking.ACROSS_ITEMS,
                AttachTarget.ARMOR);

        // 永恒纹章：蜡制的，配蜜脾；防具、武器、工具都能钉——它保的是「这件东西本身不被毁掉」，
        // 因此不该限定用在哪一类上。纹章不填类别，可以叠着钉
        RelicAttachment.register(
                ModItems.ETERNAL_EMBLEM,
                Items.HONEYCOMB,
                RelicAttachment.Stacking.NONE,
                AttachTarget.ARMOR,
                AttachTarget.WEAPON,
                AttachTarget.TOOL);

        // 川流纹章：蜡制的，配蜜脾；只钉头盔与靴子——它给的两条附魔分别只对这两个部位有意义
        RelicAttachment.register(
                ModItems.STREAM_EMBLEM,
                Items.HONEYCOMB,
                RelicAttachment.Stacking.NONE,
                AttachTarget.HELMET,
                AttachTarget.BOOTS);

        // 坚铁甲片：铁的，配铁锭；只缝四个部位的防具，盾牌不算。与铜甲片同属"甲片"这一类
        RelicAttachment.registerFitting(
                ModItems.HARDENED_IRON_PLATE,
                FittingCategory.PLATE,
                Items.IRON_INGOT,
                RelicAttachment.Stacking.ACROSS_ITEMS,
                AttachTarget.ARMOR_PIECE);

        // 皮革内衬：配铁粒 —— 内衬（半成品）的合成里本就要用两粒铁粒缝住，缝到防具上沿用同一种材料。
        // 它是"内衬"这一类：一件防具上只能有一层，三种内衬彼此互斥
        RelicAttachment.registerFitting(
                ModItems.LEATHER_LINING,
                FittingCategory.LINING,
                Items.IRON_NUGGET,
                RelicAttachment.Stacking.ACROSS_ITEMS,
                AttachTarget.ARMOR_PIECE);

        // 鳞甲内衬：配线 —— 内衬是缝上去的，与它配方里那两缕线是同一种材料。同属"内衬"
        RelicAttachment.registerFitting(
                ModItems.SCUTE_LINING,
                FittingCategory.LINING,
                Items.STRING,
                RelicAttachment.Stacking.ACROSS_ITEMS,
                AttachTarget.ARMOR_PIECE);

        // 龟壳内衬：同样配线，同属"内衬"
        RelicAttachment.registerFitting(
                ModItems.TURTLE_SHELL_LINING,
                FittingCategory.LINING,
                Items.STRING,
                RelicAttachment.Stacking.ACROSS_ITEMS,
                AttachTarget.ARMOR_PIECE);

        // 皮革肩甲（左 / 右 / 一套）：肩甲是缝在胸甲上的皮革件，配线——与鳞甲内衬、龟壳内衬同一种材料。
        // 三件都**只缝胸甲**：它们护的是肩膀，缝在头盔、护腿或靴子上没有意义。
        // 累加方式用 NONE：这三件都是「只认附着份」的遗物，背包里那一份本来就不算数；
        // 而它们又只能缝在胸甲这一处，一件胸甲上同一件最多出现一枚，件数永远是一，没有可累加的地方。
        // 类别上：左、右、整套各自成类——左肩甲与右肩甲可以各挂一只，但同一侧不能挂两件
        RelicAttachment.registerFitting(
                ModItems.LEATHER_SHOULDER_GUARD_LEFT,
                FittingCategory.SHOULDER_LEFT,
                Items.STRING,
                RelicAttachment.Stacking.NONE,
                AttachTarget.CHESTPLATE);

        RelicAttachment.registerFitting(
                ModItems.LEATHER_SHOULDER_GUARD_RIGHT,
                FittingCategory.SHOULDER_RIGHT,
                Items.STRING,
                RelicAttachment.Stacking.NONE,
                AttachTarget.CHESTPLATE);

        RelicAttachment.registerFitting(
                ModItems.LEATHER_SHOULDER_GUARD_PAIR,
                FittingCategory.SHOULDER_PAIR,
                Items.STRING,
                RelicAttachment.Stacking.NONE,
                AttachTarget.CHESTPLATE);

        // 鳞片肩甲（左 / 右 / 一套）：与皮革那三件同一路登记——同样配线、同样只缝胸甲，
        // 类别也分别与皮革那三件相同（同侧的皮革与鳞片互斥，正是"同类只能一件"的意思）
        RelicAttachment.registerFitting(
                ModItems.SCUTE_SHOULDER_GUARD_LEFT,
                FittingCategory.SHOULDER_LEFT,
                Items.STRING,
                RelicAttachment.Stacking.NONE,
                AttachTarget.CHESTPLATE);

        RelicAttachment.registerFitting(
                ModItems.SCUTE_SHOULDER_GUARD_RIGHT,
                FittingCategory.SHOULDER_RIGHT,
                Items.STRING,
                RelicAttachment.Stacking.NONE,
                AttachTarget.CHESTPLATE);

        RelicAttachment.registerFitting(
                ModItems.SCUTE_SHOULDER_GUARD_PAIR,
                FittingCategory.SHOULDER_PAIR,
                Items.STRING,
                RelicAttachment.Stacking.NONE,
                AttachTarget.CHESTPLATE);

        // 龟壳肩甲（左 / 右 / 一套）：与前两档同一路登记——配线、只缝胸甲、只认附着份。
        // 「会弹开远程」那一条不在这里，它登记在肩甲表（registry/ShoulderGuards）
        RelicAttachment.registerFitting(
                ModItems.TURTLE_SHELL_SHOULDER_GUARD_LEFT,
                FittingCategory.SHOULDER_LEFT,
                Items.STRING,
                RelicAttachment.Stacking.NONE,
                AttachTarget.CHESTPLATE);

        RelicAttachment.registerFitting(
                ModItems.TURTLE_SHELL_SHOULDER_GUARD_RIGHT,
                FittingCategory.SHOULDER_RIGHT,
                Items.STRING,
                RelicAttachment.Stacking.NONE,
                AttachTarget.CHESTPLATE);

        RelicAttachment.registerFitting(
                ModItems.TURTLE_SHELL_SHOULDER_GUARD_PAIR,
                FittingCategory.SHOULDER_PAIR,
                Items.STRING,
                RelicAttachment.Stacking.NONE,
                AttachTarget.CHESTPLATE);

        // 太阳纹章：蜡制的，配蜜脾；能缝在四个部位的防具与盾牌上（用户指定「装备和盾牌」）。
        // 纹章不填类别，可以叠着钉
        RelicAttachment.register(
                ModItems.SUN_EMBLEM,
                Items.HONEYCOMB,
                RelicAttachment.Stacking.NONE,
                AttachTarget.ARMOR);

        // 月亮纹章：与太阳纹章同一路登记，只是管夜晚、给的是速度
        RelicAttachment.register(
                ModItems.MOON_EMBLEM,
                Items.HONEYCOMB,
                RelicAttachment.Stacking.NONE,
                AttachTarget.ARMOR);
    }
}
