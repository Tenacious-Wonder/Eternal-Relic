package org.eternalrelic.capability.attached;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.DamageTypeTags;

import org.eternalrelic.bodypart.BodyPart;
import org.eternalrelic.registry.ChestplatePlates;
import org.eternalrelic.relic.ChestplatePlate;
import org.eternalrelic.relic.RelicAttachment;

/**
 * 「胸甲片」能力：被近战或远程打中<b>正胸</b>时让那一击少掉几点，被魔法伤害打中时也少掉一点。
 *
 * <p><b>只在缝着的时候管用。</b>它看的是玩家<b>正穿着的那件胸甲</b>上附着了哪些胸甲片
 * （见 {@link RelicAttachment#attachedTo}）：胸甲脱了、甲片拆了、或者把它放在背包里，
 * 都一点不减。这与它在遗物表里登记的「只认附着份」是同一条口径。</p>
 *
 * <p><b>与肩甲的分工</b>：肩甲护左右两侧肩膀，胸甲片护正胸，两者互不重叠，
 * 可以同时缝在一件胸甲上。同一个部位上如果缝了两片胸甲片（目前只有一件，属于将来的事），
 * 减伤也只认最狠的那一件、不叠加——与肩甲那边「同一侧只认最狠的一件」是同一条规矩。</p>
 *
 * <p><b>魔法那一条不看部位</b>：药水、凋零、龙息这类伤害是从身体内部发作的，游戏不会产生
 * 「打中哪儿」的消息，因此只有「缝着没有」这一个条件。真正要不要算魔法伤害，
 * 由调用方按伤害类型判断（见 {@code mixin/PlayerDamageMixin}）。</p>
 *
 * <p><b>为什么要用便条传话</b>：判定发生在伤害结算之前（见
 * {@link org.eternalrelic.bodypart.RecentBodyPartHit}），与肩甲共用同一张便条——
 * 因此<b>取便条的动作在调用方</b>（{@code mixin/PlayerDamageMixin}），两张表共用一份部位，
 * 这里只管"给定部位，该减多少"。</p>
 */
public final class ChestplatePlateEffect {

    private ChestplatePlateEffect() {
    }

    /**
     * 算出这一击应当少掉多少伤害。
     *
     * <p>扣到 0 为止由调用方保证。便条过期（那一击并没有真的落下）时，调用方传进来的部位是
     * {@code null}，这里直接返回 0，于是这一击按原样结算。</p>
     *
     * @param player 挨打的玩家
     * @param part   这一击打中的部位；没有有效便条时为 {@code null}
     * @return 这一击应当少掉的伤害点数；没有胸甲片护着、或打在别处时返回 0
     */
    public static float reductionFor(PlayerEntity player, BodyPart part) {
        if (part == null) {
            return 0.0F;
        }

        ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
        if (chest.isEmpty()) {
            return 0.0F;
        }

        float best = 0.0F;

        for (Item attached : RelicAttachment.attachedTo(chest)) {
            ChestplatePlate plate = ChestplatePlates.plateOf(attached);
            if (plate != null && plate.guards(part)) {
                best = Math.max(best, plate.reduction());
            }
        }

        return best;
    }

    /**
     * 算出这一下魔法伤害应当少掉多少。
     *
     * <p><b>不看部位</b>：魔法伤害没有「打中哪儿」，只要胸甲上缝着会挡魔法的胸甲片就算数。
     * 缝了两片（目前只有一件）时同样只认挡得最多的那一片。</p>
     *
     * @param player 挨打的玩家
     * @return 应当少掉的魔法伤害点数；没缝着这类胸甲片时返回 0
     */
    public static float magicReductionFor(PlayerEntity player) {
        ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
        if (chest.isEmpty()) {
            return 0.0F;
        }

        float best = 0.0F;

        for (Item attached : RelicAttachment.attachedTo(chest)) {
            ChestplatePlate plate = ChestplatePlates.plateOf(attached);
            if (plate != null) {
                best = Math.max(best, plate.magicReduction());
            }
        }

        return best;
    }

    /**
     * 这一下算不算「魔法伤害」—— 决定要不要减那 1 点。
     *
     * <p>由两部分合起来看：原版「女巫抗性」那一组（魔法、间接魔法、音爆、荆棘反伤），
     * 加上原版没归进那一组、但同属魔法类的<b>凋零</b>与<b>龙息</b>。合起来覆盖玩家会遇到的
     * 全部药水与魔法伤害：瞬间伤害药水、守卫者光束、女巫的伤害、凋零效果、末影龙吐息。</p>
     *
     * <p>与 {@link org.eternalrelic.relic.AttackDamage} 的口径一致——那边也是把魔法、间接魔法、
     * 凋零、龙息这四类排除在「被攻击」之外的，两边对「什么是魔法伤害」的理解相同。</p>
     *
     * @param source 伤害来源
     * @return 是否算魔法伤害
     */
    public static boolean isMagicDamage(DamageSource source) {
        return source.isIn(DamageTypeTags.WITCH_RESISTANT_TO)
                || source.isOf(DamageTypes.WITHER)
                || source.isOf(DamageTypes.DRAGON_BREATH);
    }

    /**
     * 这一箭是否会<b>被胸甲片弹开</b>。
     *
     * <p>只有射中护着那一块（正胸）时才有机会；概率由 {@link ChestplatePlates 胸甲片表} 登记。
     * 缝了两片时取概率最高的那件，与减伤取最高的规矩一致。</p>
     *
     * <p><b>本方法不消费那张便条</b>：与肩甲那边同一个理由——弹开只意味着「这一箭整个不算」，
     * 而便条还要留给伤害结算去算减伤（万一没弹开）。</p>
     *
     * <p>只在服务端调用（弹射物那一处的判定已经滤掉了客户端）。</p>
     *
     * @param player 挨这一箭的玩家
     * @param part   这一箭打中的部位
     * @return 这一箭是否被弹开
     */
    public static boolean deflects(PlayerEntity player, BodyPart part) {
        ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
        if (chest.isEmpty()) {
            return false;
        }

        float bestChance = 0.0F;

        for (Item attached : RelicAttachment.attachedTo(chest)) {
            ChestplatePlate plate = ChestplatePlates.plateOf(attached);
            if (plate != null && plate.canDeflect() && plate.guards(part)) {
                bestChance = Math.max(bestChance, plate.deflectChance());
            }
        }

        return bestChance > 0.0F && player.getRandom().nextFloat() < bestChance;
    }
}
