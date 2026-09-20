package org.eternalrelic.registry;

import net.minecraft.item.Items;

import org.eternalrelic.relic.AttachTarget;
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

        // 勇气纹章：蜡制的，所以配蜜脾；只钉防具。四件装备都附上，也只生效一次
        RelicAttachment.register(
                ModItems.COURAGE_EMBLEM,
                Items.HONEYCOMB,
                RelicAttachment.Stacking.NONE,
                AttachTarget.ARMOR);

        // 斑驳的铜甲片：铜的，配铜锭；只钉防具。四个部位各附一枚时各算一份
        RelicAttachment.register(
                ModItems.MOTTLED_COPPER_PLATE,
                Items.COPPER_INGOT,
                RelicAttachment.Stacking.ACROSS_ITEMS,
                AttachTarget.ARMOR);

        // 永恒纹章：蜡制的，配蜜脾；防具、武器、工具都能钉——它保的是「这件东西本身不被毁掉」，
        // 因此不该限定用在哪一类上。它没有携带属性加成，累加方式用不上，登记 NONE 即可
        RelicAttachment.register(
                ModItems.ETERNAL_EMBLEM,
                Items.HONEYCOMB,
                RelicAttachment.Stacking.NONE,
                AttachTarget.ARMOR,
                AttachTarget.WEAPON,
                AttachTarget.TOOL);

        // 川流纹章：蜡制的，配蜜脾；只钉头盔与靴子——它给的两条附魔分别只对这两个部位有意义，
        // 钉在别处既拿不到效果、面板上也不该多出用不上的附魔行
        RelicAttachment.register(
                ModItems.STREAM_EMBLEM,
                Items.HONEYCOMB,
                RelicAttachment.Stacking.NONE,
                AttachTarget.HELMET,
                AttachTarget.BOOTS);

        // 坚铁甲片：铁的，配铁锭；只缝四个部位的防具，盾牌不算（它本身不提供护甲）。
        // 四个部位各缝一枚时各算一份，因此用 ACROSS_ITEMS 而不是 NONE
        RelicAttachment.register(
                ModItems.HARDENED_IRON_PLATE,
                Items.IRON_INGOT,
                RelicAttachment.Stacking.ACROSS_ITEMS,
                AttachTarget.ARMOR_PIECE);

        // 皮革内衬：皮做的，配皮革；与坚铁甲片一样只缝四个部位的防具，各缝一枚各算一份
        RelicAttachment.register(
                ModItems.LEATHER_LINING,
                Items.LEATHER,
                RelicAttachment.Stacking.ACROSS_ITEMS,
                AttachTarget.ARMOR_PIECE);
    }
}
