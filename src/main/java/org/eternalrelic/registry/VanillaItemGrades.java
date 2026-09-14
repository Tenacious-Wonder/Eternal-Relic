package org.eternalrelic.registry;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

import org.eternalrelic.relic.EquipmentGrade;

/**
 * 原版工具装备的分档表 —— 把《我的世界》原有的工具与装备按 {@link EquipmentGrade} 定级。
 *
 * <p><b>定级依据</b>：一件装备的品阶由两个因素共同决定——</p>
 * <ol>
 *   <li><b>制作材料</b>：材料越精，基准品阶越高。木石之作只是粗胚，铁铜之作出自良工，
 *       钻石与下界合金已达名匠的门槛。</li>
 *   <li><b>锻造熟练度</b>：熟练的工匠能把同样的材料做出更高一档的成品
 *       （由 {@link EquipmentGrade#withProficiency(int, int)} 叠加）。</li>
 * </ol>
 *
 * <p>因此这张表记录的是<b>材料决定的基准品阶</b>；玩家实际做出什么档次，
 * 还要看他自己的锻造火候。原版材料配合熟练度，最高可达名匠。</p>
 *
 * <p>并非打造而来的特殊装备（鞘翅、三叉戟）与无法制作的战利品（锻造模板）单独定级。</p>
 */
public final class VanillaItemGrades {

    /** 木制工具与武器的基准品阶。 */
    private static final EquipmentGrade WOOD = EquipmentGrade.ROUGH;

    /** 石制工具的基准品阶。 */
    private static final EquipmentGrade STONE = EquipmentGrade.ROUGH;

    /** 铁制装备的基准品阶。 */
    private static final EquipmentGrade IRON = EquipmentGrade.ROUGH;

    /** 金制装备的基准品阶：材质珍贵但偏软，与铁同级。 */
    private static final EquipmentGrade GOLD = EquipmentGrade.ROUGH;

    /** 钻石装备的基准品阶。 */
    private static final EquipmentGrade DIAMOND = EquipmentGrade.FINE;

    /** 下界合金装备的基准品阶。 */
    private static final EquipmentGrade NETHERITE = EquipmentGrade.FINE;

    /** 物品 → 基准品阶。 */
    private static final Map<Item, EquipmentGrade> BY_ITEM = new HashMap<>();

    private VanillaItemGrades() {
    }

    /**
     * 由 {@link ModItems#register()} 调用，触发本类静态内容初始化。
     */
    static void register() {
    }

    /**
     * @param item 待查询的物品
     * @return 该装备由材料决定的基准品阶；未收录（或不是装备）时返回 {@code null}
     */
    public static EquipmentGrade baseGradeOf(Item item) {
        return BY_ITEM.get(item);
    }

    /**
     * 计算一件装备在特定锻造熟练度下的实际品阶。
     *
     * @param item             装备
     * @param proficiencyBonus 锻造熟练度带来的额外档位
     * @return 实际品阶；该物品不在表内时返回 {@code null}
     */
    public static EquipmentGrade gradeOf(Item item, int proficiencyBonus) {
        EquipmentGrade base = BY_ITEM.get(item);
        if (base == null) {
            return null;
        }
        return EquipmentGrade.withProficiency(base.level(), proficiencyBonus);
    }

    /**
     * @param item  物品
     * @param grade 基准品阶
     */
    private static void put(Item item, EquipmentGrade grade) {
        BY_ITEM.put(item, grade);
    }

    /**
     * 把一整套同材质的物品登记为同一品阶。
     *
     * @param grade 基准品阶
     * @param items 同材质的物品
     */
    private static void putSet(EquipmentGrade grade, Item... items) {
        for (Item item : items) {
            put(item, grade);
        }
    }

    static {
        // ==================== 木制：最基础的粗胚 ====================
        putSet(WOOD,
                Items.WOODEN_SWORD, Items.WOODEN_PICKAXE, Items.WOODEN_AXE,
                Items.WOODEN_SHOVEL, Items.WOODEN_HOE);

        // ==================== 石制：同为粗胚 ====================
        putSet(STONE,
                Items.STONE_SWORD, Items.STONE_PICKAXE, Items.STONE_AXE,
                Items.STONE_SHOVEL, Items.STONE_HOE);

        // ==================== 铁制：材料已可正经使用，仍是粗胚 ====================
        putSet(IRON,
                Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_AXE,
                Items.IRON_SHOVEL, Items.IRON_HOE,
                Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
                Items.SHIELD);

        // ==================== 金制：材质贵重但偏软 ====================
        putSet(GOLD,
                Items.GOLDEN_SWORD, Items.GOLDEN_PICKAXE, Items.GOLDEN_AXE,
                Items.GOLDEN_SHOVEL, Items.GOLDEN_HOE,
                Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS);

        // ==================== 皮革与锁链：护身之物 ====================
        putSet(EquipmentGrade.ROUGH,
                Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE,
                Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS,
                Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE,
                Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS);

        // ==================== 弓弩与箭矢 ====================
        put(Items.BOW, EquipmentGrade.ROUGH);
        put(Items.CROSSBOW, EquipmentGrade.ROUGH);
        putSet(EquipmentGrade.ROUGH,
                Items.ARROW, Items.SPECTRAL_ARROW, Items.TIPPED_ARROW);

        // ==================== 钻石：良工 ====================
        putSet(DIAMOND,
                Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE,
                Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE,
                Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE,
                Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);

        // ==================== 下界合金：良工 ====================
        putSet(NETHERITE,
                Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE,
                Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE,
                Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE,
                Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);

        // ==================== 海龟壳：良工 ====================
        put(Items.TURTLE_HELMET, EquipmentGrade.FINE);

        // ==================== 特殊装备 ====================
        // 鞘翅：只能从末地取得，非打造之物，已达名匠
        put(Items.ELYTRA, EquipmentGrade.MASTER);
        // 三叉戟：出自溺尸，非批量可制
        put(Items.TRIDENT, EquipmentGrade.MASTER);

        // ==================== 马铠：替坐骑披挂的护具 ====================
        put(Items.LEATHER_HORSE_ARMOR, EquipmentGrade.ROUGH);
        put(Items.IRON_HORSE_ARMOR, EquipmentGrade.ROUGH);
        put(Items.GOLDEN_HORSE_ARMOR, EquipmentGrade.FINE);
        put(Items.DIAMOND_HORSE_ARMOR, EquipmentGrade.FINE);
    }
}
