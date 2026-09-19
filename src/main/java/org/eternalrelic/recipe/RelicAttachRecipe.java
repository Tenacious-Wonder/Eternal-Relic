package org.eternalrelic.recipe;

import com.google.gson.JsonObject;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SmithingRecipe;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import org.eternalrelic.registry.ModRecipes;
import org.eternalrelic.relic.RelicAttachment;
import org.eternalrelic.relic.RelicAttachRule;

/**
 * 「把遗物附到装备上」的锻造台配方。
 *
 * <p>走锻造台而不是铁砧，是因为原版锻造台**按配方办事**：只要把配方登记成
 * {@link net.minecraft.recipe.RecipeType#SMITHING}，原版界面就会自动认它、自动制作，
 * 一行 mixin 都不需要。三格的用法是：</p>
 *
 * <ul>
 *   <li>第 1 格 —— **辅料**（逐件遗物指定，例如蜡制的纹章要蜜脾）；</li>
 *   <li>第 2 格 —— **要被附着的装备 / 武器 / 工具**；</li>
 *   <li>第 3 格 —— **那件遗物**。</li>
 * </ul>
 *
 * <p>输出是「原装备的一份副本，只是数据里多记了一枚遗物」——装备原有的附魔、耐久、
 * 自定义名字一概保留，本配方只往上加东西，不改动其它任何内容。</p>
 */
public class RelicAttachRecipe implements SmithingRecipe {

    private final Identifier id;

    public RelicAttachRecipe(Identifier id) {
        this.id = id;
    }

    @Override
    public boolean matches(Inventory inventory, World world) {
        return RelicAttachRule.matches(
                inventory.getStack(0), inventory.getStack(1), inventory.getStack(2));
    }

    @Override
    public ItemStack craft(Inventory inventory, DynamicRegistryManager registryManager) {
        // 判定与成品都由 RelicAttachRule 给出 —— 与遗物装卸台那条路共用同一套裁决，
        // 两条入口不会出现"锻造台能做、装卸台说不能"的分叉
        return RelicAttachRule.previewOf(
                inventory.getStack(0), inventory.getStack(1), inventory.getStack(2));
    }

    @Override
    public ItemStack getOutput(DynamicRegistryManager registryManager) {
        // 输出取决于放进去的是哪件装备，没有固定结果。原版界面算预览走的是 craft()，
        // 不走这里；配方书与进度也用不到这个动态配方。
        return ItemStack.EMPTY;
    }

    @Override
    public boolean testTemplate(ItemStack stack) {
        // 第 1 格：任何一件被某件遗物指定为辅料的东西都能放
        return RelicAttachRule.isMaterialItem(stack.getItem());
    }

    @Override
    public boolean testBase(ItemStack stack) {
        // 第 2 格：只要算得上装备 / 武器 / 工具之一就能放
        return RelicAttachRule.isAttachableTarget(stack.getItem());
    }

    @Override
    public boolean testAddition(ItemStack stack) {
        // 第 3 格：只收登记过的可附遗物
        return RelicAttachRule.isRelicItem(stack.getItem());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.RELIC_ATTACH;
    }

    @Override
    public Identifier getId() {
        return this.id;
    }

    @Override
    public boolean isEmpty() {
        // 本配方没有「原料」清单可以据以判断，永远不算空
        return false;
    }

    /**
     * 本配方的读写器。
     *
     * <p>配方本身**没有任何参数**——三格收什么、产出什么，全部由
     * {@link RelicAttachment} 的登记表决定。因此数据包里那份配方只要写个类型就够了，
     * 同步到客户端时也不必写额外内容（配方编号由同步框架自己带着）。</p>
     */
    public static class Serializer implements RecipeSerializer<RelicAttachRecipe> {

        @Override
        public RelicAttachRecipe read(Identifier id, JsonObject json) {
            return new RelicAttachRecipe(id);
        }

        @Override
        public RelicAttachRecipe read(Identifier id, PacketByteBuf buffer) {
            return new RelicAttachRecipe(id);
        }

        @Override
        public void write(PacketByteBuf buffer, RelicAttachRecipe recipe) {
            // 没有参数要写
        }
    }
}
