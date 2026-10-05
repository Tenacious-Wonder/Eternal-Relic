package org.eternalrelic.relic;

import net.minecraft.entity.EquipmentSlot;
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
 * 遗物可以附着到哪一类物品上，以及一条附魔「认哪一类物品」。
 *
 * <p>同一个类目服务两处用途，因为它们问的其实是同一件事——「这件东西算哪一类」：</p>
 *
 * <ul>
 *   <li><b>可附目标</b>：哪件遗物能附到哪几类东西上，登记在 {@code AttachableRelics} 里；</li>
 *   <li><b>补魔的适用对象</b>：一条附魔只在附着物属于这一类时才补上去
 *       （川流纹章：头盔给水下呼吸、靴子给深海探索者），登记在 {@code EnchantingRelics} 里。</li>
 * </ul>
 *
 * <p><b>大类比小类宽，一件物品可以同时属于好几类</b>：{@link #ARMOR} 覆盖全部防具与盾牌，
 * 因此一个头盔同时属于 {@code ARMOR} 与 {@link #HELMET}。判断结果不能当作
 * 「它唯一属于哪一类」来用——需要确定类别时请逐类去问。</p>
 *
 * <p><b>判定用原版的类型，不写死一份物品名单</b>：这样原版添了新东西、或别的模组加了
 * 自己的装备，都会自动被认出来，不必回来补名单。几处按用户要求做了特例：
 * <b>斧</b>既算工具又算武器，<b>盾牌</b>算装备，<b>钓鱼竿 / 剪刀 / 打火石</b>算工具。</p>
 */
public enum AttachTarget {

    /** 穿在身上的防具，以及盾牌——四个部位都算。 */
    ARMOR("装备"),

    /** 只算四个部位的防具，盾牌不算。 */
    ARMOR_PIECE("防具"),

    /** 只算戴在头上的那一件。 */
    HELMET("头盔"),

    /** 只算穿在胸前的那一件。 */
    CHESTPLATE("胸甲"),

    /** 只算穿在脚上的那一件。 */
    BOOTS("靴子"),

    /**
     * 只算盾牌。
     *
     * <p>{@link #ARMOR} 那个大类里本来就含盾牌（凡是钉在装备上的遗物都能钉上去）。这一类是从
     * 那堆里<b>单把盾牌挑出来</b>用的：盾徽一类的遗物只对盾牌有意义，钉在头盔或靴子上说不通。</p>
     */
    SHIELD("盾牌"),

    /** 用来打人的东西。 */
    WEAPON("武器"),

    /**
     * 贴上去近身打人的东西：剑、斧、三叉戟。
     *
     * <p>与 {@link #RANGED_WEAPON} 合起来正好是 {@link #WEAPON} 的全部内容——
     * 「武器」是那一大类，这两个是它按打法分出来的两半。之所以要分，
     * 是因为有的东西只对其中一半有意义：涂在弓弩上的油加的是<b>箭的伤害</b>，
     * 缠在剑柄上的带子加的是<b>挥砍的伤害</b>，两者换过来都毫无作用。</p>
     */
    MELEE_WEAPON("近战武器"),

    /** 用来远程打人的东西：弓与弩。 */
    RANGED_WEAPON("远程武器"),

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
     * <p>注意<b>一件物品可以同时属于两类</b>（头盔既是「装备」也是「头盔」，斧既是工具也是武器），
     * 因此判断结果不能当作「它唯一属于哪一类」来用——需要类别时请逐类去问。</p>
     *
     * @param item 待判断的物品
     * @return 这件物品是否算这类目标
     */
    public boolean covers(Item item) {
        return switch (this) {
            case ARMOR -> item instanceof ArmorItem || item instanceof ShieldItem;
            case ARMOR_PIECE -> item instanceof ArmorItem;
            case HELMET -> isArmorIn(item, EquipmentSlot.HEAD);
            case CHESTPLATE -> isArmorIn(item, EquipmentSlot.CHEST);
            case BOOTS -> isArmorIn(item, EquipmentSlot.FEET);
            case SHIELD -> item instanceof ShieldItem;
            case WEAPON -> item instanceof SwordItem
                    || item instanceof AxeItem
                    || item instanceof TridentItem
                    || item instanceof BowItem
                    || item instanceof CrossbowItem;
            case MELEE_WEAPON -> item instanceof SwordItem
                    || item instanceof AxeItem
                    || item instanceof TridentItem;
            case RANGED_WEAPON -> item instanceof BowItem || item instanceof CrossbowItem;
            case TOOL -> item instanceof MiningToolItem
                    || item instanceof HoeItem
                    || item instanceof FishingRodItem
                    || item instanceof ShearsItem
                    || item instanceof FlintAndSteelItem;
        };
    }

    /**
     * 判断一件物品是不是穿在指定部位的防具。
     *
     * <p>写法照抄原版 {@code EnchantmentTarget.ARMOR_HEAD}／{@code ARMOR_FEET} 的判定，
     * 这样「头盔」「靴子」的定义与游戏自己的理解完全一致，不必另外维护一份物品名单。</p>
     *
     * @param item 待判断的物品
     * @param slot 部位
     * @return 是穿在该部位的防具时返回 {@code true}
     */
    private static boolean isArmorIn(Item item, EquipmentSlot slot) {
        return item instanceof ArmorItem armor && armor.getSlotType() == slot;
    }

    /**
     * 判断一件物品是否**算得上任意一类**目标（装备 / 武器 / 工具之一）。
     *
     * <p>没有这个方法时，调用处都会自己写一遍"逐类问一遍"的循环——本项目已经出现过
     * 两处同样的循环，正是"改一处漏一处"的温床。统一走这里。</p>
     *
     * @param item 待判断的物品
     * @return 至少中一类时返回 {@code true}
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
