package org.eternalrelic.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.entity.LivingEntity;

import org.eternalrelic.capability.carried.SnowGripStudsEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 让带着雪地靴钉的玩家踩在冰面上不打滑。
 *
 * <p><b>为什么必须动游戏内部代码</b>：方块滑不滑是游戏在算移动时自己读的一个数，
 * 模组既没有可以覆写的方法，也没有「滑不滑」的事件——这个数只在这一处被读走，
 * 因此只能在那一次读取上包一层。</p>
 *
 * <p><b>为什么包在 {@code LivingEntity#travel} 里那一次读取上，而不是改方块本身</b>：
 * 方块的摩擦系数是<b>方块自己</b>的属性，与方法调用者无关。若直接去改 {@code Block#getSlipperiness}，
 * 就成了「这块冰对所有人都变涩了」——船在冰上滑行、别的生物走冰面也一并受影响，
 * 而那时根本无从判断踩在上面的是谁。包在生物读取的那一处，才拿得到「是谁在读」。</p>
 *
 * <p><b>只影响玩家，且只影响带着靴钉的那位</b>：判断在 {@link SnowGripStudsEffect#grips} 里，
 * 未携带者与所有生物都原样放行。原版这个方法里只有这一处读取，因此注入器自带的
 * 「恰好命中一处」正好替我们看着它——原版若日后另加一处同类读取，启动时会直接报错。</p>
 */
@Mixin(LivingEntity.class)
public abstract class SnowGripStudsMixin {

    /**
     * 在游戏读取脚下摩擦系数的那一刻把它调稳。
     *
     * @param original 脚下方块原本的摩擦系数（冰是 0.98，普通地面是 0.6）
     * @return 交给游戏使用的摩擦系数
     */
    @ModifyExpressionValue(
            method = "travel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getSlipperiness()F"))
    private float eternal_relic$snowGripStuds(float original) {
        LivingEntity self = (LivingEntity) (Object) this;

        return SnowGripStudsEffect.grips(self) ? SnowGripStudsEffect.firmer(original) : original;
    }
}
