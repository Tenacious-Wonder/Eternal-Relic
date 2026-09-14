package org.eternalrelic.relic;

/**
 * 材料稀有度 —— 制作遗物所用的原材料与半成品的成色分级。
 *
 * <p>七档从「遍地都是的残渣」一路到「万物源头级别的本质之物」，是判断一份材料值不值得
 * 用来制作遗物的依据。每档都带有自己的代表色，物品提示框里会用它把档位名染成对应颜色，
 * 玩家扫一眼颜色就知道手上这份料的成色。</p>
 *
 * <p>原版材料的分档以「下界之星 = 精萃」为锚点校准（见
 * {@link org.eternalrelic.registry.VanillaMaterialRarities}）。</p>
 */
public enum MaterialRarity {

    /** 残渣、边角料，遍地都是。 */
    DEBRIS(1, "碎屑", 0x8B7355),

    /** 未加工的原始形态，稍有价值。 */
    ROUGH_STONE(2, "粗石", 0xC2A24B),

    /** 已可正经使用的完整材料。 */
    LUMBER(3, "成材", 0xB06A3B),

    /** 提纯、浓缩后的精华部分。 */
    ESSENCE(4, "精萃", 0xC0C0C0),

    /** 稀少、受追捧的贵重之物。 */
    TREASURE(5, "珍品", 0xE0902A),

    /** 可遇不可求的顶级材料。 */
    SUPREME(6, "至宝", 0xD32F2F),

    /** 万物源头级别的本质之物。 */
    SOURCE(7, "源质", 0x8E44AD);

    private final int level;
    private final String displayName;
    private final int color;

    private MaterialRarity(int level, String displayName, int color) {
        this.level = level;
        this.displayName = displayName;
        this.color = color;
    }

    /**
     * @return 档位序号，1 为最低、7 为最高
     */
    public int level() {
        return this.level;
    }

    /**
     * @return 档位名称，例如「精萃」
     */
    public String displayName() {
        return this.displayName;
    }

    /**
     * @return 该档位的代表色（RGB）
     */
    public int color() {
        return this.color;
    }
}
