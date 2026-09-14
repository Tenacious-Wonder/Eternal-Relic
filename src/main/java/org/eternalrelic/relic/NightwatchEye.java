package org.eternalrelic.relic;

/**
 * 守夜之瞳的左右两只眼。
 *
 * <p>两颗眼珠是两件彼此独立的遗物：左眼负责在暗中看清环境，右眼负责照见附近的活物。
 * 玩家可以只装入其中一只，也可以两只都装入；装入哪只，就单独承担哪只的代价。</p>
 *
 * <p>这里只区分「是哪只眼」，不含「能量是否耗尽」。耗尽与否是物品自身的状态，
 * 由 {@link org.eternalrelic.item.NightwatchEyeItem} 分开表达。</p>
 */
public enum NightwatchEye {

    /** 左眼：低光环境下获得夜视。 */
    LEFT("left"),

    /** 右眼：低光环境下照见附近的活物。 */
    RIGHT("right");

    /** 用于派生物品编号与生命上限扣减标识的短名。 */
    private final String id;

    private NightwatchEye(String id) {
        this.id = id;
    }

    /**
     * @return 本只眼的短名，例如 {@code left}
     */
    public String id() {
        return this.id;
    }
}
