package org.eternalrelic.relic;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.registry.tag.DamageTypeTags;

/**
 * 「这一下算不算一次攻击」的判定 —— 需要为玩家「被打时出手」的遗物共用这一份规则。
 *
 * <p>它不关心遗物是什么、出手做什么，只回答一个问题：眼前这次伤害是不是「有人把他打了」。
 * 把规则收在这里而不是抄进每个能力类，是为了让所有遗物对「被打」的理解始终一致——
 * 否则同一场战斗里，一件遗物认为算、另一件认为不算，玩家只会觉得莫名其妙。</p>
 *
 * <p>药的、烧的、饿的一律不算：中毒、凋零、瞬间伤害药水、龙息，以及守卫者光束、
 * 幻术师尖牙这类带着攻击者却走 {@code indirectMagic} 的魔法弹道，都不算一次攻击；
 * 摔落、岩浆、仙人掌、虚空这类环境伤害同样不算。</p>
 *
 * <p>剩下的分两类都算：一类是有人打的（近战、弓箭、有主的爆炸）；
 * 另一类是没有主、但同属「炸过来、砸下来」的物理伤害。后者必须单独列出来，
 * 否则红石引爆的 TNT 会因为查不到攻击者而被放行。</p>
 */
public final class AttackDamage {

    private AttackDamage() {
    }

    /**
     * @param source 伤害来源
     * @return 是否算一次攻击
     */
    public static boolean isAttack(DamageSource source) {
        if (source.isOf(DamageTypes.MAGIC)
                || source.isOf(DamageTypes.INDIRECT_MAGIC)
                || source.isOf(DamageTypes.WITHER)
                || source.isOf(DamageTypes.DRAGON_BREATH)) {
            return false;
        }

        if (source.getAttacker() != null) {
            return true;
        }

        return source.isIn(DamageTypeTags.IS_EXPLOSION)
                || source.isOf(DamageTypes.UNATTRIBUTED_FIREBALL)
                || source.isOf(DamageTypes.FALLING_ANVIL)
                || source.isOf(DamageTypes.FALLING_BLOCK)
                || source.isOf(DamageTypes.FALLING_STALACTITE);
    }
}
