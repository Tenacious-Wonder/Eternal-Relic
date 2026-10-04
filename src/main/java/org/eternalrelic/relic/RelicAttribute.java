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
    ARMOR(EntityAttributes.GENERIC_ARMOR),

    /** 盔甲韧性 —— 挨重击时用来保住减伤的属性，护甲条与提示框上都不显示。 */
    ARMOR_TOUGHNESS(EntityAttributes.GENERIC_ARMOR_TOUGHNESS),

    /**
     * 攻击速度 —— 挥砍之后那一下的冷却恢复快慢。
     *
     * <p>本模组目前只用它做<b>负面代价</b>：铁片肩甲缝得越多，挥砍越慢（见
     * {@code registry/ModRelics}）。登记的是负的百分比，因此换什么武器都是「慢掉同样的比例」，
     * 而不是「固定的点数」——后者会让本来攻速就低的武器慢得不成比例。</p>
     */
    ATTACK_SPEED(EntityAttributes.GENERIC_ATTACK_SPEED),

    /**
     * 幸运 —— 影响战利品与钓鱼的收获。
     *
     * <p>原版属性（{@code generic.luck}），由服务端在跑掉落表时读；因此它<b>只在服务端有意义</b>，
     * 客户端那份改了也不作数，这与其它属性一样由属性系统自己处理，不必额外写代码。</p>
     */
    LUCK(EntityAttributes.GENERIC_LUCK);

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
