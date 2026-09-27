package org.eternalrelic.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import org.eternalrelic.capability.carried.CarriedStacks;
import org.eternalrelic.capability.worn.WornRelicEffect;
import org.eternalrelic.registry.ModItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 玩家身上的两处通知：吃下东西、以及获得经验。
 *
 * <p><b>吃金苹果为什么需要钩子</b>：游戏没有提供「玩家吃了什么」的事件，只会告诉你玩家在回血，
 * 因此吃金苹果这件事本身无从监听。这里挂在 {@code PlayerEntity#eatFood} 上——这是玩家真正
 * 吃下食物的那一步，金苹果与附魔金苹果都会经过它。</p>
 *
 * <p><b>获得经验为什么需要钩子</b>：经验是游戏自己算出来的一个数字，模组没有可以监听的事件。
 * 但所有把经验给到玩家的路径最后都汇到同一个方法 {@code PlayerEntity#addExperience} 上，
 * 在那里把数字改大一点，就等于「任何方式获得的经验都多一点」。</p>
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    /**
     * 玩家吃下食物时，若吃的是金苹果或附魔金苹果，就把已经装入的眼睛取下来。
     *
     * <p>只在服务端处理：客户端那份属性变化会被服务端覆盖，处理了也是白做，
     * 还可能让物品在客户端凭空多出来。</p>
     *
     * <p>注入点选在方法开头而不是结尾：食物在那里还没有被扣除数量，物品堆仍然认得出自己是金苹果
     * （若在结尾注入，物品数量归零后 {@code getItem()} 会变成空气）。</p>
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

    /**
     * 玩家获得经验时，带着学者单片眼镜就多给一点。
     *
     * <p><b>改的是传进来的那个数字，而不是另外再调一次 {@code addExperience} 补一点。</b>
     * 后者会让本注入自己再触发一次，一路递归下去直接崩掉；改参数则整条结算——经验条、
     * 总经验、记分——都按加过的那份走，与游戏原本的算法只差那个数字。</p>
     *
     * <p>只认服务端玩家：经验是服务端算完再同步给客户端的，在客户端做同一件事没有意义。</p>
     *
     * <p><b>只对正数生效</b>：这个方法也会被用来扣经验或加零（例如把经验设成某个值的命令），
     * 那不是「获得经验」，多给一点会把该扣的扣少、把零变成一。</p>
     *
     * @param experience 游戏原本要给的经验点数
     * @return 实际给的点数
     */
    @ModifyVariable(method = "addExperience(I)V", at = @At("HEAD"), argsOnly = true)
    private int eternal_relic$scholarMonocleBonus(int experience) {
        if (experience <= 0) {
            return experience;
        }

        PlayerEntity player = (PlayerEntity) (Object) this;
        if (player instanceof ServerPlayerEntity serverPlayer
                && CarriedStacks.inEffect(serverPlayer, ModItems.SCHOLAR_MONOCLE)) {
            return experience + 1;
        }

        return experience;
    }
}
