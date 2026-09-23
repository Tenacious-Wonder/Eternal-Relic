package org.eternalrelic.capability.carried;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import org.eternalrelic.registry.ModItems;

/**
 * 「被蜜蜂蜇了不中毒」能力：带着蜂蜡吊坠时，蜜蜂那一下照常挨，只是蜇不进毒。
 *
 * <p><b>它与既有三类能力都不同</b>：{@link CarriedRelicEffect} 管的是一直升效的数值，
 * {@link org.eternalrelic.capability.carried.DamageWardEffect} 管的是把这一击挡下来，
 * {@link EnchantedRabbitFootEffect} 管的是挨打之后换来点什么。这里<b>伤害一点不减</b>，
 * 被拿掉的只有蜜蜂顺手挂上去的那一条中毒。</p>
 *
 * <p><b>为什么必须动游戏内部</b>：蜜蜂蜇人是分两步走的——先结算伤害，再单独把中毒挂到目标身上，
 * 这两步之间没有任何一步经过本模组能监听的事件。因此只能挂在「状态效果即将生效」这一步上
 * 把它拦下来，见 {@link org.eternalrelic.mixin.LivingEntityMixin}。</p>
 *
 * <p><b>只认蜜蜂</b>：毒箭、毒土豆、洞穴蜘蛛与药水给的中毒照旧生效——吊坠挡的是
 * 「被蜜蜂蜇了一口」这件事，而不是「一切中毒」。判断依据是那条效果的<b>来源实体</b>，
 * 蜜蜂挂毒时会把自己作为来源传进来。</p>
 */
public final class BeeswaxPendantEffect {

    private BeeswaxPendantEffect() {
    }

    /**
     * 判断一条即将生效的状态效果要不要被吊坠拦下。
     *
     * <p>三条都要满足才拦：挨的这一下是玩家、效果是中毒、下毒的是蜜蜂。任意一条不满足就放行，
     * 游戏原本的结算一步不动。</p>
     *
     * @param target 即将获得状态效果的实体
     * @param effect 即将生效的状态效果
     * @param source 施加这条效果的来源实体，可能为 {@code null}
     * @return 是否应当拦下（拦下即表示这条效果不会生效）
     */
    public static boolean blocksBeeStingPoison(LivingEntity target, StatusEffectInstance effect, Entity source) {
        if (!(target instanceof ServerPlayerEntity player)) {
            return false;
        }

        if (effect.getEffectType() != StatusEffects.POISON) {
            return false;
        }

        if (!(source instanceof BeeEntity)) {
            return false;
        }

        return CarriedStacks.inEffect(player, ModItems.BEESWAX_PENDANT);
    }
}
