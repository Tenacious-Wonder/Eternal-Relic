package org.eternalrelic.recipe;

import com.google.gson.JsonObject;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import org.eternalrelic.registry.ModItems;
import org.eternalrelic.registry.ModRecipes;

/**
 * 「用附魔之瓶修余烬吊坠」的工作台配方：烧过一阵的吊坠 + 一个附魔之瓶 → 补回三分之一耐久。
 *
 * <p><b>为什么不能用普通的合成配方</b>：普通配方（数据包里那种）只会吐出一件写死的产物，
 * 而这里要保住的恰恰是「这块吊坠自己还剩几次」——修完的耐久得由放进去的那一块算出来。
 * 原版把这类"配方表表达不了的合成"交给 {@link SpecialCraftingRecipe}（它自己的
 * 「两个相同工具合成修复」走的就是这条路），本类照同一路子写。</p>
 *
 * <p><b>两种输入都收</b>：正常但用掉了几次的吊坠，以及彻底烧空的「黯淡」形态。
 * 黯淡形态按「耐久刚好见底」算，因此修一次正好把它救回七次可用——与它烧空前的算法一致，
 * 玩家不会觉得换了个东西就换了规矩。</p>
 *
 * <p><b>满耐久的吊坠不匹配</b>：免得玩家白白赔进去一个附魔之瓶。</p>
 */
public class EmberPendantRepairRecipe extends SpecialCraftingRecipe {

    /** 一次修回多少 —— 制作者定的是三分之一。21 的三分之一正好是 7 点。 */
    private static final double REPAIR_RATIO = 0.33D;

    public EmberPendantRepairRecipe(Identifier id, CraftingRecipeCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(RecipeInputInventory inventory, World world) {
        ItemStack pendant = null;
        boolean bottle = false;
        int filled = 0;

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) {
                continue;
            }

            filled++;

            if (stack.isOf(Items.EXPERIENCE_BOTTLE)) {
                bottle = true;
                continue;
            }

            if (!isDamagedPendant(stack)) {
                // 混进了别的东西就不成 —— 否则一格吊坠加一格瓶子再加任意杂物都能合成
                return false;
            }

            pendant = stack;
        }

        return filled == 2 && bottle && pendant != null;
    }

    @Override
    public ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registryManager) {
        ItemStack pendant = findPendant(inventory);
        if (pendant == null) {
            return ItemStack.EMPTY;
        }

        int maxDamage = ModItems.EMBER_PENDANT.getMaxDamage();

        // 「黯淡」＝已经烧空，等价于耐久刚好见底；正常形态则照它自己剩的算
        int damage = pendant.isOf(ModItems.EMBER_PENDANT_DULL) ? maxDamage : pendant.getDamage();

        int repair = Math.max(1, (int) Math.round(maxDamage * REPAIR_RATIO));

        ItemStack result = new ItemStack(ModItems.EMBER_PENDANT);
        result.setDamage(Math.max(0, damage - repair));
        return result;
    }

    @Override
    public boolean fits(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.EMBER_PENDANT_REPAIR;
    }

    /**
     * 找出格子里那一块可以修的吊坠。
     *
     * @param inventory 合成格
     * @return 可以修的那一块；没有时返回 {@code null}
     */
    private static ItemStack findPendant(RecipeInputInventory inventory) {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (isDamagedPendant(stack)) {
                return stack;
            }
        }

        return null;
    }

    /**
     * 这一格是不是「可以修的吊坠」—— 烧空的黯淡形态，或者用掉过几次的正常形态。
     *
     * @param stack 待判断的一格
     * @return 可以修时返回 {@code true}
     */
    private static boolean isDamagedPendant(ItemStack stack) {
        if (stack.isOf(ModItems.EMBER_PENDANT_DULL)) {
            return true;
        }

        return stack.isOf(ModItems.EMBER_PENDANT) && stack.getDamage() > 0;
    }

    /**
     * 本配方的读写器。
     *
     * <p>配方本身<b>没有任何参数</b>——收什么、出什么全部写在本类里，因此数据包里那份配方文件
     * 只要写个类型就够了，同步到客户端时也不必写额外内容（配方编号由同步框架自己带着）。</p>
     */
    public static class Serializer implements RecipeSerializer<EmberPendantRepairRecipe> {

        @Override
        public EmberPendantRepairRecipe read(Identifier id, JsonObject json) {
            return new EmberPendantRepairRecipe(id, CraftingRecipeCategory.MISC);
        }

        @Override
        public EmberPendantRepairRecipe read(Identifier id, PacketByteBuf buffer) {
            return new EmberPendantRepairRecipe(id, CraftingRecipeCategory.MISC);
        }

        @Override
        public void write(PacketByteBuf buffer, EmberPendantRepairRecipe recipe) {
            // 没有参数要写
        }
    }
}
