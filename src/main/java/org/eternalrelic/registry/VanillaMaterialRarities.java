package org.eternalrelic.registry;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

import org.eternalrelic.relic.MaterialRarity;

/**
 * 原版材料的分档表 —— 把《我的世界》原有的材料按 {@link MaterialRarity} 定级。
 *
 * <p>分档以「<b>下界之星 = 精萃</b>」为锚点校准。这张表只收录原版物品，
 * 其他模组的材料由各自模组决定，本模组不去替它们定级。</p>
 *
 * <p>原版材料没有「等级」这一概念，因此这份对照表是人为判定的结果，依据是获取难度与实际用途：
 * 随手可得的归入碎屑，需要专门采集或加工才成形的归入粗石与成材，
 * 需要深入危险区域、稀有矿脉或击败强敌才得到的归入精萃及以上。</p>
 */
public final class VanillaMaterialRarities {

    /** 物品 → 材料档位。 */
    private static final Map<Item, MaterialRarity> BY_ITEM = new HashMap<>();

    private VanillaMaterialRarities() {
    }

    /**
     * 由 {@link ModItems#register()} 调用，触发本类静态内容初始化。
     */
    static void register() {
    }

    /**
     * @param item 待查询的物品
     * @return 该原版材料对应的档位；未收录（或不是原版材料）时返回 {@code null}
     */
    public static MaterialRarity rarityOf(Item item) {
        return BY_ITEM.get(item);
    }

    /**
     * @param item   物品
     * @param rarity 档位
     */
    private static void put(Item item, MaterialRarity rarity) {
        BY_ITEM.put(item, rarity);
    }

    /**
     * 把一组同类材料登记为同一档位。
     *
     * @param rarity 档位
     * @param items  同档位的物品
     */
    private static void putSet(MaterialRarity rarity, Item... items) {
        for (Item item : items) {
            put(item, rarity);
        }
    }

    static {
        // ==================== 1 碎屑：残渣、边角料，遍地都是 ====================
        put(Items.DIRT, MaterialRarity.DEBRIS);
        put(Items.COARSE_DIRT, MaterialRarity.DEBRIS);
        put(Items.ROOTED_DIRT, MaterialRarity.DEBRIS);
        put(Items.SAND, MaterialRarity.DEBRIS);
        put(Items.RED_SAND, MaterialRarity.DEBRIS);
        put(Items.GRAVEL, MaterialRarity.DEBRIS);
        put(Items.CLAY_BALL, MaterialRarity.DEBRIS);
        put(Items.FLINT, MaterialRarity.DEBRIS);
        put(Items.SNOWBALL, MaterialRarity.DEBRIS);
        put(Items.STICK, MaterialRarity.DEBRIS);
        put(Items.LEATHER, MaterialRarity.DEBRIS);
        put(Items.STRING, MaterialRarity.DEBRIS);
        put(Items.FEATHER, MaterialRarity.DEBRIS);
        put(Items.BONE, MaterialRarity.DEBRIS);
        put(Items.BONE_MEAL, MaterialRarity.DEBRIS);
        put(Items.ROTTEN_FLESH, MaterialRarity.DEBRIS);
        put(Items.GUNPOWDER, MaterialRarity.DEBRIS);
        put(Items.WHEAT_SEEDS, MaterialRarity.DEBRIS);
        put(Items.SUGAR_CANE, MaterialRarity.DEBRIS);
        put(Items.KELP, MaterialRarity.DEBRIS);
        put(Items.RABBIT_HIDE, MaterialRarity.DEBRIS);
        put(Items.INK_SAC, MaterialRarity.DEBRIS);
        put(Items.PUFFERFISH, MaterialRarity.DEBRIS);
        put(Items.SPIDER_EYE, MaterialRarity.DEBRIS);
        put(Items.GLISTERING_MELON_SLICE, MaterialRarity.LUMBER);
        put(Items.GOLDEN_CARROT, MaterialRarity.LUMBER);

        // 石料与木料：到处都能挖到、砍到
        put(Items.COBBLESTONE, MaterialRarity.DEBRIS);
        put(Items.COBBLED_DEEPSLATE, MaterialRarity.DEBRIS);
        put(Items.STONE, MaterialRarity.DEBRIS);
        put(Items.DEEPSLATE, MaterialRarity.DEBRIS);
        put(Items.ANDESITE, MaterialRarity.DEBRIS);
        put(Items.DIORITE, MaterialRarity.DEBRIS);
        put(Items.GRANITE, MaterialRarity.DEBRIS);
        put(Items.TUFF, MaterialRarity.DEBRIS);
        put(Items.CALCITE, MaterialRarity.DEBRIS);
        put(Items.BASALT, MaterialRarity.DEBRIS);
        put(Items.BLACKSTONE, MaterialRarity.DEBRIS);
        put(Items.NETHERRACK, MaterialRarity.DEBRIS);
        put(Items.NETHER_BRICK, MaterialRarity.DEBRIS);
        put(Items.PRISMARINE_SHARD, MaterialRarity.LUMBER);
        put(Items.PRISMARINE_CRYSTALS, MaterialRarity.LUMBER);
        put(Items.OAK_LOG, MaterialRarity.DEBRIS);
        put(Items.SPRUCE_LOG, MaterialRarity.DEBRIS);
        put(Items.BIRCH_LOG, MaterialRarity.DEBRIS);
        put(Items.JUNGLE_LOG, MaterialRarity.DEBRIS);
        put(Items.ACACIA_LOG, MaterialRarity.DEBRIS);
        put(Items.DARK_OAK_LOG, MaterialRarity.DEBRIS);
        put(Items.MANGROVE_LOG, MaterialRarity.DEBRIS);
        put(Items.CHERRY_LOG, MaterialRarity.DEBRIS);
        put(Items.CRIMSON_STEM, MaterialRarity.DEBRIS);
        put(Items.WARPED_STEM, MaterialRarity.DEBRIS);
        put(Items.BAMBOO, MaterialRarity.DEBRIS);
        put(Items.COAL, MaterialRarity.DEBRIS);
        put(Items.CHARCOAL, MaterialRarity.DEBRIS);
        put(Items.BRICK, MaterialRarity.DEBRIS);

        // ==================== 2 粗石：未加工的原始形态，稍有价值 ====================
        put(Items.END_STONE, MaterialRarity.ROUGH_STONE);
        put(Items.OBSIDIAN, MaterialRarity.ROUGH_STONE);
        put(Items.NETHER_WART, MaterialRarity.ROUGH_STONE);
        put(Items.GLOW_INK_SAC, MaterialRarity.ROUGH_STONE);
        put(Items.GLOWSTONE_DUST, MaterialRarity.ROUGH_STONE);
        put(Items.SLIME_BALL, MaterialRarity.ROUGH_STONE);
        put(Items.IRON_INGOT, MaterialRarity.ROUGH_STONE);
        put(Items.RAW_IRON, MaterialRarity.ROUGH_STONE);
        put(Items.COPPER_INGOT, MaterialRarity.ROUGH_STONE);
        put(Items.RAW_COPPER, MaterialRarity.ROUGH_STONE);
        put(Items.IRON_NUGGET, MaterialRarity.ROUGH_STONE);
        put(Items.GOLD_NUGGET, MaterialRarity.ROUGH_STONE);
        put(Items.GLASS, MaterialRarity.ROUGH_STONE);
        put(Items.PAPER, MaterialRarity.ROUGH_STONE);
        put(Items.BOOK, MaterialRarity.ROUGH_STONE);
        put(Items.HONEYCOMB, MaterialRarity.ROUGH_STONE);

        // ==================== 3 成材：已可正经使用的完整材料 ====================
        put(Items.DIAMOND, MaterialRarity.LUMBER);
        put(Items.EMERALD, MaterialRarity.LUMBER);
        put(Items.RAW_GOLD, MaterialRarity.LUMBER);
        put(Items.GOLD_INGOT, MaterialRarity.LUMBER);
        put(Items.ENDER_EYE, MaterialRarity.LUMBER);
        put(Items.DISC_FRAGMENT_5, MaterialRarity.LUMBER);
        put(Items.REDSTONE, MaterialRarity.LUMBER);
        put(Items.LAPIS_LAZULI, MaterialRarity.LUMBER);
        put(Items.QUARTZ, MaterialRarity.LUMBER);
        put(Items.AMETHYST_SHARD, MaterialRarity.LUMBER);
        put(Items.NAUTILUS_SHELL, MaterialRarity.LUMBER);
        put(Items.PHANTOM_MEMBRANE, MaterialRarity.LUMBER);
        put(Items.BLAZE_ROD, MaterialRarity.LUMBER);
        put(Items.BLAZE_POWDER, MaterialRarity.LUMBER);
        put(Items.MAGMA_CREAM, MaterialRarity.LUMBER);
        put(Items.GHAST_TEAR, MaterialRarity.LUMBER);
        put(Items.ENDER_PEARL, MaterialRarity.LUMBER);
        put(Items.SHULKER_SHELL, MaterialRarity.LUMBER);
        put(Items.EXPERIENCE_BOTTLE, MaterialRarity.LUMBER);

        // ==================== 4 精萃：提纯、浓缩后的精华部分 ====================
        // 锚点：下界之星即此档
        put(Items.NETHER_STAR, MaterialRarity.ESSENCE);
        put(Items.ECHO_SHARD, MaterialRarity.ESSENCE);
        put(Items.NETHERITE_INGOT, MaterialRarity.ESSENCE);
        put(Items.NETHERITE_SCRAP, MaterialRarity.ESSENCE);
        put(Items.ANCIENT_DEBRIS, MaterialRarity.ESSENCE);
        put(Items.HEART_OF_THE_SEA, MaterialRarity.ESSENCE);
        put(Items.TOTEM_OF_UNDYING, MaterialRarity.ESSENCE);

        // ==================== 5 珍品：稀少、受追捧的贵重之物 ====================
        put(Items.DRAGON_BREATH, MaterialRarity.TREASURE);
        // ==================== 锻造模板 ====================
        // 下界合金升级模板：通往最高阶材料的关键
        put(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, MaterialRarity.ESSENCE);
        // 盔甲纹饰模板：装饰用途
        putSet(MaterialRarity.LUMBER,
                Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE,
                Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE,
                Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE,
                Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE,
                Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE,
                Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE,
                Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE,
                Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE,
                Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE,
                Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE,
                Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE,
                Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE,
                Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE,
                Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE,
                Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE,
                Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE);
    }
}
