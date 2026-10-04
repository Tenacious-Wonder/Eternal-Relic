package org.eternalrelic.capability.attached;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import org.eternalrelic.registry.ModItems;
import org.eternalrelic.relic.RelicAttachment;

/**
 * 「弓油」能力：弓弩上涂了古旧弓油时，它射出去的箭更有杀伤。
 *
 * <p><b>为什么这件必须动游戏内部代码，而剑带不用</b>：近战伤害最终读的是玩家自己的
 * 「攻击力」属性，往属性上加半点就够了；而<b>箭的伤害与玩家的攻击力毫无关系</b>——
 * 它由弹射物自己按飞行速度算出来，属性系统碰不到它。因此只能在「箭打中人、伤害即将落下」
 * 那一步把它加上去（见 {@code mixin/PersistentProjectileEntityMixin}）。</p>
 *
 * <p><b>为什么看射手的<b>手</b>而不是看箭</b>：箭本身不记得自己是从哪张弓射出来的
 * （原版的箭只认"主人"是谁），所以这里问的是射手此刻手上拿着的那两件东西——
 * 弓弩就那两处，够用。射出去之后换手、把弓收起来，这一箭的加成就没了，这与"涂在弓上"的说法一致。</p>
 */
public final class BowOilEffect {

    /** 涂了油的弓弩射出的箭，伤害多出这么多。 */
    private static final float BONUS_DAMAGE = 0.5F;

    private BowOilEffect() {
    }

    /**
     * 这一箭的射手此刻是不是"拿着涂了油的弓弩"。
     *
     * @param owner 弹射物的主人；箭没有主人（例如发射器射出的）时为 {@code null}
     * @return 是否该给这一箭加伤
     */
    public static boolean appliesTo(Entity owner) {
        if (!(owner instanceof PlayerEntity player)) {
            return false;
        }

        return hasOil(player.getMainHandStack()) || hasOil(player.getOffHandStack());
    }

    /**
     * @return 涂了油的弓弩射出的箭，伤害要多出多少
     */
    public static float bonusDamage() {
        return BONUS_DAMAGE;
    }

    /**
     * @param stack 射手手上的那一件
     * @return 上面是不是附着了古旧弓油
     */
    private static boolean hasOil(ItemStack stack) {
        return !stack.isEmpty() && RelicAttachment.isAttached(stack, ModItems.OLD_BOW_OIL);
    }
}
