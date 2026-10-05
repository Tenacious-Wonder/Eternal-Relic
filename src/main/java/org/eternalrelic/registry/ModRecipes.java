package org.eternalrelic.registry;

import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import org.eternalrelic.EternalRelic;
import org.eternalrelic.recipe.EmberPendantRepairRecipe;
import org.eternalrelic.recipe.RelicAttachRecipe;

/**
 * 本模组的配方注册入口。
 *
 * <p>目前登记了两条配方，各自走原版的一条现成通路：一是「把遗物附到装备上」，
 * 走 {@code smithing} 类型，锻造台界面会自动认它；二是「用附魔之瓶修余烬吊坠」，
 * 走 {@code crafting} 类型（{@link EmberPendantRepairRecipe}）。
 * 两条都不需要改动任何原版逻辑。</p>
 */
public final class ModRecipes {

    /**
     * 「把遗物附到装备上」的锻造台配方。
     *
     * <p>数据包里那份配方文件只要写类型就够了（见 {@code data/eternal_relic/recipes/}），
     * 因为收什么、出什么全部由 {@link org.eternalrelic.relic.RelicAttachment} 的登记表决定。</p>
     */
    public static final RecipeSerializer<RelicAttachRecipe> RELIC_ATTACH = Registry.register(
            Registries.RECIPE_SERIALIZER,
            EternalRelic.id("relic_attach"),
            new RelicAttachRecipe.Serializer());

    /**
     * 「用附魔之瓶修余烬吊坠」的工作台配方。
     *
     * <p>它走原版的 {@code crafting} 类型，因此原版工作台会自动认它、自动制作；
     * 之所以要自己写一个类，是因为产出得由放进去的那块吊坠算出来（修三分之一耐久），
     * 而数据包里的普通配方只能吐出写死的产物（见 {@link EmberPendantRepairRecipe}）。</p>
     */
    public static final RecipeSerializer<EmberPendantRepairRecipe> EMBER_PENDANT_REPAIR = Registry.register(
            Registries.RECIPE_SERIALIZER,
            EternalRelic.id("ember_pendant_repair"),
            new EmberPendantRepairRecipe.Serializer());

    private ModRecipes() {
    }

    /**
     * 由 {@link EternalRelic#onInitialize()} 调用，触发本类静态字段初始化并完成配方注册。
     */
    public static void register() {
    }
}
