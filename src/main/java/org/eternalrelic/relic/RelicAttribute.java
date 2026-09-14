package org.eternalrelic.relic;

import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;

/**
 * 遗物可以影响的属性类型。
 *
 * <p>这是「遗物表」能填的效果清单：表里写哪一个，遗物携带生效时就改变哪个属性。
 * 需要让遗物能影响新的属性时，在这里补一项即可，核心逻辑不必改动。</p>
 */
public enum RelicAttribute {

    /** 生命上限。 */
    MAX_HEALTH(EntityAttributes.GENERIC_MAX_HEALTH),

    /** 移动速度。 */
    MOVEMENT_SPEED(EntityAttributes.GENERIC_MOVEMENT_SPEED),

    /** 攻击力。 */
    ATTACK_DAMAGE(EntityAttributes.GENERIC_ATTACK_DAMAGE),

    /** 护甲值。 */
    ARMOR(EntityAttributes.GENERIC_ARMOR);

    private final EntityAttribute attribute;

    private RelicAttribute(EntityAttribute attribute) {
        this.attribute = attribute;
    }

    /**
     * @return 本项对应的游戏属性
     */
    public EntityAttribute attribute() {
        return this.attribute;
    }
}
