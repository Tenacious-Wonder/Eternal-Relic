package org.eternalrelic.relic;

/**
 * 装备配件的类别 —— <b>同一类配件，一件装备上只能有一件</b>。
 *
 * <p>这条规矩是为了让"装了什么"一眼看得懂：一件胸甲上挂着两套肩甲、或者两只左肩甲，
 * 既不美观也说不通（效果本来也只取其中最好的那一件）。分档变体（皮革 / 鳞片 / 龟壳）
 * 属于同一类，所以它们之间也是互斥的——这正是"同类只能一件"的意思。</p>
 *
 * <p><b>左与右是两类，不是一类。</b>玩家可以左肩装一只、右肩装一只，这是正常的配装；
 * 只有"同一侧出现两件"才被拦下。整套（左右合起来的那一件）自成一类，
 * 因此它不会与单只的左肩甲冲突。</p>
 *
 * <p><b>不是所有能钉上去的东西都算配件。</b>纹章一类（勇气、太阳、月亮、川流、永恒）
 * 不属于任何类别，因此不受这条限制——它们本来就是可以叠着钉的。判断"某个物品属于哪一类"
 * 一律走 {@link RelicAttachment#categoryOf(net.minecraft.item.Item)}。</p>
 */
public enum FittingCategory {

    /** 护住左肩的那一只。 */
    SHOULDER_LEFT("肩甲·左"),

    /** 护住右肩的那一只。 */
    SHOULDER_RIGHT("肩甲·右"),

    /** 左右两只合成而来的一整套，两侧都护。 */
    SHOULDER_PAIR("肩甲·整套"),

    /** 缝在防具内侧的衬里。 */
    LINING("内衬"),

    /** 铆在防具上的金属片。 */
    PLATE("甲片");

    private final String displayName;

    FittingCategory(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return 这类配件的中文名，供提示与日志使用
     */
    public String displayName() {
        return this.displayName;
    }
}
