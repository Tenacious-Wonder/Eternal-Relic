package org.eternalrelic.mixin.client;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

import org.eternalrelic.client.RelicEnchantmentTooltip;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 在游戏写出附魔那几行的当口，把遗物补上的份额接在后面。
 *
 * <p><b>为什么挑这一处</b>：游戏写附魔行的那一句只拿到「一个列表 + 一份附魔表」，
 * 拿不到物品本身，也就问不出「这件东西上钉了什么」。所以这里守在它的调用点上——
 * 此刻 {@code this} 正是那件物品，先照原样让游戏把行写完，再回头给这几行补金色加号。</p>
 *
 * <p>提示框只在客户端生成，所以本类只在客户端加载；服务端那边一行都不受影响。</p>
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    /**
     * 让游戏写完附魔行之后，由本模组补上遗物的份额。
     *
     * @param tooltip      提示框内容，就地改写
     * @param enchantments 游戏本要写出的附魔表（物品自己的那份）
     */
    @Redirect(
            method = "getTooltip",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;appendEnchantments(Ljava/util/List;Lnet/minecraft/nbt/NbtList;)V"))
    private void eternal_relic$decorateEnchantmentLines(List<Text> tooltip, NbtList enchantments) {
        ItemStack self = (ItemStack) (Object) this;
        int firstEnchantLine = tooltip.size();

        ItemStack.appendEnchantments(tooltip, enchantments);
        RelicEnchantmentTooltip.decorate(self, tooltip, firstEnchantLine);
    }
}
