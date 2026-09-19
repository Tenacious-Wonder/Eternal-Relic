package org.eternalrelic.registry;

import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import org.eternalrelic.EternalRelic;
import org.eternalrelic.recipe.RelicAttachRecipe;

/**
 * 本模组的配方注册入口。
 *
 * <p>目前只有一条「把遗物附到装备上」的锻造台配方。它没有自己的配方类型——
 * 走的是原版的 {@code smithing} 类型，因此原版锻造台界面会自动认它、自动制作，
 * 不需要改动任何原版逻辑。</p>
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

    private ModRecipes() {
    }

    /**
     * 由 {@link EternalRelic#onInitialize()} 调用，触发本类静态字段初始化并完成配方注册。
     */
    public static void register() {
    }
}
