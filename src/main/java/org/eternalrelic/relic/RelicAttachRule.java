package org.eternalrelic.relic;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * <b>「能不能附」的唯一裁决处</b> —— 辅料对不对、能不能附到这件东西上、成品长什么样，全在这里回答。
 *
 * <p><b>为什么必须只有这一处</b>：附着有两条入口 —— 原版**锻造台**（配方驱动）与**遗物装卸台**
 * （界面驱动）。原先这两条路各自把同一套判断抄了一遍（连"三类目标逐类问一遍"的循环都各写了一遍），
 * 一共散成五份。那种写法的问题是：<b>改一处、漏一处，两条路的判定会慢慢分叉</b> ——
 * 装上同样的三样东西，锻造台说能做、装卸台说不能做，而且不会有人报错。</p>
 *
 * <p>因此把判断收在这里，两条入口都调它。以后要改规则（比如允许附到新类型的物品上），
 * <b>只改这一个文件</b>。</p>
 */
public final class RelicAttachRule {

    private RelicAttachRule() {
    }

    /**
     * 这件物品是不是某件遗物指定的辅料。
     *
     * @param item 待判断的物品
     * @return 有任何一件遗物把它当作辅料时返回 {@code true}
     */
    public static boolean isMaterialItem(Item item) {
        for (RelicAttachment.Spec spec : RelicAttachment.all().values()) {
            if (item == spec.material()) {
                return true;
            }
        }

        return false;
    }

    /**
     * 这件物品算不算「可附遗物」—— 也就是有没有登记在可附白名单里。
     *
     * @param item 待判断的物品
     * @return 在名单里返回 {@code true}
     */
    public static boolean isRelicItem(Item item) {
        return RelicAttachment.specOf(item) != null;
    }

    /**
     * 这件物品算不算装备 / 武器 / 工具之一（也就是"能不能被附"）。
     *
     * @param item 待判断的物品
     * @return 三类里至少中一类时返回 {@code true}
     */
    public static boolean isAttachableTarget(Item item) {
        return AttachTarget.coversAny(item);
    }

    /**
     * 这件物品身上是不是已经附了东西。
     *
     * <p>拆解时要用：一件东西即使**现在**不在可附名单里（例如名单改过了），
     * 只要它身上还挂着遗物，也必须能放进装卸台把遗物拆下来，否则那枚遗物就永远拿不回来了。</p>
     *
     * @param stack 待判断的物品
     * @return 附了至少一枚时返回 {@code true}
     */
    public static boolean hasAttachments(ItemStack stack) {
        return !RelicAttachment.attachedTo(stack).isEmpty();
    }

    /**
     * 放在装卸台中间的那件东西能不能被受理 —— 能被附的，或者身上已经附了东西的。
     *
     * @param stack 待判断的物品
     * @return 可以放进工件格时返回 {@code true}
     */
    public static boolean canBeWorkedOn(ItemStack stack) {
        return isAttachableTarget(stack.getItem()) || hasAttachments(stack);
    }

    /**
     * 辅料与遗物对不对得上。
     *
     * @param relic    要附的那件遗物
     * @param material 放进辅料格的物品
     * @return 对得上返回 {@code true}
     */
    public static boolean materialMatches(ItemStack relic, ItemStack material) {
        if (relic.isEmpty() || material.isEmpty()) {
            return false;
        }

        RelicAttachment.Spec spec = RelicAttachment.specOf(relic.getItem());
        return spec != null && material.isOf(spec.material());
    }

    /**
     * 这件遗物能不能附到这件目标上。
     *
     * @param target 目标（装备 / 武器 / 工具）
     * @param relic  要附的遗物
     * @return 能附返回 {@code true}
     */
    public static boolean canAttach(ItemStack target, ItemStack relic) {
        return !relic.isEmpty() && RelicAttachment.accepts(target, relic.getItem());
    }

    /**
     * 三样东西凑不凑得成一次附着。
     *
     * @param material 辅料
     * @param target   要被附着的目标
     * @param relic    遗物
     * @return 三者齐备且规则允许时返回 {@code true}
     */
    public static boolean matches(ItemStack material, ItemStack target, ItemStack relic) {
        if (target.isEmpty() || !materialMatches(relic, material)) {
            return false;
        }

        return canAttach(target, relic);
    }

    /**
     * 算出附着之后的成品 —— **目标的一份副本，只是数据里多记了一枚遗物**。
     *
     * <p>复制而不是改动原件：这样附魔、耐久、自定义名字都跟着原物品走，
     * 而玩家原来那件东西在被拿走成品时才真正扣除（两条入口都靠这个语义）。</p>
     *
     * @param material 辅料
     * @param target   要被附着的目标
     * @param relic    遗物
     * @return 附着后的成品；三样没凑齐或规则不允许时返回空
     */
    public static ItemStack previewOf(ItemStack material, ItemStack target, ItemStack relic) {
        if (!matches(material, target, relic)) {
            return ItemStack.EMPTY;
        }

        ItemStack result = target.copy();
        result.setCount(1);
        RelicAttachment.attach(result, relic.getItem());
        return result;
    }
}
