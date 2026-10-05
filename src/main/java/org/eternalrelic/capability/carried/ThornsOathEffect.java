package org.eternalrelic.capability.carried;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.network.ServerPlayerEntity;

import org.eternalrelic.registry.ModItems;
import org.eternalrelic.relic.AttackDamage;
import org.twcore.api.event.PlayerDamageEvent;
import org.twcore.api.event.TwEventPhases;

/**
 * 「挨打反伤」能力：带着荆棘之誓挨打时，把<b>玩家实际掉的那部分血</b>的五分之一扎回给打你的人
 * （单次最多 20 点）。
 *
 * <p>与另外几类挨打能力的分工：{@code DamageWardEffect}（回响之环）把这一击<b>挡下来</b>，
 * {@code EnchantedRabbitFootEffect}（附魔兔脚）不改伤害、只换一段速度，而这里<b>什么都不替玩家挡</b>——
 * 伤害照常挨，只是让动手的人也要付一点代价。</p>
 *
 * <h2>为什么算的是「最终值」而不是「打过来的原始伤害」</h2>
 * <p>这一条是制作者指定的口径：<b>玩家实际掉了多少血，就按那个数的五分之一扎回去</b>。
 * 于是穿着好甲的人反得少、裸着挨打的人反得多，与「你实际被打掉多少，对方就挨多少的零头」
 * 这句话对得上。因此本能力接在伤害结算事件上——那个时点已经算完了护甲、保护附魔与胸甲护具，
 * 减掉金心之后就是游戏接下去真正会从血条上扣掉的那个数。挨打事件里的数字则是这些全都没算的
 * 原始伤害，取不到最终值，所以本能力不挂那一条。</p>
 *
 * <p><b>被金心完全挡下的那一下不反</b>：玩家一滴血都没掉，也就没有「承受」可言
 * （参数为 0 时直接返回）。</p>
 *
 * <p><b>反伤打回去的那一下不会再被反弹</b>：两个人各带一枚、互相砍，如果反伤也能触发反伤，
 * 就会一直弹到其中一方死亡。做法是让反伤走原版那条「荆棘」伤害类型
 * （{@code DamageTypes.THORNS}，原版荆棘附魔用的就是它），本能力见到它就放行——
 * 语义相同，且天然与它互斥。副作用是：<b>被原版荆棘附魔扎回来的那一下，本遗物也不会再反</b>，
 * 这正是想要的结果（否则剑上的荆棘会与遗物串成一条链）。</p>
 *
 * <p>只认「有人把他打了」这一件事，判定复用 {@link AttackDamage#isAttack}，与回响之环、附魔兔脚
 * 完全同一套口径：中毒、岩浆、摔落、虚空都不反弹；无主的爆炸（红石引燃的 TNT）算攻击，
 * 但它没有攻击者，因此照样放过。玩家自己造成的伤害（例如自己点燃的 TNT）也不反弹。</p>
 */
public final class ThornsOathEffect {

    /** 反弹比例：玩家实际承受伤害的五分之一。 */
    private static final float REFLECT_RATIO = 0.2F;

    /** 单次反伤的上限：20 点（十颗心）。 */
    private static final float MAX_REFLECTED = 20.0F;

    private ThornsOathEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，接上伤害结算事件。
     *
     * <p>排在默认阶段之后：它要的是胸甲护具减完之后的数值。</p>
     */
    public static void register() {
        PlayerDamageEvent.PLAYER_DAMAGE.register(TwEventPhases.LOW, ThornsOathEffect::onDamage);
    }

    /**
     * 在伤害结算处把这一击的一部分扎回去。
     *
     * <p>本能力从不替玩家挡伤害，因此数值原样放行。</p>
     *
     * @param context       本次结算的只读信息
     * @param currentDamage 护甲、附魔、状态效果与胸甲护具都算完之后、金心抵扣之前的伤害值
     * @return 放行结果
     */
    private static PlayerDamageEvent.Result onDamage(PlayerDamageEvent.Context context, float currentDamage) {
        // 金心（吸收）抵扣发生在更后面，这里先减掉它，得到的就是游戏真正会从血条上扣掉的数
        reflect(context.player(), context.source(),
                currentDamage - context.player().getAbsorptionAmount());

        return PlayerDamageEvent.Result.keep();
    }

    /**
     * 把这一击的一部分扎回给动手的人。
     *
     * @param player      挨打、且带着荆棘之誓的玩家
     * @param source      这一击的伤害来源
     * @param finalDamage 玩家实际承受的伤害 —— 护甲、保护附魔、胸甲护具与金心抵扣都算完之后，
     *                    游戏真正会从他血条上扣掉的那个数
     */
    public static void reflect(ServerPlayerEntity player, DamageSource source, float finalDamage) {
        // 一滴血都没掉（被金心整个挡下）：没有「承受」可言，也就没什么可扎回去的
        if (finalDamage <= 0.0F) {
            return;
        }

        // 反伤自己扎出去的那一下不再被反（见类文档：否则会无限互弹）
        if (source.isOf(DamageTypes.THORNS)) {
            return;
        }

        if (!AttackDamage.isAttack(source)) {
            return;
        }

        // 没有攻击者（无主爆炸、落石）或动手的就是自己时，没有可扎回去的对象
        if (!(source.getAttacker() instanceof LivingEntity attacker) || attacker == player) {
            return;
        }

        if (!CarriedStacks.inEffect(player, ModItems.THORNS_OATH)) {
            return;
        }

        float reflected = Math.min(finalDamage * REFLECT_RATIO, MAX_REFLECTED);
        if (reflected <= 0.0F) {
            return;
        }

        // 用玩家当攻击者：对方照常被击退、也照常算作「被玩家打了」，与原版荆棘附魔的口径一致
        attacker.damage(player.getServerWorld().getDamageSources().thorns(player), reflected);
    }
}
