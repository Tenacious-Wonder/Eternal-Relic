package org.eternalrelic.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;

import org.eternalrelic.capability.worn.WornRelicEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让游戏在玩家吃下东西时通知本模组。
 *
 * <p><b>为什么需要这个钩子</b>：游戏没有提供「玩家吃了什么」的事件，只会告诉你玩家在回血，
 * 因此吃金苹果这件事本身无从监听。这里挂在 {@code PlayerEntity#eatFood} 上——这是玩家真正
 * 吃下食物的那一步，金苹果与附魔金苹果都会经过它。</p>
 *
 * <p>注入点选在方法开头而不是结尾：食物在那里还没有被扣除数量，物品堆仍然认得出自己是金苹果
 * （若在结尾注入，物品数量归零后 {@code getItem()} 会变成空气）。</p>
 *
 * <p>这是本模组唯一一处改动游戏内部代码的地方，只读取「吃的是什么」，不改变游戏原有的行为。</p>
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    /**
     * 玩家吃下食物时，若吃的是金苹果或附魔金苹果，就把已经装入的眼睛取下来。
     *
     * <p>只在服务端处理：客户端那份属性变化会被服务端覆盖，处理了也是白做，
     * 还可能让物品在客户端凭空多出来。</p>
     *
     * @param world 玩家所在的世界
     * @param stack 正在吃下的食物
     * @param callbackInfo 原方法的返回值回调（本注入不改变它）
     */
    @Inject(method = "eatFood", at = @At("HEAD"))
    private void eternal_relic$releaseEyesOnGoldenApple(World world, ItemStack stack,
                                                        CallbackInfoReturnable<ItemStack> callbackInfo) {
        if (world.isClient) {
            return;
        }

        if (stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
            WornRelicEffect.releaseAll((PlayerEntity) (Object) this);
        }
    }
}
