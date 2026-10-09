package org.eternalrelic.mixin;

import java.util.UUID;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ShieldItem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * <b>让原版盾牌同时是一把武器</b> —— 给「拿在主手的盾」补上攻击力与攻速，好让它成为一种武器流派。
 *
 * <p>制作者 2026-10-06 定：盾牌本身要能打人（总伤害 <b>5 点</b>、攻速 <b>0.6</b>），
 * 而「破阵之书」学的冲刺技能又按它算伤害（主手盾牌 5 × 2.5 = 12.5 点）。</p>
 *
 * <h2>为什么改在这里</h2>
 * <p>原版 {@link ShieldItem} <b>没有覆写</b> {@code getAttributeModifiers}，走的是 {@link Item}
 * 的默认实现（什么都不给）。因此把注入点放在 {@code Item} 上、再用
 * {@code instanceof ShieldItem} 挑出盾牌，既不影响任何原版武器（剑、斧、锤各自覆写了那一处，
 * 根本不会经过这里），也不必去改原版类本身。</p>
 *
 * <p><b>数值怎么换算</b>（与两把锤子、剥皮小刀同一套）：面板上的攻击力是
 * 「玩家基础 1 + 这里给的值」，因此要 5 点就填 <b>4</b>；面板上的攻速是
 * 「玩家基础 4 + 这里给的值」，因此要 0.6 就填 <b>−3.4</b>。</p>
 *
 * <p><b>为什么用「包一层」而不是顶掉</b>：万一将来有别的模组也想给盾牌加属性，
 * 两边会叠加而不是互相作废（设计决策 56 的口径）。</p>
 *
 * <p>⚠️ 与冲刺技能的分工：<b>不举盾时</b>盾牌就是一件普通武器（左键照常挥砍）；
 * <b>举盾时</b>原版本来就会屏蔽左键（{@code MinecraftClient} 里那句
 * {@code !player.isUsingItem()}），那一下正好被破阵之书借去当蓄力键。</p>
 */
@Mixin(Item.class)
public abstract class ShieldWeaponMixin {

    /**
     * 攻击力修饰符的名字。
     *
     * <p>与原版武器用的是同一个 UUID —— 玩家主手同时只拿一件东西，因此不会撞车；
     * 而沿用同一个值，也让「这是主手武器的伤害加成」这件事对原版与别的模组保持原样。</p>
     */
    private static final UUID ATTACK_DAMAGE_MODIFIER_ID =
            UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");

    /** 攻速修饰符的名字。同上，沿用原版那一个。 */
    private static final UUID ATTACK_SPEED_MODIFIER_ID =
            UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3");

    /** 补的攻击力。<b>4 + 玩家基础 1 = 面板 5 点</b>。 */
    private static final double SHIELD_ATTACK_DAMAGE = 4.0D;

    /** 补的攻速修正。<b>−2.8 + 玩家基础 4 = 面板 1.2</b>（制作者 2026-10-06 按手感从 0.6 提上来的）。 */
    private static final double SHIELD_ATTACK_SPEED = -2.8D;

    /**
     * 算物品属性时，若这件物品是盾牌、且问的是主手，就补上武器那两条。
     *
     * @param original 游戏原本算出来的属性表（盾牌原本是空的）
     * @param slot     问的是哪个装备槽
     * @return 主手盾牌多出攻击力与攻速的属性表；其余情况原样返回
     */
    @ModifyReturnValue(method = "getAttributeModifiers", at = @At("RETURN"))
    private Multimap<EntityAttribute, EntityAttributeModifier> eternal_relic$shieldAsWeapon(
            Multimap<EntityAttribute, EntityAttributeModifier> original, EquipmentSlot slot) {

        if (!((Object) this instanceof ShieldItem) || slot != EquipmentSlot.MAINHAND) {
            return original;
        }

        ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> builder = ImmutableMultimap.builder();
        builder.putAll(original);

        builder.put(EntityAttributes.GENERIC_ATTACK_DAMAGE,
                new EntityAttributeModifier(ATTACK_DAMAGE_MODIFIER_ID, "Weapon modifier",
                        SHIELD_ATTACK_DAMAGE, EntityAttributeModifier.Operation.ADDITION));

        builder.put(EntityAttributes.GENERIC_ATTACK_SPEED,
                new EntityAttributeModifier(ATTACK_SPEED_MODIFIER_ID, "Weapon modifier",
                        SHIELD_ATTACK_SPEED, EntityAttributeModifier.Operation.ADDITION));

        return builder.build();
    }
}
