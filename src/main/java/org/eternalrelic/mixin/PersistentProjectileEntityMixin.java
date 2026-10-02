package org.eternalrelic.mixin;

import net.minecraft.entity.projectile.PersistentProjectileEntity;

import org.eternalrelic.capability.attached.BowOilEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 让涂了古旧弓油的弓弩射出的箭更有杀伤。
 *
 * <p><b>为什么必须动游戏内部代码</b>：箭的伤害是弹射物自己按飞行速度算出来的一个整数，
 * 与射手的「攻击力」属性毫无关系（原版 {@code PersistentProjectileEntity#onEntityHit} 里
 * {@code ceil(速度 × 基础伤害)}），属性系统碰不到它，也没有任何模组事件能改这一击。</p>
 *
 * <p><b>改的是「交给对方结算的那一个数」</b>：原版算完之后会调
 * {@code entity.damage(source, 伤害)}，这里包住那一次调用的参数加上半点。
 * 它落在原版取整<b>之后</b>，因此那半点不会被 {@code ceil} 吃掉——
 * 这一点很关键：若改在取整之前，+0.5 会有大半时候等于没加。</p>
 *
 * <p>用「包一层」而不是顶掉：别的模组也想改箭伤时，两层会叠加而不是互相作废。
 * （这一处用的是原版 Mixin 自带的 {@code @ModifyArg}——它是"改一个参数"的温和式改写，
 * 与 MixinExtras 那几个注入器同属可以多家共存的那一档。）</p>
 */
@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectileEntityMixin {

    /**
     * 箭将要打中某个实体时，若射手手里拿着涂了油的弓弩，就把这一击的伤害加上去。
     *
     * @param amount 原版算好的这一箭伤害
     * @return 加过油之后的伤害
     */
    @ModifyArg(
            method = "onEntityHit",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"),
            index = 1)
    private float eternal_relic$bowOilBonusDamage(float amount) {
        PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;

        return BowOilEffect.appliesTo(self.getOwner()) ? amount + BowOilEffect.bonusDamage() : amount;
    }
}
