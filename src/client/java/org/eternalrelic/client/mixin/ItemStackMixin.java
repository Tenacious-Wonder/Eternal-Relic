package org.eternalrelic.client.mixin;

import java.util.List;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

import org.eternalrelic.client.RelicEnchantmentTooltip;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 在游戏写出附魔那几行的当口，把遗物补上的份额接在后面。
 *
 * <p><b>为什么挑这一处</b>：游戏写附魔行的那一句只拿到「一个列表 + 一份附魔表」，
 * 拿不到物品本身，也就问不出「这件东西上钉了什么」。所以这里守在它的调用点上——
 * 此刻 {@code this} 正是那件物品，先照原样让游戏把行写完，再回头给这几行补金色加号。</p>
 *
 * <p><b>用「包一层」而不是「顶掉」</b>：这里写的是 {@code @WrapOperation} —— 拿到那次调用的
 * 「代办」自己执行，前后随意加料，而不是把那次调用整个替换掉。原因是这一处
 * <b>别的模组也很可能动</b>（附魔说明、附魔信息增强那一类都爱改提示框里的附魔行），
 * 而「顶掉」是占位式的，两家都占就会在启动时报错崩掉；「包一层」则可以多家叠加。
 * 代价是两家的装饰会前后排在一起（而不是互相顶掉），对本类来说正是想要的。
 * 这条口径见设计决策 56。</p>
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
     * @param original     那次「写附魔行」的调用本身，由本方法负责执行
     */
    @WrapOperation(
            method = "getTooltip",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;appendEnchantments(Ljava/util/List;Lnet/minecraft/nbt/NbtList;)V"))
    private void eternal_relic$decorateEnchantmentLines(List<Text> tooltip, NbtList enchantments,
                                                        Operation<Void> original) {
        ItemStack self = (ItemStack) (Object) this;
        int firstEnchantLine = tooltip.size();

        original.call(tooltip, enchantments);
        RelicEnchantmentTooltip.decorate(self, tooltip, firstEnchantLine);
    }
}
