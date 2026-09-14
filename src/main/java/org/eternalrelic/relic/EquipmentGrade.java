package org.eternalrelic.relic;

import java.util.Locale;

import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

/**
 * 装备品阶 —— 一件工具或装备做出来之后的成色等级。
 *
 * <p>与材料档位 {@link MaterialRarity} 是两条线：材料看的是那块料本身有多精，
 * 装备品阶看的是成品做出来有多好，且它取决于<b>两个会变动的因素</b>——</p>
 * <ol>
 *   <li><b>投入的材料</b>：材料越精，基准档次越高。木石之作只是粗胚，铁铜之作出自良工，
 *       钻石与下界合金已达名匠的门槛（见
 *       {@link org.eternalrelic.registry.VanillaItemGrades}）。</li>
 *   <li><b>制作者的熟练度</b>：同一份材料，熟练工匠能做出更高一档的成品，
 *       由 {@link #withProficiency(int, int)} 叠加。</li>
 * </ol>
 *
 * <p>因此「铁剑」这件东西本身没有固定品阶——新手打出来是粗胚，老师傅打出来可能就是良工。
 * 原版材料配合熟练度，最高可达名匠。</p>
 */
public enum EquipmentGrade {

    /** 粗胚 —— 勉强成形，堪用而已。 */
    ROUGH(1, "粗胚", 0x9E9E9E),

    /** 良工 —— 手艺扎实的成品。 */
    FINE(2, "良工", 0x63C46B),

    /** 名匠 —— 出自名家之手。 */
    MASTER(3, "名匠", 0x4A9EE8),

    /** 英杰 —— 足以配得上英雄的名号。 */
    HERO(4, "英杰", 0x9B59B6),

    /** 圣物 —— 被供奉、被传颂之物。 */
    SACRED(5, "圣物", 0xF1C40F),

    /** 神陨 —— 神明陨落时遗留之物。 */
    DIVINE(6, "神陨", 0xE74C3C),

    /** 永恒 —— 超越时间的存在。 */
    ETERNAL(7, "永恒", 0xA64CE8);

    private final int level;
    private final String displayName;
    private final int color;

    private EquipmentGrade(int level, String displayName, int color) {
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
     * @return 档位名称，例如「名匠」
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

    /**
     * 按档位序号取档位，用于叠加熟练度加成后落到具体档位上。
     *
     * @param level 目标序号，超出范围时收敛到最近的一档
     * @return 对应的档位
     */
    public static EquipmentGrade ofLevel(int level) {
        EquipmentGrade[] values = values();
        int index = Math.max(0, Math.min(values.length - 1, level - 1));
        return values[index];
    }

    /**
     * 计算叠加熟练度之后的最终品阶。
     *
     * @param baseLevel        由材料决定的基准档位
     * @param proficiencyBonus 锻造熟练度带来的额外档位，通常为 0 或 1
     * @return 最终品阶，最高不超过永恒
     */
    public static EquipmentGrade withProficiency(int baseLevel, int proficiencyBonus) {
        return ofLevel(baseLevel + Math.max(0, proficiencyBonus));
    }

    /**
     * @return 在提示框中显示这一档名称所用的文本样式
     */
    public Style style() {
        return Style.EMPTY.withColor(TextColor.fromRgb(this.color));
    }

    /**
     * @return 提示框中显示的品阶文本，例如「品阶：名匠」
     */
    public Text asTooltip() {
        return Text.translatable("grade.eternal_relic." + this.name().toLowerCase(Locale.ROOT))
                .setStyle(this.style());
    }
}