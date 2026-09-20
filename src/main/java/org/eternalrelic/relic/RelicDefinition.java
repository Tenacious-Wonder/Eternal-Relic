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
 * <p>四处能力插槽各自独立：{@code effect} 管「一直挂在身上的数值」，
 * {@code ward} 管「每次挨打时出手一次」，{@code arrivalEffect} 管「刚开始携带时要不要亮相」，
 * {@code attachmentOnly} 则决定那份数值<b>从哪儿算起</b>（见下）。一件遗物可以只有其中任意几项；
 * 没有的那一项留 {@code null}（开关留 {@code false}）即可，相应的能力类查不到配置就会自动跳过这件遗物。</p>
 *
 * <p><b>{@code attachmentOnly}：属性加成只认「缝在装备上的那一份」。</b>
 * 关着（默认）时，遗物放在背包里就生效、缝在装备上也生效；打开之后，背包里那一份完全不算数，
 * 必须真的缝在正穿着 / 正拿着的装备上才给属性（坚铁甲片就是这一类）。</p>
 *
 * @param item           遗物对应的物品
 * @param id             内部编号
 * @param rarity         遗物固有稀有度（沿用材料档位）
 * @param effect         携带时生效的属性加成；没有则为 {@code null}
 * @param ward           受到攻击时的守护效果；没有则为 {@code null}
 * @param arrivalEffect  刚开始携带时是否播放一记「入手」表现
 * @param attachmentOnly 属性加成是否只认附着份（放在背包里不算数）
 */
public record RelicDefinition(Item item, Identifier id, MaterialRarity rarity, RelicEffect effect, DamageWard ward,
        boolean arrivalEffect, boolean attachmentOnly) {

    /**
     * 登记一件没有守护效果、也不做入手表现的遗物。
     *
     * @param item   遗物对应的物品
     * @param id     内部编号
     * @param rarity 遗物固有稀有度（沿用材料档位）
     * @param effect 携带时生效的属性加成；没有则为 {@code null}
     */
    public RelicDefinition(Item item, Identifier id, MaterialRarity rarity, RelicEffect effect) {
        this(item, id, rarity, effect, null, false, false);
    }

    /**
     * 登记一件不做入手表现的遗物。
     *
     * @param item   遗物对应的物品
     * @param id     内部编号
     * @param rarity 遗物固有稀有度（沿用材料档位）
     * @param effect 携带时生效的属性加成；没有则为 {@code null}
     * @param ward   受到攻击时的守护效果；没有则为 {@code null}
     */
    public RelicDefinition(Item item, Identifier id, MaterialRarity rarity, RelicEffect effect, DamageWard ward) {
        this(item, id, rarity, effect, ward, false, false);
    }

    /**
     * @return 显示名的翻译键，例如 {@code item.eternal_relic.aota_branch}
     */
    public String translationKey() {
        return this.item.getTranslationKey();
    }

    /**
     * 效果说明的翻译键，例如 {@code item.eternal_relic.aota_branch.desc}。
     *
     * <p>这段文字已经不在物品提示框里显示了——提示框只留一句「按左 Shift 详细查看」，
     * 正文一律由遗物界面呈现。</p>
     *
     * @return 效果说明的翻译键
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

    /**
     * @return 本遗物刚开始被携带时，是否要在玩家身上放一记「入手」表现
     */
    public boolean hasArrivalEffect() {
        return this.arrivalEffect;
    }

    /**
     * @return 本遗物的属性加成是否<b>只认附着份</b>——放在背包里不算数，必须缝在装备上
     */
    public boolean isAttachmentOnly() {
        return this.attachmentOnly;
    }
}
