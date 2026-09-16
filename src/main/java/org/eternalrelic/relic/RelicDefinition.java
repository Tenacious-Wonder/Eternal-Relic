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
 * <p>两处能力插槽各自独立：{@code effect} 管「一直挂在身上的数值」，
 * {@code ward} 管「每次挨打时出手一次」。一件遗物可以只有其中之一，也可以两样都有，
 * 没有的那一项留 {@code null} 即可——相应的能力类查不到配置就会自动跳过这件遗物。</p>
 *
 * @param item   遗物对应的物品
 * @param id     内部编号
 * @param rarity 遗物固有稀有度（沿用材料档位）
 * @param effect 携带时生效的属性加成；没有则为 {@code null}
 * @param ward   受到攻击时的守护效果；没有则为 {@code null}
 */
public record RelicDefinition(Item item, Identifier id, MaterialRarity rarity, RelicEffect effect, DamageWard ward) {

    /**
     * 登记一件没有守护效果的遗物。
     *
     * @param item   遗物对应的物品
     * @param id     内部编号
     * @param rarity 遗物固有稀有度（沿用材料档位）
     * @param effect 携带时生效的属性加成；没有则为 {@code null}
     */
    public RelicDefinition(Item item, Identifier id, MaterialRarity rarity, RelicEffect effect) {
        this(item, id, rarity, effect, null);
    }

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

    /**
     * @return 本遗物是否会在玩家受到攻击时出手守护
     */
    public boolean hasDamageWard() {
        return this.ward != null;
    }
}
