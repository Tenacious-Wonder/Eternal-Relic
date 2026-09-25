package org.eternalrelic.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtList;

import org.eternalrelic.relic.RelicEnchantmentBonus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 让游戏在<b>计算附魔效果</b>时，把「遗物补上的那一份」也算进去。
 *
 * <p><b>为什么只需要动两处</b>：游戏读取附魔的方式看着很散（{@code getLevel}、{@code getEfficiency}、
 * {@code getAttackDamage}、{@code hasSilkTouch}、{@code getProtectionAmount} 等等几十个方法），
 * 但它们最终只落在两个动作上——「查某一条附魔的等级」与「遍历这件物品的所有附魔」，
 * 后者又被几乎所有按名字查询的方法共用。接上这两处，挖掘速度、攻击伤害、耐久消耗、时运、
 * 精准采集、经验修补便一并生效，不必逐个去接。</p>
 *
 * <p><b>唯独「导出整张附魔表」那一处（{@code EnchantmentHelper#get}）故意不接</b> ——
 * 这是本类最容易改错的地方，读到这段注释请先别急着"补齐"：</p>
 *
 * <ul>
 *   <li>{@code get} 的用途不是算效果，而是<b>把一件物品的附魔整张搬走</b>：铁砧合并两件物品的附魔、
 *       砂轮把附魔磨出来、附魔台判断"这条附魔已经有了"。</li>
 *   <li>一旦让它看见虚拟附魔，玩家就能把它<b>变成真的</b>：拿去铁砧并到别的物品上，
 *       或者丢进砂轮——砂轮会把整张表读出来、再写进输出物，虚拟的经验修补就这样变成了
 *       一本真的附魔书，而且能反复磨。虚拟附魔本是「摘下即失效」的东西，被复制走规则就破了。</li>
 *   <li>反过来，这里漏接的后果只是「某处效果算少了」，而错接的后果是「凭空造出附魔」。
 *       两种错法轻重差得很远，所以宁可少接一处。</li>
 * </ul>
 *
 * <h2>接法：在那次读取外面包一层，而不是把它顶掉</h2>
 * <p>这里用的是「改返回值」（{@code @ModifyExpressionValue}），而不是「整个替换那次调用」
 * （{@code @Redirect}）。区别只在<b>别人也想改同一处时</b>：</p>
 * <ul>
 *   <li><b>整个替换</b>是占位式的 —— 同一个调用点只容得下一个，两个模组都占就会在启动时直接报错；</li>
 *   <li><b>改返回值</b>是包一层 —— 别的模组也能再包一层，两层都会生效。代价是两个模组都想改同一处时
 *       结果会<b>叠加</b>而不是互相顶掉，而本类做的本来就只是「追加几条附魔」，叠加正是想要的行为。</li>
 * </ul>
 * <p>本模组在别处也一样：<b>能不占位就不占位</b>。这条口径见设计决策 56。</p>
 *
 * <p><b>两处调用点必须都接上</b>：少接一处不会报错，只会让某一片功能悄悄失效
 * （例如面板写着经验修补、捡经验却不修），那是最难查的一类问题。因此下面两个方法各接一处，
 * 而注入器默认要求<b>恰好命中一处</b>，任何一处对不上都会在启动时暴露。</p>
 *
 * <p><b>为什么用 {@code @Local} 取那件物品，而不是像别处那样直接写在参数表里</b>：
 * 遍历附魔那个方法的第一个参数是一个<b>私有</b>的内部接口（{@code EnchantmentHelper.Consumer}），
 * 在方法签名里写它的类型根本编译不过。{@code @Local(argsOnly = true)} 是按<b>类型</b>
 * 从目标方法的参数里把物品取出来，因此完全不必提到那个私有类型。</p>
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {

    /**
     * 查单条附魔等级时，把游戏读出来的附魔表换成「它的 + 遗物补的」。
     *
     * @param original 游戏原本读出来的附魔表
     * @param stack    正在被清点附魔的物品（从目标方法的参数里取）
     * @return 合并后的附魔表
     */
    @ModifyExpressionValue(
            method = "getLevel(Lnet/minecraft/enchantment/Enchantment;Lnet/minecraft/item/ItemStack;)I",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;getEnchantments()Lnet/minecraft/nbt/NbtList;"))
    private static NbtList eternal_relic$mergeForLevel(NbtList original, @Local(argsOnly = true) ItemStack stack) {
        return RelicEnchantmentBonus.merged(stack, original);
    }

    /**
     * 遍历整件物品的附魔时，同样把遗物那一份并进去。
     *
     * <p>按名字查询的那几十个方法（挖掘速度、攻击伤害、耐久消耗、时运、精准采集……）都走这一条。</p>
     *
     * @param original 游戏原本读出来的附魔表
     * @param stack    正在被清点附魔的物品（从目标方法的参数里取）
     * @return 合并后的附魔表
     */
    @ModifyExpressionValue(
            method = "forEachEnchantment(Lnet/minecraft/enchantment/EnchantmentHelper$Consumer;Lnet/minecraft/item/ItemStack;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;getEnchantments()Lnet/minecraft/nbt/NbtList;"))
    private static NbtList eternal_relic$mergeForEach(NbtList original, @Local(argsOnly = true) ItemStack stack) {
        return RelicEnchantmentBonus.merged(stack, original);
    }
}
