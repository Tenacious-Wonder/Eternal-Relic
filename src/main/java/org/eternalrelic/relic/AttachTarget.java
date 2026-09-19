package org.eternalrelic.relic;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.ShearsItem;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;

/**
 * 遗物可以附着到哪一类目标上。
 *
 * <p>这是「三张名单」的类目：哪件遗物能附到装备上、哪件能附到武器上、哪件能附到工具上，
 * 分别登记在 {@code AttachableRelics} 里。这里只回答「这件物品算哪一类」。</p>
 *
 * <p><b>判定用原版的类型，不写死一份物品名单</b>：这样原版添了新东西、或别的模组加了
 * 自己的装备，都会自动被认出来，不必回来补名单。三处按用户要求做了特例：
 * <b>斧</b>既算工具又算武器，<b>盾牌</b>算装备，<b>钓鱼竿 / 剪刀 / 打火石</b>算工具。</p>
 */
public enum AttachTarget {

    /** 穿在身上的防具，以及盾牌。 */
    ARMOR("装备"),

    /** 用来打人的东西。 */
    WEAPON("武器"),

    /** 用来干活的东西。 */
    TOOL("工具");

    private final String displayName;

    private AttachTarget(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return 这类目标的中文名，供提示与日志使用
     */
    public String displayName() {
        return this.displayName;
    }

    /**
     * 判断一件物品是否属于这类目标。
     *
     * <p>注意<b>一件物品可以同时属于两类</b>（斧既是工具也是武器），因此判断结果不能当作
     * 「它唯一属于哪一类」来用——需要类别时请逐类去问。</p>
     *
     * @param item 待判断的物品
     * @return 这件物品是否算这类目标
     */
    public boolean covers(Item item) {
        return switch (this) {
            case ARMOR -> item instanceof ArmorItem || item instanceof ShieldItem;
            case WEAPON -> item instanceof SwordItem
                    || item instanceof AxeItem
                    || item instanceof TridentItem
                    || item instanceof BowItem
                    || item instanceof CrossbowItem;
            case TOOL -> item instanceof MiningToolItem
                    || item instanceof HoeItem
                    || item instanceof FishingRodItem
                    || item instanceof ShearsItem
                    || item instanceof FlintAndSteelItem;
        };
    }

    /**
     * 判断一件物品是否**算得上任意一类**目标（装备 / 武器 / 工具之一）。
     *
     * <p>没有这个方法时，调用处都会自己写一遍"三类逐类问一遍"的循环——本项目已经出现过
     * 两处同样的循环，正是"改一处漏一处"的温床。统一走这里。</p>
     *
     * @param item 待判断的物品
     * @return 三类里至少中一类时返回 {@code true}
     */
    public static boolean coversAny(Item item) {
        for (AttachTarget target : values()) {
            if (target.covers(item)) {
                return true;
            }
        }

        return false;
    }
}
