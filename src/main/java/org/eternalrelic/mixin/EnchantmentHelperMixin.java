package org.eternalrelic.mixin;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtList;

import org.eternalrelic.relic.RelicEnchantmentBonus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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
 * <p>两处调用点必须<b>都</b>接上（{@code require = 2}）：少接一处不会报错，只会让某一片功能悄悄
 * 失效（例如面板写着经验修补、捡经验却不修），那是最难查的一类问题。</p>
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {

    /**
     * 把取出的附魔列表换成「物品自己的 + 遗物补上的」。
     *
     * <p>只覆盖算效果的两条路：单条查询与全表遍历。整表导出（{@code get}）不在其中，原因见类注释。</p>
     *
     * @param stack 正在被清点附魔的物品
     * @return 合并后的附魔列表
     */
    @Redirect(
            method = {
                    "getLevel(Lnet/minecraft/enchantment/Enchantment;Lnet/minecraft/item/ItemStack;)I",
                    "forEachEnchantment(Lnet/minecraft/enchantment/EnchantmentHelper$Consumer;Lnet/minecraft/item/ItemStack;)V"
            },
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;getEnchantments()Lnet/minecraft/nbt/NbtList;"),
            require = 2)
    private static NbtList eternal_relic$mergeRelicEnchantments(ItemStack stack) {
        return RelicEnchantmentBonus.merged(stack);
    }
}
