package org.eternalrelic.registry;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import org.eternalrelic.EternalRelic;
import org.eternalrelic.item.AotaBranchItem;

/**
 * 本模组的物品注册入口。
 *
 * <p>每件物品以「静态常量 + 私有 register 方法」的形式声明：静态字段初始化时即完成注册，
 * 因此 {@link #register()} 只需被调用一次来触发类加载。</p>
 *
 * <p>遗物类物品还要在 {@link ModRelics} 里登记身份与携带效果，登记内容与这里的字段一一对应。</p>
 */
public final class ModItems {

    /** 本模组在创造模式物品栏中的专属分类的注册名。 */
    private static final RegistryKey<ItemGroup> RELIC_GROUP_KEY =
            RegistryKey.of(RegistryKeys.ITEM_GROUP, new Identifier(EternalRelic.MOD_ID, "relic_group"));

    /**
     * 奥塔的枝叶 —— 携带在背包中时提升生命上限的遗物。
     */
    public static final Item AOTA_BRANCH = register("aota_branch",
            new AotaBranchItem(new Item.Settings().maxCount(1)));

    private ModItems() {
    }

    /**
     * 由 {@link EternalRelic#onInitialize()} 调用，触发本类静态字段初始化并完成物品注册。
     */
    public static void register() {
        ModRelics.register();
        VanillaMaterialRarities.register();
        VanillaItemGrades.register();

        Registry.register(Registries.ITEM_GROUP, RELIC_GROUP_KEY, FabricItemGroup.builder()
                .icon(() -> new ItemStack(AOTA_BRANCH))
                .displayName(Text.translatable("itemGroup.eternal_relic.relic_group"))
                .build());

        ItemGroupEvents.modifyEntriesEvent(RELIC_GROUP_KEY).register(entries -> entries.add(AOTA_BRANCH));
    }

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(EternalRelic.MOD_ID, name), item);
    }
}
