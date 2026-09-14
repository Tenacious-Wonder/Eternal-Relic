package org.eternalrelic.relic;

import net.minecraft.item.Item;
import net.minecraft.util.Identifier;

/**
 * 一件遗物的身份信息，也就是「遗物表」里的一行。
 *
 * <p>存在的意义是把散落各处的信息收进同一份名单：物品本身、内部编号、显示名
 * （由物品的翻译键推出）、稀有度、以及它携带时生效的属性加成。以后做图鉴、掉落表这类
 * 需要「所有遗物」的功能时，都从这份名单读，不再另立第二份。</p>
 *
 * <p>这里的稀有度取自<b>材料档位</b> {@link MaterialRarity}，登记一次便固定不变——
 * 遗物的成色由它自身决定，不随谁做出来而改变。</p>
 *
 * @param item   遗物对应的物品
 * @param id     内部编号
 * @param rarity 遗物固有稀有度（沿用材料档位）
 * @param effect 携带时生效的属性加成；没有任何属性加成的遗物为 {@code null}
 */
public record RelicDefinition(Item item, Identifier id, MaterialRarity rarity, RelicEffect effect) {

    /**
     * @return 显示名的翻译键，例如 {@code item.eternal_relic.aota_branch}
     */
    public String translationKey() {
        return this.item.getTranslationKey();
    }

    /**
     * @return 悬浮说明文字的翻译键，例如 {@code item.eternal_relic.aota_branch.desc}
     */
    public String descriptionKey() {
        return this.item.getTranslationKey() + ".desc";
    }

    /**
     * @return 本遗物是否带有「携带生效」的属性加成
     */
    public boolean hasCarriedEffect() {
        return this.effect != null;
    }
}
