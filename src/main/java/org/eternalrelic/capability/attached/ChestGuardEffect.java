package org.eternalrelic.capability.attached;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.DamageTypeTags;

import org.eternalrelic.bodypart.BodyPart;
import org.eternalrelic.bodypart.BodyPartHit;
import org.eternalrelic.bodypart.BodyPartHits;
import org.eternalrelic.bodypart.RecentBodyPartHit;
import org.eternalrelic.registry.ChestGuards;
import org.eternalrelic.relic.ChestGuard;
import org.eternalrelic.relic.RelicAttachment;

/**
 * 「胸甲护具」能力：被近战或远程打中护着的那一块时，让那一击少掉几点伤害；
 * 缝着会挡魔法的胸甲片时，魔法伤害也少掉一点。
 *
 * <p><b>只在缝着的时候管用。</b>它看的是玩家<b>正穿着的那件胸甲</b>上附着了哪些护具
 * （见 {@link RelicAttachment#attachedTo}）：胸甲脱了、护具拆了、或者把它放在背包里，
 * 都一点不减。这与它们在遗物表里登记的「只认附着份」是同一条口径。</p>
 *
 * <p><b>减在那一步、减多少，都不在这里决定。</b>部位判定由既有的两条路负责
 * （弹射物算命中点、近战算站位），减伤点数由 {@link ChestGuards 胸甲护具表} 登记，
 * 这里只做一件事：把两边接起来——判定时记下打在哪儿，结算时按胸甲上的护具算出差额。</p>
 *
 * <p><b>肩甲、胸甲片与斗篷共用这一个能力类</b>：它们做的本来就是同一件事，只是护的部位不同
 * （两侧肩膀 / 正胸 / 后背），登记的数值不同。护住同一块的多件护具之间<b>取最狠的那一件</b>，
 * 不相加——否则几件一叠就能把轻击整个抹掉。不同部位的各算各的，一件胸甲上可以同时缝着
 * 肩甲、胸甲片与斗篷，挨打时三处互不干扰。</p>
 *
 * <p><b>魔法那一条不看部位</b>：药水、凋零、龙息这类伤害是从身体内部发作的，游戏不会产生
 * 「打中哪儿」的消息，因此只有「缝着没有」这一个条件。真正要不要算魔法伤害，由调用方按伤害类型
 * 判断（见 {@code mixin/PlayerDamageMixin}），而且那一条<b>至少给对方留 1 点</b>。</p>
 *
 * <p>注意这与<b>盔甲韧性</b>不是一回事：韧性按属性加成的既有规矩「各缝一件各算一份」累加，
 * 而这里只管挨打时减掉的那几点。两者各走各的路。</p>
 *
 * <p><b>为什么要用便条传话</b>：判定发生在伤害结算之前，中间隔着几行游戏代码，
 * 没有参数可递；便条本身与它的防残留设计见 {@link RecentBodyPartHit}。
 * 便条由调用方取一次，再问这里——一件胸甲上可以同时缝着好几类护具，而便条取走就没了。</p>
 */
public final class ChestGuardEffect {

    private ChestGuardEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，挂上「打中哪儿」的订阅。
     */
    public static void register() {
        BodyPartHits.register(ChestGuardEffect::rememberHit);
    }

    /**
     * 记下这一击打在哪个部位。
     *
     * <p>订阅本身不判断有没有护具、也不改伤害——它只在每次命中时留一张便条，
     * 真正要不要减、减多少，留给伤害结算那一步去问（见 {@link #reductionFor}）。
     * 这样便条不会在「身上根本没有护具」的玩家身上白留一个游戏刻。</p>
     *
     * @param hit 一次命中
     */
    private static void rememberHit(BodyPartHit hit) {
        RecentBodyPartHit.remember(hit.player(), hit.part());
    }

    /**
     * 算出这一击应当少掉多少伤害。
     *
     * <p>扣到 0 为止由调用方保证。便条过期（那一击并没有真的落下）时，调用方传进来的部位是
     * {@code null}，这里直接返回 0，于是这一击按原样结算。</p>
     *
     * @param player 挨打的玩家
     * @param part   这一击打中的部位；没有有效便条时为 {@code null}
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
     * <b>至少留 1 点</b>——中毒与凋零每跳只有 1 点，抹平就等于白送一个免疫
     * （见 {@code mixin/PlayerDamageMixin} 里的说明）。</p>
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
     * <p><b>本方法不消费那张便条</b>：弹开只意味着「这一箭整个不算」，而便条还要留给伤害结算
     * 去算减伤（万一没弹开）。真弹出去了，伤害根本不会发生，那张便条自然在下一刻过期。</p>
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
