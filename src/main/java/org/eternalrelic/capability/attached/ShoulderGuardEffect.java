package org.eternalrelic.capability.attached;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.eternalrelic.bodypart.BodyPart;
import org.eternalrelic.bodypart.BodyPartHit;
import org.eternalrelic.bodypart.BodyPartHits;
import org.eternalrelic.bodypart.RecentBodyPartHit;
import org.eternalrelic.registry.ShoulderGuards;
import org.eternalrelic.relic.RelicAttachment;
import org.eternalrelic.relic.ShoulderGuard;

/**
 * 「护肩」能力：被近战或远程打中肩膀时，让那一击少掉几点伤害。
 *
 * <p><b>只在缝着的时候管用。</b>它看的是玩家<b>正穿着的那件胸甲</b>上附着了哪些肩甲
 * （见 {@link RelicAttachment#attachedTo}）：胸甲脱了、肩甲拆了、或者把肩甲放在背包里，
 * 都一点不减。这与三件肩甲在遗物表里登记的「只认附着份」是同一条口径。</p>
 *
 * <p><b>减在那一步、减多少，都不在这里决定。</b>部位判定由既有的两条路负责
 * （弹射物算命中点、近战算站位），减伤点数由 {@link ShoulderGuards 肩甲表} 登记，
 * 这里只做一件事：把两边接起来——判定时记下打在哪儿，结算时按胸甲上的肩甲算出差额。</p>
 *
 * <p><b>为什么要用便条传话</b>：判定发生在伤害结算之前，中间隔着几行游戏代码，
 * 没有参数可递；便条本身与它的防残留设计见 {@link RecentBodyPartHit}。</p>
 *
 * <p><b>同一侧只认减得最狠的那一件。</b>胸甲上同时缝了护住同一侧的好几件肩甲（例如皮革左肩甲
 * 与鳞片左肩甲）时，只取其中减得最多的那份点数，而不是把它们加起来——否则两件一叠就能把
 * 轻击整个抹掉，肩甲会从"一件护具"变成"一堵墙"。左右两侧各算各的：护左肩的那件不影响右肩。</p>
 *
 * <p>注意这与<b>盔甲韧性</b>不是一回事：韧性按属性加成的既有规矩「各缝一件各算一份」累加
 * （见 {@code CarriedRelicEffect}），而这里只管挨打时减掉的那几点。两者各走各的路。</p>
 */
public final class ShoulderGuardEffect {

    private ShoulderGuardEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，挂上「打中哪儿」的订阅。
     */
    public static void register() {
        BodyPartHits.register(ShoulderGuardEffect::rememberHit);
    }

    /**
     * 记下这一击打在哪个部位。
     *
     * <p>订阅本身不判断有没有肩甲、也不改伤害——它只在每次命中时留一张便条，
     * 真正要不要减、减多少，留给伤害结算那一步去问（见 {@link #reductionFor}）。
     * 这样便条不会在「身上根本没有肩甲」的玩家身上白留一个游戏刻。</p>
     *
     * @param hit 一次命中
     */
    private static void rememberHit(BodyPartHit hit) {
        RecentBodyPartHit.remember(hit.player(), hit.part());
    }

    /**
     * 算出这一击应当少掉多少伤害，并把便条取走。
     *
     * <p><b>每一次伤害结算都必须调用它</b>，哪怕玩家身上一件肩甲都没有：
     * 便条正是在这里被取走并清掉的，跳过它就等于把便条留在了桌上。</p>
     *
     * <p>打中的部位由便条给出；便条过期（那一击并没有真的落下）时返回 0，
     * 于是这一击按原样结算。这一条路只管「少了多少」，扣到 0 为止由调用方保证。</p>
     *
     * @param player 挨打的玩家
     * @return 这一击应当少掉的伤害点数；没有肩甲护着、或便条已过期时返回 0
     */
    public static float reductionFor(PlayerEntity player) {
        BodyPart part = RecentBodyPartHit.consume(player);
        if (part == null) {
            return 0.0F;
        }

        ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
        if (chest.isEmpty()) {
            return 0.0F;
        }

        float best = 0.0F;

        for (Item attached : RelicAttachment.attachedTo(chest)) {
            ShoulderGuard guard = ShoulderGuards.guardOf(attached);
            if (guard != null && guard.guards(part)) {
                best = Math.max(best, guard.reduction());
            }
        }

        return best;
    }

    /**
     * 这一箭是否会<b>被肩甲弹开</b>。
     *
     * <p>只有远程打中肩甲护着的那一侧时才有机会；概率由 {@link ShoulderGuards 肩甲表} 登记
     * （目前只有龟壳肩甲会弹，10%）。护住同一侧的多件肩甲之间<b>取概率最高的那件</b>，
     * 与减伤取最高的规矩一致——不叠加，免得几件一叠就变成必弹。</p>
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
            ShoulderGuard guard = ShoulderGuards.guardOf(attached);
            if (guard != null && guard.canDeflect() && guard.guards(part)) {
                bestChance = Math.max(bestChance, guard.deflectChance());
            }
        }

        return bestChance > 0.0F && player.getRandom().nextFloat() < bestChance;
    }
}
