package org.eternalrelic.registry;

import net.minecraft.enchantment.Enchantments;

import org.eternalrelic.relic.RelicEnchantmentBonus;

/**
 * <b>遗物补魔表</b> —— 哪些遗物会给它所附着的物品补上附魔，各补哪条、补几级。
 *
 * <p>与 {@link AttachableRelics} 是两份名单、各管一段：那张表回答「这枚遗物能不能钉上去、要什么辅料」，
 * 这张表回答「钉上去之后，那件东西多了哪条附魔」。一件遗物可以只出现在其中一张里
 * （铜甲片只加属性、不补附魔），也可以两张都有。</p>
 *
 * <p>补上去的附魔是<b>虚拟的</b>：不写进物品数据，摘下纹章即失效，也不会被铁砧复制走。
 * 细节见 {@link RelicEnchantmentBonus}。</p>
 */
public final class EnchantingRelics {

    private EnchantingRelics() {
    }

    /**
     * 由 {@link ModItems#register()} 调用，触发本类静态内容初始化。
     *
     * <p>必须排在遗物表登记之后——这里引用的都是 {@link ModItems} 里的物品。</p>
     */
    static void register() {
        // ==================== 制作者选定的白名单 ====================

        // 永恒纹章：钉在哪件东西上，那件东西就视同带有「经验修补」——捡经验时自动修补自己。
        // 经验修补在原版最大只有 1 级，所以这里也只能是 1 级。
        RelicEnchantmentBonus.register(ModItems.ETERNAL_EMBLEM, Enchantments.MENDING, 1);
    }
}
