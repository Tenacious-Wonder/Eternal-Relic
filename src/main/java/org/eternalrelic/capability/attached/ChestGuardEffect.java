package org.eternalrelic.capability.attached;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.DamageTypeTags;

import org.eternalrelic.registry.ChestGuards;
import org.eternalrelic.relic.ChestGuard;
import org.eternalrelic.relic.RelicAttachment;
import org.twcore.api.bodypart.BodyPart;
import org.twcore.api.bodypart.BodyPartHit;
import org.twcore.api.event.BodyPartHitEvent;
import org.twcore.api.event.PlayerDamageEvent;
import org.twcore.api.event.TwEventPhases;

/**
 * 「胸甲护具」能力：被近战或远程打中护着的那一块时，让那一击少掉几点伤害；
 * 缝着会挡魔法的胸甲片时，魔法伤害也少掉一点。
 *
 * <p><b>只在缝着的时候管用。</b>它看的是玩家<b>正穿着的那件胸甲</b>上附着了哪些护具
 * （见 {@link RelicAttachment#attachedTo}）：胸甲脱了、护具拆了、或者把它放在背包里，
 * 都一点不减。这与它们在遗物表里登记的「只认附着份」是同一条口径。</p>
 *
 * <p><b>减在那一步、减多少，都不在这里决定。</b>部位判定与伤害结算的接线由 TW Core 负责
 * （{@link BodyPartHitEvent} / {@link PlayerDamageEvent}），减伤点数由
 * {@link ChestGuards 胸甲护具表} 登记，这里只做一件事：把两边接起来。</p>
 *
 * <p><b>肩甲、胸甲片与斗篷共用这一个能力类</b>：它们做的本来就是同一件事，只是护的部位不同
 * （两侧肩膀 / 正胸 / 后背），登记的数值不同。护住同一块的多件护具之间<b>取最狠的那一件</b>，
 * 不相加——否则几件一叠就能把轻击整个抹掉。不同部位的各算各的，一件胸甲上可以同时缝着
 * 肩甲、胸甲片与斗篷，挨打时三处互不干扰。</p>
 *
 * <p><b>魔法那一条不看部位</b>：药水、凋零、龙息这类伤害是从身体内部发作的，游戏不会产生
 * 「打中哪儿」的消息，因此只有「缝着没有」这一个条件。而且要<b>至少给对方留 1 点</b>。</p>
 *
 * <p>注意这与<b>盔甲韧性</b>不是一回事：韧性按属性加成的既有规矩「各缝一件各算一份」累加，
 * 而这里只管挨打时减掉的那几点。两者各走各的路。</p>
 */
public final class ChestGuardEffect {

    private ChestGuardEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，接上两条事件。
     */
    public static void register() {
        BodyPartHitEvent.BODY_PART_HIT.register(ChestGuardEffect::onHit);
        PlayerDamageEvent.PLAYER_DAMAGE.register(TwEventPhases.NORMAL, ChestGuardEffect::onDamage);
    }

    /**
     * 打中护着的那一块时，把这一箭整个弹开。
     *
     * <p>弹回的动作为 TW Core 既有行为：速度反向并衰减、朝向加 180 度，
     * 与原版被盾牌挡下的箭表现一致。这里只判定要不要弹。</p>
     *
     * @param hit 一次命中
     * @return 挡下或放行
     */
    private static BodyPartHitEvent.Result onHit(BodyPartHit hit) {
        return deflects(hit.player(), hit.part())
                ? BodyPartHitEvent.Result.block()
                : BodyPartHitEvent.Result.pass();
    }

    /**
     * 按打中的部位与胸甲上的护具，把这一击减掉几点。
     *
     * <p>此刻的 {@code currentDamage} 是护甲、附魔与状态效果都算完之后、金心抵扣之前的数值，
     * 正是护具该管的那一段。</p>
     *
     * @param context       本次结算的只读信息
     * @param currentDamage 当前伤害值
     * @return 减完之后的伤害；没有护具护着时原样放行
     */
    private static PlayerDamageEvent.Result onDamage(PlayerDamageEvent.Context context, float currentDamage) {
        // 按部位的减伤：胸甲上缝着的护具各减几点，**可以扣到 0**——挡下轻击本来就是护具的本事
        float guarded = Math.max(0.0F, currentDamage - reductionFor(context.player(), context.part()));

        // 魔法减伤另算，而且**至少给对方留 1 点**。
        //
        // 为什么单独放宽这一条：中毒与凋零的伤害是每跳 1 点，而这里也是减 1 点——
        // 若照"扣到 0 为止"，这 1 点会被整个抹平，等于白送一个「免疫中毒与凋零」，
        // 比「受到的魔法伤害减少 1 点」这句话强得多。因此这一条只削不灭。
        // 末尾再与扣之前取较小值：万一原始伤害本来就不足 1 点，不能被这条抬高。
        if (isMagicDamage(context.source())) {
            float magic = magicReductionFor(context.player());
            if (magic > 0.0F) {
                guarded = Math.min(guarded, Math.max(1.0F, guarded - magic));
            }
        }

        return guarded == currentDamage
                ? PlayerDamageEvent.Result.keep()
                : PlayerDamageEvent.Result.set(guarded);
    }

    /**
     * 算出这一击应当少掉多少伤害。
     *
     * <p>扣到 0 为止由调用方保证。这一击没有部位（药水、摔落之类）时部位为 {@code null}，
     * 这里直接返回 0。</p>
     *
     * @param player 挨打的玩家
     * @param part   这一击打中的部位；没有部位的伤害为 {@code null}
     * @return 这一击应当少掉的伤害点数；没有护具护着、或打在别处时返回 0
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
            ChestGuard guard = ChestGuards.guardOf(attached);
            if (guard != null && guard.guards(part)) {
                best = Math.max(best, guard.reduction());
            }
        }

        return best;
    }

    /**
     * 算出这一下魔法伤害应当少掉多少。
     *
     * <p><b>不看部位</b>：魔法伤害没有「打中哪儿」，只要胸甲上缝着会挡魔法的护具就算数。
     * 缝了多片时同样只认挡得最多的那一片。</p>
     *
     * <p><b>这里给的是「该减多少」，不是「最终减多少」</b>：真正结算的那一处会给这一击
     * <b>至少留 1 点</b>——中毒与凋零每跳只有 1 点，抹平就等于白送一个免疫。</p>
     *
     * @param player 挨打的玩家
     * @return 应当少掉的魔法伤害点数；没缝着这类护具时返回 0
     */
    public static float magicReductionFor(PlayerEntity player) {
        ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
        if (chest.isEmpty()) {
            return 0.0F;
        }

        float best = 0.0F;

        for (Item attached : RelicAttachment.attachedTo(chest)) {
            ChestGuard guard = ChestGuards.guardOf(attached);
            if (guard != null) {
                best = Math.max(best, guard.magicReduction());
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
     * 这一箭是否会<b>被护具弹开</b>。
     *
     * <p>只有射中护着那一块时才有机会；概率由 {@link ChestGuards 胸甲护具表} 登记。
     * 护住同一块的多件护具之间<b>取概率最高的那件</b>，与减伤取最高的规矩一致——
     * 不叠加，免得几件一叠就变成必弹。</p>
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
            ChestGuard guard = ChestGuards.guardOf(attached);
            if (guard != null && guard.canDeflect() && guard.guards(part)) {
                bestChance = Math.max(bestChance, guard.deflectChance());
            }
        }

        return bestChance > 0.0F && player.getRandom().nextFloat() < bestChance;
    }
}
